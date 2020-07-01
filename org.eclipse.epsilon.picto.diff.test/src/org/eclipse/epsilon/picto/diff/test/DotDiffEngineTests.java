package org.eclipse.epsilon.picto.diff.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.MessageFormat;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffEngine;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.MutableNode;

public class DotDiffEngineTests {

	static String filesLocationFormat = "files/DotDiffEngine/{0}";
	static String format = "graphviz-dot";

	static ViewTree baselineView;
	protected ViewTree changedView;
	protected ViewTree diffView;

	protected DotDiffEngine engine;

	@BeforeClass
	public static void setupBaseline() throws Exception {
		baselineView = ViewTreeUtil.getFromFile(
				MessageFormat.format(filesLocationFormat, "baseline.dot"), format);
	}

	@Before
	public void setupEngine() {
		engine = new DotDiffEngine();
	}

	protected void prepareTest(String file) throws Exception {
		changedView = ViewTreeUtil.getFromFile(
				MessageFormat.format(filesLocationFormat, file), format);
		diffView = new ViewTree();

		engine.diff(diffView, baselineView, changedView);
		diffView.getContent(); // perform the actual diffing if lazy execution
	}

	@Test
	public void addNode() throws Exception {
		prepareTest("baseline-addNode.dot");

		assertTrue(engine.getAddedNodes().size() == 1);
		assertTrue(engine.getAddedNodes().iterator().next().name().value().equals("FileSystem"));

		assertTrue(engine.getRemovedNodes().isEmpty());
		assertTrue(engine.getChangedNodes().isEmpty());
	}

	@Test
	public void removeNodes() throws Exception {
		prepareTest("baseline-removeNodes.dot");

		assertTrue(engine.getRemovedNodes().size() == 2);
		for (MutableNode node : engine.getRemovedNodes()) {
			String nodeName = node.name().value();
			assertTrue(nodeName.equals("Admin") || nodeName.contentEquals("Windows"));
		}

		assertTrue(engine.getAddedNodes().isEmpty());
		assertTrue(engine.getChangedNodes().isEmpty());
	}

	@Test
	public void modifyNode() throws Exception {
		prepareTest("baseline-modifyNode.dot");

		assertTrue(engine.getChangedNodes().size() == 1);
		assertTrue(engine.getChangedNodes().iterator().next().name().value().equals("OperatingSystem"));

		assertTrue(engine.getAddedNodes().isEmpty());
		assertTrue(engine.getRemovedNodes().isEmpty());
	}

	@Test
	public void addEdges() throws Exception {
		prepareTest("baseline-addEdges.dot");

		assertTrue(engine.getChangedNodes().isEmpty());
		assertTrue(engine.getAddedNodes().isEmpty());
		assertTrue(engine.getRemovedNodes().isEmpty());

		assertTrue(engine.getAddedLinks().entrySet().size() == 1);
		Entry<MutableNode, Set<Link>> entry =
				engine.getAddedLinks().entrySet().iterator().next();
		MutableNode node = entry.getKey();
		assertTrue(node.name().value().equals("OperatingSystem"));
		Set<Link> links = entry.getValue();
		assertTrue(links.size() == 2);
		for (Link link : links) {
			String linkName = (String) link.attrs().get("label");
			assertTrue(linkName.equals(" admins* ") ||
					linkName.equals(" virtualMachines* "));
		}
	}

	@Test
	public void removeEdges() throws Exception {
		prepareTest("baseline-removeEdges.dot");

		assertTrue(engine.getChangedNodes().isEmpty());
		assertTrue(engine.getAddedNodes().isEmpty());
		assertTrue(engine.getRemovedNodes().isEmpty());

		assertTrue(engine.getRemovedLinks().entrySet().size() == 2);
		engine.getRemovedLinks().entrySet().stream().forEach((entry) -> {
			MutableNode node = entry.getKey();
			if (node.name().value().equals("OperatingSystem")) {
				assertTrue(entry.getValue().size() == 1);
				Link link = entry.getValue().iterator().next();
				assertTrue(link.attrs().get("name").equals("h4"));
			}
			else if (node.name().value().equals("Antivirus")) {
				assertTrue(entry.getValue().size() == 1);
				Link link = entry.getValue().iterator().next();
				assertTrue(link.attrs().get("label").equals(" attacks "));
			}
			else {
				fail();
			}
		});
	}
}
