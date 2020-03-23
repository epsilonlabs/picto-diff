package org.eclipse.epsilon.picto.diff.engines.dot.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.parse.Parser;

public class GraphPromiseGenerator {

	private MutableGraph graph;
	private HashMap<String, SubGraphPromise> map = new HashMap<>();
	
	public GraphPromiseGenerator(MutableGraph graph) {
		this.graph = graph;
		init();
	}

	public static void main(String[] args) throws Exception {
		InputStream dot1 = new FileInputStream("files/simple_filesystem.dot");
		MutableGraph g1 = new Parser().read(dot1);
		GraphPromiseGenerator pg = new GraphPromiseGenerator(g1);
		for(MutableNode node: g1.rootNodes()) {
			System.out.println(new SubGraphPromise(g1, node).getContent());
			System.out.println();
		}
		System.out.println(pg.getSVGGraph());
	}
	
	public void init() {
		if (graph != null) {
			for(MutableNode n: graph.nodes()) {
				String node_name = n.name().toString();
				map.put(node_name, new SubGraphPromise(graph, n));
			}
		}
	}

	public String getSVGGraph() {
		return getGraph(Format.SVG);
	}

	public String getDotGraph() {
		return getGraph(Format.DOT);
	}

	protected String getGraph(Format format) {
		return Graphviz.fromGraph(graph).render(format).toString();
	}

	public ArrayList<SubGraphPromise> getNodePromises() {
		ArrayList<SubGraphPromise> arr = new ArrayList<>();
		for (MutableNode node : graph.nodes()) {
			arr.add(new SubGraphPromise(graph, node));
		}
		return arr;
	}
	
	public HashSet<String> getNodeNames() {
		return (HashSet<String>) map.keySet();
	}
	
	public HashMap<String, SubGraphPromise> getPromiseMap() {
		return map;
	}
	
	public String getPromiseForNode(String node) throws Exception {
		return map.get(node).getContent();
	}

	public static MutableNode findNode(String name, MutableGraph graph) {
		for (MutableNode n : graph.rootNodes()) {
			if (n.name().toString().equals(name)) {
				return n;
			}
		}
		return null;
	}

	public static MutableNode getCopy(MutableNode node) {
		MutableNode copy = node.copy();
		copy.links().clear();
		return copy;
	}

	public static void link(Link l, MutableNode s, MutableNode t) {
		Link link = s.linkTo(t);
		link.attrs().add(l.copy());
		s.addLink(link);
	}
}
