package org.eclipse.epsilon.picto.diff.engines.dot;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.dot.util.PictoDiffIDUtil;
import org.eclipse.epsilon.picto.diff.engines.dot.util.PictoDiffUtil;
import org.eclipse.epsilon.picto.diff.engines.dot.util.PictoDiffValidator;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.model.PortNode;

public class DotDiffEngine implements DiffEngine {
	
	private ArrayList<String> feedbacks = new ArrayList<String>();
	public enum DISPLAY_MODE {ALL, CHANGED};
	private enum ADD_MODE {ADDED, CHANGED, REMOVED, NORMAL};
	protected DotDiffContext context = null;
	protected boolean linkClusters = true;
	
	protected MutableGraph source_temp;
	protected MutableGraph target_temp;
	protected MutableGraph result;

	protected PictoDiffValidator graphValidator = new PictoDiffValidator();
	
	protected HashSet<MutableNode> changedNodes = new HashSet<MutableNode>();
	protected HashSet<MutableNode> unchangedNodes = new HashSet<MutableNode>();
	protected HashSet<MutableNode> added3dNodes = new HashSet<MutableNode>();
	protected HashSet<MutableNode> removedNodes = new HashSet<MutableNode>();
	
	protected HashMap<MutableNode, HashSet<Link>> unchangedLinks = new HashMap<MutableNode, HashSet<Link>>();
	protected HashMap<MutableNode, HashSet<Link>> addedLinks = new HashMap<MutableNode, HashSet<Link>>();
	protected HashMap<MutableNode, HashSet<Link>> removedLinks = new HashMap<MutableNode, HashSet<Link>>();
	protected HashMap<MutableNode, HashSet<Link>> changedLinks = new HashMap<MutableNode, HashSet<Link>>();
	
	protected HashMap<MutableNode, HashSet<String>> addedAttrs = new HashMap<MutableNode, HashSet<String>>();
	protected HashMap<MutableNode, HashSet<String>> removedAttrs = new HashMap<MutableNode, HashSet<String>>();
	protected HashMap<MutableNode, HashSet<String>> changedAttrs = new HashMap<MutableNode, HashSet<String>>();
	protected HashMap<MutableNode, HashSet<String>> unchangedAttrs = new HashMap<MutableNode, HashSet<String>>();
	
	public static void main(String[] args) throws IOException {

		DotDiffContext context = new DotDiffContext(
				new FileInputStream("files/simple_filesystem.dot"),
				new FileInputStream("files/simple_filesystem2.dot"));
		DotDiffEngine comparisonEngine = new DotDiffEngine(context);
	    comparisonEngine.load();
		context.setSerialiseOptions("example/result_1.svg", "example/result_1.dot");
	    comparisonEngine.compare();
	    comparisonEngine.serialise();
	}

	public DotDiffEngine() {
	}

	public DotDiffEngine(DotDiffContext context) {
		this.context = context;
	}

