/*
 * Copyright 2013 Twitter, Inc. and other contributors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.mesos;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.model.Descriptor.FormException;
import hudson.slaves.*;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

import com.codahale.metrics.Timer;
import org.apache.commons.lang.StringUtils;

public class MesosJenkinsAgent extends AbstractCloudSlave {

  private static final long serialVersionUID = 1L;

  private final String cloudName;
  private transient MesosCloud cloud;
  private final MesosAgentSpecs spec;
  private final int idleTerminationMinutes;
  private final double cpus;
  private final int mem;
  private final double diskNeeded;
  private transient final Timer.Context provisioningContext;

  private boolean pendingDelete;

  private static final Logger LOGGER = Logger.getLogger(MesosJenkinsAgent.class.getName());

  public MesosJenkinsAgent(MesosCloud cloud, String name, int numExecutors, MesosAgentSpecs spec,
      Timer.Context provisioningContext, List<? extends NodeProperty<?>> nodeProperties)
      throws IOException, FormException {
    super(name, spec.getLabelString(),
        StringUtils.isBlank(spec.getRemoteFSRoot()) ? "jenkins" : spec.getRemoteFSRoot().trim(), "" + numExecutors,
        spec.getMode(), spec.getLabelString(), // Label.
        new MesosComputerLauncher(cloud, name), new MesosRetentionStrategy(spec.getIdleTerminationMinutes()),
        nodeProperties);
    this.cloud = cloud;
    this.cloudName = cloud.getDisplayName();
    this.spec = spec;
    this.idleTerminationMinutes = spec.getIdleTerminationMinutes();
    this.cpus = spec.getSlaveCpus() + (numExecutors * spec.getExecutorCpus());
    this.mem = spec.getSlaveMem() + (numExecutors * spec.getExecutorMem());
    this.diskNeeded = spec.getdiskNeeded();
    this.provisioningContext = provisioningContext;
    LOGGER.fine("Constructing Mesos slave " + name + " from cloud " + cloud.getDescription());
  }

  public MesosCloud getCloud() {
    if (cloud == null) {
      cloud = (MesosCloud) getJenkins().getCloud(cloudName);
    }
    return cloud;
  }

  private Jenkins getJenkins() {
    Jenkins jenkins = Jenkins.get();
    if (jenkins == null) {
      throw new IllegalStateException("Jenkins is null");
    }
    return jenkins;
  }

  public double getCpus() {
    return cpus;
  }

  public int getMem() {
    return mem;
  }

  public double getDiskNeeded() {
    return diskNeeded;
  }

  public MesosAgentSpecs getSlaveInfo() {
    return spec;
  }

  public int getIdleTerminationMinutes() {
    return idleTerminationMinutes;
  }

  public Timer.Context getProvisioningContext() {
    return provisioningContext;
  }

  /**
   * Releases and removes this agent.
   */
  public void terminate() {
    final Computer computer = toComputer();
    if (computer != null) {
      computer.recordTermination();
    }
    try {
      // TODO: send the output to somewhere real
      _terminate(new StreamTaskListener(System.out, Charset.defaultCharset()));
    } finally {
      try {
        Jenkins.get().removeNode(this);
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to remove " + name, e);
      }
    }
  }

  @Override
  public void _terminate(TaskListener listener) {
    try {
      LOGGER.info("Terminating slave " + getNodeName());
      // Remove the node from jenkins.
      Jenkins.get().removeNode(this);
      Mesos mesos = Mesos.getInstance(cloud);
      mesos.stopJenkinsSlave(name);
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Failed to terminate Mesos instance: " + getInstanceId(), e);
    }
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  @Extension
  public static class DescriptorImpl extends SlaveDescriptor {
    @Override
    public String getDisplayName() {
      return "Mesos Slave";
    }

    @Override
    public boolean isInstantiable() {
      return false;
    }
  }

  private String getInstanceId() {
    return getNodeName();
  }

  public boolean isPendingDelete() {
    return pendingDelete;
  }

  public void setPendingDelete(boolean pendingDelete) {
    this.pendingDelete = pendingDelete;
  }

  @Override
  public AbstractCloudComputer<MesosJenkinsAgent> createComputer() {
    return new MesosComputer(this);
  }

  @Override
  public FilePath getRootPath() {
    FilePath rootPath = createPath(remoteFS);
    if (rootPath != null) {
      try {
        // Construct absolute path for slave's remote file system root.
        rootPath = rootPath.absolutize();
      } catch (IOException e) {
        LOGGER.warning("IO exception while absolutizing slave root path: " + e);
      } catch (InterruptedException e) {
        LOGGER.warning("InterruptedException while absolutizing slave root path: " + e);
      }
    }
    // Return root path even if we caught an exception,
    // let the caller handle the error.
    return rootPath;
  }

  @Override
  public FilePath getWorkspaceFor(TopLevelItem item) {

    if (!getCloud().isNfsRemoteFSRoot()) {
      return super.getWorkspaceFor(item);
    }

    FilePath r = getWorkspaceRoot();
    if (r == null)
      return null; // offline
    FilePath child = r.child(item.getFullName());
    if (child != null && item instanceof AbstractProject) {
      AbstractProject project = (AbstractProject) item;
      for (int i = 1;; i++) {
        FilePath candidate = i == 1 ? child : child.withSuffix(COMBINATOR + i);
        boolean candidateInUse = false;
        for (Object run : project.getBuilds()) {
          if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            if (build.isBuilding() && build.getWorkspace() != null
                && build.getWorkspace().getBaseName().equals(candidate.getName())) {
              candidateInUse = true;
              break;
            }
          }
        }
        if (!candidateInUse) {
          // Save the workspace folder name so that user can view the workspace through MesosWorkspaceBrowser even after slave goes offline
          MesosRecentWSTracker.getMesosRecentWSTracker().updateRecentWorkspaceMap(item.getName(), candidate.getName());
          return candidate;
        }

      }
    }
    return child;
  }

  // Let us use the same property that is used by Jenkins core to get combinator for workspace
  private static final String COMBINATOR = System.getProperty(WorkspaceList.class.getName(), "@");
}
