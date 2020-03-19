package org.eclipse.epsilon.picto.diff.engines.dot.util;

import static guru.nidi.graphviz.model.Factory.mutGraph;

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
import guru.nidi.graphviz.model.PortNode;
import guru.nidi.graphviz.parse.Parser;

public class GraphPromiseGenerator {

	private MutableGraph graph;
	private HashMap<String, String> map = new HashMap<String, String>();
	
	public GraphPromiseGenerator(MutableGraph graph) {
		this.graph = graph;
		init();
	}

	public static void main(String[] args) throws Exception {
		InputStream dot1 = new FileInputStream("files/simple_filesystem.dot");
		MutableGraph g1 = new Parser().read(dot1);
		GraphPromiseGenerator pg = new GraphPromiseGenerator(g1);
		for(MutableNode node: g1.rootNodes()) {
			System.out.println(pg.getPromise(node));
		}
		System.out.println(pg.getGraphPromise());
		
	}
	
	public void init() {
		if (graph != null) {
			for(MutableNode n: graph.nodes()) {
				String node_name = n.name().toString();
				map.put(node_name, getPromise(n));
			}
		}
	}

	public String getGraphPromise() {
		return  Graphviz.fromGraph(graph).render(Format.SVG).toString();
	}
	
	public ArrayList<String> getNodePromises() {
		ArrayList<String> arr = new ArrayList<String>();
		for(MutableNode node: graph.nodes()) {
			arr.add(getPromise(node));
		}
		return arr;
	}
	
	public HashSet<String> getNodeNames() {
		return (HashSet<String>) map.keySet();
	}
	
	public HashMap<String, String> getPromiseMap() {
		return map;
	}
	
	public String getPromiseForNode(String node) {
		return map.get(node);
	}
	
	public String getPromise(MutableNode node) {
		String result = "";
		MutableGraph g = mutGraph();
		g.setName(node.name().toString());
		//g.graphAttrs().add("label", node.attrs().get("label"));
		MutableNode node_copy = getCopy(node);
		g.rootNodes().add(node_copy);

		for (MutableNode n : graph.nodes()) {
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
					MutableNode source_copy = getCopy(n);
					g.rootNodes().add(source_copy);
					link(link, source_copy, node_copy);
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
			MutableNode target = findNode(target_name);
			MutableNode target_copy = getCopy(target);
			g.rootNodes().add(target_copy);
			link(link, node_copy, target_copy);
		}

		result = Graphviz.fromGraph(g).render(Format.DOT).toString();
		return result;
	}

	public MutableNode findNode(String name) {
		for (MutableNode n : graph.rootNodes()) {
			if (n.name().toString().equals(name)) {
				return n;
			}
		}
		return null;
	}

	public MutableNode getCopy(MutableNode node) {
		MutableNode copy = node.copy();
		copy.links().clear();
		return copy;
	}

	public void link(Link l, MutableNode s, MutableNode t) {
		Link link = s.linkTo(t);
		link.attrs().add(l.copy());
		s.addLink(link);
	}
}
