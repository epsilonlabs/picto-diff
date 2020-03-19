package org.eclipse.epsilon.picto.diff.engines.dot.util;

import static guru.nidi.graphviz.model.Factory.mutNode;

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

public class PictoDiffUtil {

	public static void removeNode(MutableGraph graph, MutableNode node) {
		graph.rootNodes().remove(node);
	}
	
	public static void removeLink(MutableGraph graph, Link link) {
		graph.links().remove(link);
	}
	
	public static void removeLink(MutableNode node, Link link) {
		node.links().remove(link);
	}
	
	public static void removeLink(MutableNode node, String name) {
		Link link_to_remove = null;
		for(Link l: node.links()) {
			if (l.attrs().get("name").toString().equals(name)) {
				link_to_remove = l;
				break;
			}
		}
		node.links().remove(link_to_remove);
	}
	
	public static Link getLink(MutableNode node, String name) {
		for(Link l: node.links()) {
			if (l.attrs().get("name").toString().equals(name)) {
				return l;
			}
		}
		return null;
	}
	
	public static Link getLink(MutableGraph graph, String name) {
		for(Link l: graph.links()) {
			if (l.attrs().get("name").toString().equals(name)) {
				return l;
			}
		}
		return null;
	}
	
	public static MutableNode getNode(MutableGraph graph, String name) {
		for(MutableNode n: graph.nodes()) {
			if (n.name().toString().equals(name)) {
				return n;
			}
		}
		return null;
	}
	
	public static MutableNode getNodeRec(MutableGraph graph, String name) {
		for(MutableNode n: graph.nodes()) {
			if (n.name().toString().equals(name)) {
				return n;
			}
		}
		for(MutableGraph g: graph.graphs()) {
			MutableNode n = getNodeRec(g, name);
			if (n != null) {
				return n;
			}
		}
		return null;
	}
	
	public static MutableGraph getSubGraph(MutableGraph graph, String name) {
		for(MutableGraph g: graph.graphs()) {
			if (g.name().toString().equals(name)) {
				return g;
			}
		}
		return null;
	}
	
	public static void linkNodes(MutableNode from, MutableNode to) {
		from.addLink(to);
	}
	
	public static void linkGraphs(MutableGraph from, MutableGraph to) {
		from.addLink(to);
	}
	
	public static void linkCrossCluster(MutableGraph graph, String from, String to) {
		//the mechanism is funny, may need to report an issue.
		MutableNode node = mutNode(from);
		node.addLink(to);
		Link link = node.links().get(0);
		link.attrs().add("constraint", false);
		link.attrs().add("style", "dashed");
		link.attrs().add("dir", "forward");
		link.attrs().add("color", "orange");
		graph.rootNodes().add(node);
	}
	
	public static void linkCrossClusterNorm(MutableGraph graph, String from, String to) {
		//the mechanism is funny, may need to report an issue.
		MutableNode node = mutNode(from);
		node.addLink(to);
		Link link = node.links().get(0);
		link.attrs().add("style", "dashed");
		link.attrs().add("dir", "forward");
		graph.rootNodes().add(node);
	}
	
	public static void paintRed(MutableGraph graph) {
		graph.graphAttrs().add("color", "red");
		graph.graphAttrs().add("style", "dashed");
		graph.graphAttrs().add("fontcolor", "red");
	}
	
	public static void paintOrange(MutableGraph graph) {
		graph.graphAttrs().add("color", "orange");
	}
	
	public static void paintGreen(MutableGraph graph) {
		graph.graphAttrs().add("color", "green");
	}
	
	public static void paintRed(MutableNode node) {
		node.attrs().add("color", "red");
		node.attrs().add("style", "dashed");
		node.attrs().add("fontcolor", "red");
	}
	
	public static void paintGreen(MutableNode node) {
		node.attrs().add("color", "green");
	}
	
	public static void paintOrange(MutableNode node) {
		node.attrs().add("color", "orange");
	}
	
	public static void paintRed(Link link) {
		link.attrs().add("color", "red");
		link.attrs().add("style", "dashed");
		link.attrs().add("fontcolor", "red");
	}
	
	public static void paintGreen(Link link) {
		link.attrs().add("color", "green");
	}
	
	public static void paintOrange(Link link) {
		link.attrs().add("color", "orange");
		link.attrs().add("fontcolor", "orange");
	}
	
	public static void paintLabelRed(MutableNode node) {
		node.attrs().add("fontcolor", "red");
	}
	
	public static void paintLabelGreen(MutableNode node) {
		node.attrs().add("fontcolor", "green");
	}
	
	public static void paintLabelOrange(MutableNode node) {
		node.attrs().add("fontcolor", "orange");
	}
	
	public static void main(String[] args) throws IOException {
		InputStream dot1 = new FileInputStream("files/foo_.dot");
	    MutableGraph g = new Parser().read(dot1);
	    
	    MutableNode node = PictoDiffUtil.getNodeRec(g, "n2");
	    MutableNode node3 = PictoDiffUtil.getNodeRec(g, "n3");
	    MutableNode node4 = PictoDiffUtil.getNodeRec(g, "n4");
	    PictoDiffUtil.paintRed(node.links().get(0));
	    PictoDiffUtil.paintRed(node);
	    PictoDiffUtil.paintLabelRed(node);
	    PictoDiffUtil.paintOrange(node3);
	    PictoDiffUtil.paintGreen(node4);
	    
	    PictoDiffUtil.linkCrossCluster(g, "n1",	"_n1");
	    PictoDiffUtil.linkCrossCluster(g, "n2",	"_n2");
	    //n.addLink(n2);
//	    Link l = n.linkTo(n2);
//	    System.out.println(l);
//	    g.links().add(l);
//	    System.out.println(g.links());
//	    GraphUtil.linkNodes(n, n2);
	    
	    Graphviz.fromGraph(g).width(700).render(Format.PNG).toFile(new File("example/foo__.png"));
	    Graphviz.fromGraph(g).render(Format.DOT).toFile(new File("files/foo__.dot"));

	}
}
