package org.eclipse.epsilon.picto.diff.engines.dot.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.parse.Parser;

public class PictoDiffValidator {

	private Set<String> graphLabels = new HashSet<String>();
	private Set<String> nodeLabels = new HashSet<String>();
	private Set<String> linkLabels = new HashSet<String>();

	public PictoDiffValidator() {
	}
	
	public boolean validate(MutableGraph graph) {
		
		if (graph.name() == null) {
			return false;
		}
		else {
			if (!graphLabels.add(graph.name().toString())) {
				return false;
			}
			for(MutableNode node: graph.nodes()) {
				if (node.name() == null) {
					return false;
				}
				else {
					if (!nodeLabels.add(node.name().toString())) {
						return false;
					}
				}
				for(Link l: node.links()) {
					if (l.attrs().get("name") == null) {
						return false;
					}
					else {
						String link_label = (String) l.attrs().get("name");
						if (!linkLabels.add(link_label)) {
							return false;
						}
					}
				}
			}
		}
		for(MutableGraph g: graph.graphs()) {
			if (!validate(g)) {
				return false;
			}
		}
		return true;
	}
	
	public String insights() {
		String s = "Nodes: " + nodeLabels.toString() + "\n";
		s += "links: " + linkLabels.toString()+ "\n";
		s += "graphs: " + graphLabels.toString()+ "\n";
		return s;
	}
	
	public static void main(String[] args) throws IOException {
		InputStream dot1 = new FileInputStream("files/foo_.dot");
	    MutableGraph g1 = new Parser().read(dot1);
	    
	    PictoDiffValidator validator = new PictoDiffValidator();
	    System.out.println(validator.validate(g1));
//	    System.out.println(validator.insights());

	}
}
