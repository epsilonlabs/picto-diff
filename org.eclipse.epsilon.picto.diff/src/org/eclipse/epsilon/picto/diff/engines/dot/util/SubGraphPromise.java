package org.eclipse.epsilon.picto.diff.engines.dot.util;

import static guru.nidi.graphviz.model.Factory.mutGraph;

import org.eclipse.epsilon.picto.ContentPromise;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.model.PortNode;

/**
 * Lazy generation of subgraph views for each node
 */
public class SubGraphPromise implements ContentPromise {

	private MutableGraph parentGraph;
	private MutableNode node;
	private String content;

	public SubGraphPromise(MutableGraph parentGraph, MutableNode node) {
		this.parentGraph = parentGraph;
		this.node = node;
		content = null;
	}

	@Override
	public String getContent() throws Exception {
		if (content == null) {
			content = calculateSubGraph();
		}
		return content;
	}

	private String calculateSubGraph() {
		MutableGraph subGraph = mutGraph();
		subGraph.setName(node.name().toString());
		MutableNode node_copy = GraphPromiseGenerator.getCopy(node);
		subGraph.rootNodes().add(node_copy);

		for (MutableNode n : parentGraph.nodes()) {
			for (Link link : n.links()) {
				boolean node_is_target = false;
				if (link.to() instanceof PortNode) {
					PortNode temp = (PortNode) link.to();
					if (temp.name().toString().equals(node.name().toString())) {
						node_is_target = true;
					}
				} else if (link.to() instanceof MutableNode) {
					MutableNode temp = (MutableNode) link.to();
					if (temp.name().toString().equals(node.name().toString())) {
						node_is_target = true;
					}
				}
				if (node_is_target) {
					MutableNode source_copy = GraphPromiseGenerator.getCopy(n);
					subGraph.rootNodes().add(source_copy);
					GraphPromiseGenerator.link(link, source_copy, node_copy);
				}
			}
		}

		for (Link link : node.links()) {
			String target_name = "";
			if (link.to() instanceof PortNode) {
				PortNode temp = (PortNode) link.to();
				target_name = temp.name().toString();
			} else if (link.to() instanceof MutableNode) {
				MutableNode temp = (MutableNode) link.to();
				target_name = temp.name().toString();
			}
			MutableNode target = GraphPromiseGenerator.findNode(target_name, parentGraph);
			MutableNode target_copy = GraphPromiseGenerator.getCopy(target);
			subGraph.rootNodes().add(target_copy);
			GraphPromiseGenerator.link(link, node_copy, target_copy);
		}

		return Graphviz.fromGraph(subGraph).render(Format.DOT).toString();
	}
}