	public boolean load() {
		try {
			if (context.loadGraphs()) {
				result = mutGraph();
				result.setName("pdiff");
				source_temp = mutGraph();
				source_temp.setDirected(true);
				source_temp.setName("left");
				source_temp.graphAttrs().add("label", "digraph");
				source_temp.setCluster(true);
				source_temp.addTo(result);
				
				target_temp = mutGraph();
				target_temp.setDirected(true);
				target_temp.setName("right");
				target_temp.graphAttrs().add("label", "digraph++");
				target_temp.setCluster(true);
				target_temp.addTo(result);
			}
		} catch (IOException e) {
			addFeedback(e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public void compare() {
		for(MutableNode n: getUnmutableSourceNodes()) {
			compareNode(n);
		}
		if (getUnmutableSourceNodes().size() != 0) {
			addFeedback("unmutable source nodes > 0, something is wrong");
		}
	}
	
	public void compareNode(MutableNode left_node) {
		//get counter part node
		MutableNode right_node = findNode(left_node.name().value());
		//if node exists
		if (right_node != null) {
			//TODO: record each change in attr
			
			//compare all attributes
			for(Entry<String, Object> attr: left_node.attrs()) {
				compareAttribute(left_node, right_node, attr);
			}
			
			//if there are changed attributes, change color of the right node
			if (getChangedAttrs(right_node).size() != 0) {
				//paint orange for changed attributes
				for(String s: getChangedAttrs(right_node)) {
					if (s.equals("label")) {
						PictoDiffUtil.paintLabelOrange(right_node);
					}
					else {
						PictoDiffUtil.paintOrange(right_node);
					}
				}
				/*
				 * The following is done because in this nidi3 library adding a node = adding all its links and the targets of the links recursively
				 * So a copy of the node is made 
				 */
				addNodeToSourceTemp(left_node);
				
				//right node copy
				addNodeToTargetTemp(right_node, ADD_MODE.CHANGED);
			}
			
			
			//compare all links of the left node
			for(Link left_link: left_node.links()) {
				//find counter part
				Link right_link = findLink(right_node, left_link.attrs().get("name").toString());
				//if link exists
				if (right_link != null) {
					//if link has changed
					if (!compareLink(left_node, right_node, left_link, right_link)) {
						addChangedLink(left_node, left_link); 
						addChangedLink(right_node, right_link);
					}
					else {
						//add unchanged link
						addUnchangedLink(right_node, right_link);
					}
				}
				else {
					//add to removed links
					addRemovedLink(left_node, left_link);
				}
			}
			
			//for all changed links for the left node
			for(Link changed_link: getChangedLinks(left_node))
			{
				//find the link target
				MutableNode link_target = findLinkTarget(context.getSourceGraph(), changed_link);
				
				MutableNode temp = addNodeToSourceTemp(link_target);
				//get link source
				MutableNode link_source = findNodeInSourceTemp(left_node.name().toString());
				//if cannot find link source, make a copy of the left node, add it to left
				if (link_source == null) {
					link_source = addNodeToSourceTemp(left_node);
				}
				
				Link right_link = linkCrossCluster(target_temp, link_source.name().toString(), temp.name().toString());
				right_link.attrs().add(changed_link.copy());

				//establish link
//				Link link = link_source.linkTo(temp);
//				link.attrs().add(changed_link.copy());
//				link_source.addLink(link);
			}
			
			
			//for all changed links for the right node
			for(Link changed_link: getChangedLinks(right_node))
			{
				//find target and addit to the right temp graph
				MutableNode link_target = findLinkTarget(context.getTargetGraph(), changed_link);
				MutableNode temp = addNodeToTargetTemp(link_target, ADD_MODE.NORMAL);

				//add the source to the right temp graph too
				MutableNode link_source = findNodeInTargetTemp(PictoDiffIDUtil.getPrefix() + right_node.name().toString());
				if (link_source == null) {
					link_source = addNodeToTargetTemp(left_node, ADD_MODE.NORMAL);
				}
				
				Link right_link = linkCrossCluster(target_temp, link_source.name().toString(), temp.name().toString());
				right_link.attrs().add(changed_link.copy());
				PictoDiffUtil.paintOrange(right_link);
				
//				Link link = link_source.linkTo(temp);
//				link.attrs().add(changed_link.copy());
//				GraphUtil.paintOrange(link);
//				link_source.addLink(link);
			}
			
			//for all removed links
			for(Link removed_link: getRemovedLinks(left_node)) {
				/*
				 * deal with the left node
				 */
				MutableNode left_node_copy = left_node.copy();
				addNodeToSourceTemp(left_node);

				
				MutableNode left_link_target = findLinkTarget(context.getSourceGraph(), removed_link);
				MutableNode left_temp = addNodeToSourceTemp(left_link_target);

				MutableNode left_link_source = findNodeInSourceTemp(left_node_copy.name().toString());
				
				Link left_link = linkCrossCluster(source_temp, left_link_source.name().toString(), left_link_target.name().toString());
				left_link.attrs().add(removed_link.copy());
				//////////////////////////////////////////
				/*
				 * deal with the right node
				 */
				
				MutableNode right_node_copy = addNodeToTargetTemp(right_node, ADD_MODE.NORMAL);
				
				MutableNode right_link_target = findLinkTarget(context.getTargetGraph(), removed_link);
				if (right_link_target != null) {
					MutableNode right_temp = addNodeToTargetTemp(right_link_target, ADD_MODE.NORMAL);
					MutableNode r_link_source = findNodeInTargetTemp(right_node_copy.name().toString());
					
					Link right_link = linkCrossCluster(target_temp, r_link_source.name().toString(), right_temp.name().toString());
					right_link.attrs().add(removed_link.copy());
					PictoDiffUtil.paintRed(right_link);


				}
				else {
					right_link_target = findLinkTarget(context.getSourceGraph(), removed_link);
					MutableNode right_temp = addNodeToTargetTemp(right_link_target, ADD_MODE.NORMAL);

					MutableNode r_link_source = findNodeInTargetTemp(right_node_copy.name().toString());
					
					Link right_link = linkCrossCluster(target_temp, r_link_source.name().toString(), right_temp.name().toString());
					right_link.attrs().add(removed_link.copy());
					PictoDiffUtil.paintRed(right_link);
				}
			}
			
			/*
			 * below handles added links
			 */
			//get right node copy
			MutableNode right_node_copy = right_node.copy();
			//remove changed and unchanged links
			right_node_copy.links().removeAll(getChangedLinks(right_node));
			right_node_copy.links().removeAll(getUnchangedLinks(right_node));
			
			//for each link in the right node copy - they are added ones
			for(Link right_link: right_node_copy.links()) {
				MutableNode link_target = findLinkTarget(context.getTargetGraph(), right_link);
				MutableNode temp = addNodeToTargetTemp(link_target, ADD_MODE.NORMAL);
				MutableNode link_source = findNodeInTargetTemp(PictoDiffIDUtil.getPrefix() + right_node.name().toString());
				if (link_source == null) {
					link_source = addNodeToTargetTemp(right_node, ADD_MODE.NORMAL);
				}
				
				Link link = linkCrossCluster(target_temp, link_source.name().toString(), temp.name().toString());
				right_link.attrs().add(link.copy());
				PictoDiffUtil.paintRed(link);
			}		
			
			//remove left node and right node from their graphs (to reduce memory footprint)
			getSourceNodes().remove(left_node);
			getTargetNodes().remove(right_node);
		}
		else {
			// if node is deleted
			addRemovedNode(left_node);
			addNodeToSourceTemp(left_node);
			
			addNodeToTargetTemp(left_node, ADD_MODE.REMOVED);
			getSourceNodes().remove(left_node);

		}
	}
	
	public void compareAttribute(MutableNode source, MutableNode target, Entry<String, Object> attribute) {
		Entry<String, Object> correspond = findAttribute(target, attribute.getKey());
		
		//if attr does not exhist - attr is removed
		if (correspond == null) {
			addRemovedAttr(source, attribute.getKey());
		}
		else {
			//if attrs are same - add to unchanged?
			if (attribute.getValue().toString().equals(correspond.getValue().toString())) {
				addUnchangedAttr(target, correspond.getKey());
				addUnchangedAttr(source, attribute.getKey());
			}
			//add to changed attr
			else {
				addChangedAttr(target, correspond.getKey());
				addChangedAttr(source, attribute.getKey());
			}
		}
	}
	
	public MutableNode getNodeCopy(MutableNode node) {
		MutableNode copy = node.copy();
		copy.links().clear();
		return copy;
	}
	
	public MutableNode addNodeToSourceTemp(MutableNode node) {
		MutableNode copy = getNodeCopy(node);
		MutableGraph g = mutGraph();
		g.graphAttrs().add("label", "");
		g.setCluster(true);
		g.rootNodes().add(copy);
		g.setName(copy.name().toString());
		source_temp.graphs().add(g);
		return copy;
	}
	
	public MutableNode addNodeToTargetTemp(MutableNode node, ADD_MODE mode) {
		MutableNode copy = getNodeCopy(node);
		PictoDiffIDUtil.prefixNode(copy);
		MutableGraph g = mutGraph();
		if (mode == ADD_MODE.ADDED) {
			PictoDiffUtil.paintGreen(g);
		}
		else if (mode == ADD_MODE.CHANGED) {
			PictoDiffUtil.paintOrange(g);
		}
		else if (mode == ADD_MODE.REMOVED) {
			PictoDiffUtil.paintRed(g);
		}
		else {
			
		}
		g.graphAttrs().add("label", "");
		g.setCluster(true);
		g.rootNodes().add(copy);
		g.setName(copy.name().toString());
		target_temp.graphs().add(g);
		return copy;
	}
	
	private HashSet<MutableNode> getUnmutableSourceNodes() {
		return (HashSet<MutableNode>) context.getSourceGraph().nodes();
	}
	
	private HashSet<MutableNode> getUnmutableTargetNodes() {
		return (HashSet<MutableNode>) context.getTargetGraph().nodes();
	}

	
	private HashSet<MutableNode> getSourceNodes() {
		return (HashSet<MutableNode>) context.getSourceGraph().rootNodes();
	}
	
	private HashSet<MutableNode> getTargetNodes() {
		return (HashSet<MutableNode>) context.getTargetGraph().rootNodes();
	}
	
	public MutableNode findNodeInSourceTemp(String name) {
		for(MutableGraph g: source_temp.graphs())
		{
			for(MutableNode n: g.rootNodes()) {
				if (n.name().toString().equals(name)) {
					return n;
				}
			}
		}
		return null;
	}
	
	public MutableNode findNodeInTargetTemp(String name) {
		for(MutableGraph g: target_temp.graphs())
		{
			for(MutableNode n: g.rootNodes()) {
				if (n.name().toString().equals(name)) {
					return n;
				}
			}
		}
		return null;
	}
	
	public boolean compareLink(MutableNode ln, MutableNode rn, Link s, Link t) {
		boolean result = true;
		if (s.attrs().get("label") != null) {
			if (!s.attrs().get("label").toString().equals(t.attrs().get("label"))) {
				result = false;
			}
		}
		
		if (s.to() instanceof PortNode) {
			PortNode s_temp = (PortNode) s.to();
			if (t.to() instanceof PortNode) {
				PortNode t_temp = (PortNode) t.to();
				if (!s_temp.name().toString().equals(t_temp.name().toString())) {
					result = false;
				}
			}
			else {
				result = false;
			}
		}
		else if (s.to() instanceof MutableNode) {
			MutableNode s_temp = (MutableNode) s.to();
			if (t.to() instanceof MutableNode) {
				MutableNode t_temp = (MutableNode) t.to();
				if (!s_temp.name().toString().equals(t_temp.name().toString())) {
					result = false;
				}
			}
			else {
				result = false;
			}
		}
		return result;
	}
	

	
	public MutableNode findNode(String name) {
		for(MutableNode n: context.getTargetGraph().nodes()) {
			if (n.name().value().equals(name)) {
				return n;
			}
		}
		return null;
	}
	
	public Link findLink(MutableNode n, String name) {
		//this is assuming that links have 'name'
		for(Link l: n.links()) {
			if (l.attrs().get("name").toString().equals(name)) {
				return l;
			}
		}
		return null;
	}
	
	public MutableNode findLinkTarget(MutableGraph graph, Link link) {
		String name = "";
		//obtain name for the target
		if (link.to() instanceof PortNode) {
			PortNode portNode = (PortNode) link.to();
			name = portNode.name().toString();
		}
		else if (link.to() instanceof MutableNode) {
			MutableNode mutableNode = (MutableNode) link.to();
			name = mutableNode.name().toString();
		}
		
		//get the node in the graph by name
		for(MutableNode node: graph.nodes()) {
			if (node.name().toString().equals(name)) {
				return node;
			}
		}
		return null;
	}
	
	public Entry<String, Object> findAttribute(MutableNode node, String key) {
		for(Entry<String, Object> attr: node.attrs()) {
			if (attr.getKey().equals(key)) {
				return attr;
			}
		}
		return null;
	}
	
	private void addRemovedNode(MutableNode node) {
		removedNodes.add(node);
	}

	
	private void addUnchangedLink(MutableNode node, Link link) {
		HashSet<Link> links = unchangedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
			links.add(link);
			unchangedLinks.put(node, links);
		}
		else {
			links.add(link);	
		}
	}
	
	private void addChangedLink(MutableNode node, Link link) {
		HashSet<Link> links = changedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
			links.add(link);
			changedLinks.put(node, links);
		}
		else {
			links.add(link);	
		}
	}
	
	private void addAddedLink(MutableNode node, Link link) {
		HashSet<Link> links = addedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
			links.add(link);
			addedLinks.put(node, links);
		}
		else {
			links.add(link);
		}
	}
	
