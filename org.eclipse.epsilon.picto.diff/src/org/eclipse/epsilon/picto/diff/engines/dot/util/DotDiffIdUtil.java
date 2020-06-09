package org.eclipse.epsilon.picto.diff.engines.dot.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.parse.Parser;

public class DotDiffIdUtil {

	private static String PREFIX = "_";
	
	public static void prefixGraph(MutableGraph graph) {
		if (graph.name() != null) {
			String name = graph.name();
			graph.setName(prefix(name));
		}
		for(MutableNode node: graph.nodes()) {
			if (node.name() != null) {
				node.setName(prefix(node.name().toString()));
			}
			for(Link l: node.links()) {
				if (l.attrs().get("name") != null) {
					l.attrs().add("name", prefix((String) l.attrs().get("name")));
				}
			}
		}
		for(MutableGraph g: graph.graphs()) {
			prefixGraph(g);
		}
	}
	
	public static void prefixNode(MutableNode node) {
		String name = node.name().toString();
		node.setName(prefix(name));
		for(Link link: node.links()) {
			String link_name = (String) link.attrs().get("name");
			link.attrs().add("name", prefix(link_name));
		}
	}
	
	public static String getPrefix() {
		return PREFIX;
	}
	
	public static String prefix(String s) {
		return PREFIX+s;
	}

	public static String getPrefixedName(MutableNode node) {
		return prefix(node.name().value());
	}

	public static String getUnprefixedName(MutableNode node) {
		if (!node.name().value().startsWith(PREFIX)) {
			throw new RuntimeException(
					String.format("Node with name %s is not prefixed", node.name().value()));
		}
		return node.name().value().replaceFirst(PREFIX, "");
	}

	public static void main(String[] args) throws IOException {
		InputStream dot1 = new FileInputStream("files/foo.dot");
	    MutableGraph g = new Parser().read(dot1);
	    
	    DotDiffIdUtil.prefixGraph(g);
	    
	    Graphviz.fromGraph(g).width(700).render(Format.PNG).toFile(new File("example/foo_prefixed.png"));
		Graphviz.fromGraph(g).render(Format.DOT).toFile(new File("example/foo_prefixed.dot"));
	}
}
