package org.jenkinsci.plugins.mesos;

import antlr.ANTLRException;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import jenkins.model.Jenkins;
import org.apache.mesos.Protos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Ignore;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Jenkins.class })
public class MesosAgentSpecsTest {
        @Before
        public void setUp() {
                MockitoAnnotations.initMocks(this);

                // Simulate basic Jenkins env
                Jenkins jenkins = Mockito.mock(Jenkins.class);

                when(jenkins.getLabelAtom(any(String.class))).thenAnswer(new Answer<LabelAtom>() {
                        @Override
                        public LabelAtom answer(InvocationOnMock invocation) throws Throwable {
                                return new LabelAtom((String) invocation.getArguments()[0]);
                        }
                });

                when(jenkins.getLabel(any(String.class))).thenAnswer(new Answer<LabelAtom>() {
                        @Override
                        public LabelAtom answer(InvocationOnMock invocation) throws Throwable {
                                return new LabelAtom((String) invocation.getArguments()[0]);
                        }
                });

                PowerMockito.mockStatic(Jenkins.class);
                Mockito.when(Jenkins.get()).thenReturn(jenkins);
        }

        private MesosAgentSpecs buildMesosAgentSpecs(String label, boolean customizable)
                        throws IOException, Descriptor.FormException {
                MesosAgentSpecs.ContainerInfo container = new MesosAgentSpecs.ContainerInfo("", "", Boolean.FALSE,
                                Boolean.FALSE, customizable, false, "", new LinkedList<MesosAgentSpecs.Volume>(),
                                new LinkedList<MesosAgentSpecs.Parameter>(),
                                Protos.ContainerInfo.DockerInfo.Network.BRIDGE.name(), null,
                                new LinkedList<MesosAgentSpecs.NetworkInfo>());
                return new MesosAgentSpecs(label, Node.Mode.EXCLUSIVE, "1", "1", "1", "1", "1", "500", "1", "", "1", "",
                                "", "", "false", "false", container, new LinkedList<MesosAgentSpecs.URI>());
        }

        private static Label getLabel(String name) {
                Iterator<LabelAtom> i = Label.parse(name).iterator();

                return i.hasNext() ? i.next() : null;
        }

        @Ignore
        @Test
        public void matchesLabelTest() throws IOException, Descriptor.FormException, ANTLRException {
                assertFalse(buildMesosAgentSpecs("worker", false).matchesLabel(null));
                assertFalse(buildMesosAgentSpecs("worker", false).matchesLabel(getLabel(null)));

                assertFalse(buildMesosAgentSpecs("worker-2", false).matchesLabel(getLabel("worker")));
                assertTrue(buildMesosAgentSpecs("worker", false).matchesLabel(getLabel("worker")));

                assertFalse(buildMesosAgentSpecs("worker-2", true).matchesLabel(getLabel("worker")));
                assertTrue(buildMesosAgentSpecs("worker", true).matchesLabel(getLabel("worker")));

                assertFalse(buildMesosAgentSpecs("worker", false)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image:3.2.r3-version")));
                assertTrue(buildMesosAgentSpecs("worker", true)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image:3.2.r3-version")));
                assertTrue(buildMesosAgentSpecs("worker:example-domain.com/name-of-1-image:3.2.r3-version", false)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image:3.2.r3-version")));
                assertTrue(buildMesosAgentSpecs("worker:example-domain.com/name-of-1-image:3.2.r3-version", true)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image:3.2.r3-version")));

                assertFalse(buildMesosAgentSpecs("worker", false)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image")));
                assertTrue(buildMesosAgentSpecs("worker", true)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image")));
                assertTrue(buildMesosAgentSpecs("worker:example-domain.com/name-of-1-image", true)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image")));
                assertTrue(buildMesosAgentSpecs("worker:example-domain.com/name-of-1-image", false)
                                .matchesLabel(getLabel("worker:example-domain.com/name-of-1-image")));

                assertTrue(buildMesosAgentSpecs(null, false).matchesLabel(null));
                assertFalse(buildMesosAgentSpecs("label", false).matchesLabel(null));
                assertFalse(buildMesosAgentSpecs(null, false).matchesLabel(getLabel("label")));

                assertTrue(buildMesosAgentSpecs(null, true).matchesLabel(null));
                assertFalse(buildMesosAgentSpecs("label", true).matchesLabel(null));
                assertFalse(buildMesosAgentSpecs(null, true).matchesLabel(getLabel("label")));
        }

        @Ignore
        @Test
        public void getMesosAgentSpecsForLabelTest() throws IOException, Descriptor.FormException, ANTLRException {
                assertEquals("worker", buildMesosAgentSpecs("worker", false).getSpecsForLabel(getLabel("worker"))
                                .getLabelString());
                assertEquals("worker", buildMesosAgentSpecs("worker", true).getSpecsForLabel(getLabel("worker"))
                                .getLabelString());

                assertNull(buildMesosAgentSpecs("worker", false).getSpecsForLabel(getLabel("worker2")));
                assertNull(buildMesosAgentSpecs("worker", true).getSpecsForLabel(getLabel("worker1")));

                assertNull(buildMesosAgentSpecs("worker", false).getSpecsForLabel(
                                getLabel("worker:example-domain.com/name-of-1-image:3.2.r3-version")));
                assertEquals("worker:example-domain.com/name-of-1-image:3.2.r3-version",
                                buildMesosAgentSpecs("worker", true).getSpecsForLabel(
                                                getLabel("worker:example-domain.com/name-of-1-image:3.2.r3-version"))
                                                .getLabelString());

                assertEquals("worker:a.com/b:1", buildMesosAgentSpecs("worker:a.com/b:1", true)
                                .getSpecsForLabel(getLabel("worker:a.com/b:1")).getLabelString());
                assertEquals("worker:a.com/b:1", buildMesosAgentSpecs("worker:a.com/b:1", false)
                                .getSpecsForLabel(getLabel("worker:a.com/b:1")).getLabelString());

                assertNull(buildMesosAgentSpecs(null, false).getSpecsForLabel(null).getLabelString());
                assertNull(buildMesosAgentSpecs("label", false).getSpecsForLabel(null));
                assertNull(buildMesosAgentSpecs(null, false).getSpecsForLabel(getLabel("label")));

                assertNull(buildMesosAgentSpecs(null, true).getSpecsForLabel(null).getLabelString());
                assertNull(buildMesosAgentSpecs("label", true).getSpecsForLabel(null));
                assertNull(buildMesosAgentSpecs(null, true).getSpecsForLabel(getLabel("label")));
        }

        @Test
        public void getNetworkNameTest() throws IOException, Descriptor.FormException, ANTLRException {
                MesosAgentSpecs info = buildMesosAgentSpecs("label", false);
                MesosAgentSpecs.NetworkInfo networkInfo = new MesosAgentSpecs.NetworkInfo("exampleNetwork");
                info.getContainerInfo().getNetworkInfos().add(networkInfo);
                assertEquals(info.getContainerInfo().getNetworkInfos().get(0).getNetworkName(), "exampleNetwork");
        }
}