	private void addRemovedLink(MutableNode node, Link link) {
		HashSet<Link> links = removedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
			links.add(link);
			removedLinks.put(node, links);
		}
		else {
			links.add(link);
		}
	}
	
	public HashSet<Link> getUnchangedLinks(MutableNode node) {
		HashSet<Link> links = unchangedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	public HashSet<Link> getChangedLinks(MutableNode node) {
		HashSet<Link> links = changedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	public HashSet<Link> getAddedLinks(MutableNode node) {
		HashSet<Link> links = addedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	public HashSet<Link> getRemovedLinks(MutableNode node) {
		HashSet<Link> links = removedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	private void addChangedAttr(MutableNode node, String attr) {
		HashSet<String> attrs = changedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
			attrs.add(attr);
			changedAttrs.put(node, attrs);
		}
		else {
			attrs.add(attr);
		}
	}
	
	private void addUnchangedAttr(MutableNode node, String attr) {
		HashSet<String> attrs = unchangedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
			attrs.add(attr);
			unchangedAttrs.put(node, attrs);
		}
		else {
			attrs.add(attr);
		}
	}
	
	private void addRemovedAttr(MutableNode node, String attr) {
		HashSet<String> attrs = removedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
			attrs.add(attr);
			removedAttrs.put(node, attrs);
		}
		else {
			attrs.add(attr);
		}
	}
	
	private void addAddedAttr(MutableNode node, String attr) {
		HashSet<String> attrs = addedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
			attrs.add(attr);
			addedAttrs.put(node, attrs);
		}
		else {
			attrs.add(attr);
		}
	}
	
	public HashSet<String> getChangedAttrs(MutableNode node) {
		HashSet<String> attrs = changedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
		}
		return attrs;
	}
	
	public HashSet<String> getAddedAttrs(MutableNode node) {
		HashSet<String> attrs = addedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
		}
		return attrs;
	}
	
	public HashSet<String> getRemovedAttrs(MutableNode node) {
		HashSet<String> attrs = removedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
		}
		return attrs;
	}
	
	public void serialise() throws IOException {
	    Graphviz.fromGraph(result).render(Format.DOT).toFile(new File(context.getSerialise_dot()));
	    Graphviz.fromGraph(result).width(700).render(Format.SVG).toFile(new File(context.getSerialise_image()));
	}
	
	public String getSVGString() {
		 return Graphviz.fromGraph(result).render(Format.SVG).toString();
	}
	
	public void saveSVGFile(File file) throws IOException {
	    Graphviz.fromGraph(result).render(Format.SVG).toFile(file);
	}
	
	private Link linkCrossCluster(MutableGraph graph, String from, String to) {
		if(linkClusters) {
			//the mechanism is funny, may need to report an issue.
			MutableGraph source = null;
			MutableGraph target = null;
			for(MutableGraph g: graph.graphs()) {
				if (source != null & target != null) {	
					break;
				}
				for(MutableNode n: g.nodes()) {
					if (n.name().toString().equals(from)) {
						source = g;
					}
					else if (n.name().toString().equals(to)) {
						target = g;
					}
				}
			}
			source.addLink(target);
			Link link = source.links().get(0);
			return link;
		}
		else {
			//the mechanism is funny, may need to report an issue.
			MutableNode node = mutNode(from);
			node.addLink(to);
			Link link = node.links().get(0);
			graph.rootNodes().add(node);
			return link;
		}
	}
	
	public void addFeedback(String feedback) {
		feedbacks.add(feedback);
	}
	
	public ArrayList<String> getFeedbacks() {
		return feedbacks;
	}
	
	public String getStringFeedback() {
		String ret = "";
		for(String s: feedbacks) {
			ret = ret + s + "\n";
		}
		return ret;
	}

	@Override
	public boolean supports(String format) {
		return format.equals("graphviz-dot");
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {
		this.context = new DotDiffContext(left.getPromise().getContent(), right.getPromise().getContent());
		load();
		compare();
		String resultDot = Graphviz.fromGraph(result).render(Format.SVG).toString();
		String svgEvents = null;
		String svgEventsFile = "transformations/svgEvents.html";
		if (PictoDiffPlugin.getDefault() == null) {
			// Standalone java
			svgEvents = new String(Files.readAllBytes(Paths.get(svgEventsFile)));
		}
		else {
			// Eclipse plugin (works, but there is probably an easier way to do this?)
			svgEvents = new String(Files.readAllBytes(Paths.get(
					FileLocator.resolve(PictoDiffPlugin.getDefault().getBundle().getEntry(svgEventsFile)).getPath())));
		}
		diffView.setPromise(new StringContentPromise(resultDot + svgEvents));
		diffView.setFormat("html");
		diffView.setIcon("diagram-ff0000");
	}
}
