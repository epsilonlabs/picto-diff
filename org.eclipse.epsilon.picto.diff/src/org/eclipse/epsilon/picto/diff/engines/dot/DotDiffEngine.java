package org.eclipse.epsilon.picto.diff.engines.dot;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.dot.util.DotDiffIdUtil;
import org.eclipse.epsilon.picto.diff.engines.dot.util.DotDiffUtil;
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
	protected HashSet<MutableNode> addedNodes = new HashSet<MutableNode>();
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
				source_temp.graphAttrs().add("label", "Previous Version");
				source_temp.setCluster(true);
				source_temp.addTo(result);
				
				target_temp = mutGraph();
				target_temp.setDirected(true);
				target_temp.setName("right");
				target_temp.graphAttrs().add("label", "Current Version");
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
		detectAddedNodes();
		for (MutableNode n : addedNodes) {
			addNodeToTargetTemp(n, ADD_MODE.ADDED);
		}
		for (MutableNode n : getUnmutableSourceNodes()) {
			compareNode(n);
		}
		if (getUnmutableSourceNodes().size() != 0) {
			addFeedback("unmutable source nodes > 0, something is wrong");
		}
		// add new links of newly added nodes (e.g. links where the source is
		//   an added node). Done after the general comparison to be sure that
		//   any other change over the already existing nodes is treated first
		includeAddedNodesLinks();
	}
	
	private void includeAddedNodesLinks() {
		for (MutableNode addedNode : addedNodes) {
			MutableNode addedNode_targetTemp =
					findNodeInTargetTemp(DotDiffIdUtil.getPrefix() + addedNode.name().toString());
			for (Link addedLink : addedNode.links()) {
				MutableNode linkTarget =
						findLinkTarget(context.getTargetGraph(), addedLink);
				// add referenced elements to sourceTemp (if they are not new too)
				// TODO: is this necessary / wanted?
				if (findNode(linkTarget, addedNodes) == null) {
					MutableNode linkTarget_sourceTemp =
							findNodeInSourceTemp(linkTarget.name().value());
					if (linkTarget_sourceTemp == null) {
						addNodeToSourceTemp(linkTarget);
					}
				}
				// add referenced elements to target temp
				MutableNode linkTarget_targetTemp =
						findNodeInTargetTemp(DotDiffIdUtil.getPrefix() + linkTarget.name().value());
				if (linkTarget_targetTemp == null) {
					linkTarget_targetTemp = addNodeToTargetTemp(linkTarget, ADD_MODE.NORMAL);
				}
				Link link = linkCrossCluster(
						target_temp,
						addedNode_targetTemp.name().toString(),
						linkTarget_targetTemp.name().toString());

				copyLinkAttributes(link, addedLink);
				DotDiffUtil.paintGreen(link);

			}
		}
	}

	private void detectAddedNodes() {
		Set<MutableNode> sourceNodes = getUnmutableSourceNodes();
		for (MutableNode targetNode : getUnmutableTargetNodes()) {
			boolean found = false;
			for (MutableNode sourceNode : sourceNodes) {
				if (equalsByName(targetNode, sourceNode)) {
					found = true;
					break;
				}
			}
			if (!found) {
				addedNodes.add(targetNode);
			}
		}
	}

	public void compareNode(MutableNode left_node) {
		//get counter part node
		MutableNode right_node = findNode(left_node, context.getTargetGraph().nodes());
		//if node exists
		if (right_node != null) {

			//compare all attributes
			for(Entry<String, Object> attr: left_node.attrs()) {
				compareAttribute(left_node, right_node, attr);
			}
			
			//if there are changed attributes, change color of the right node
			if (getChangedAttrs(right_node).size() != 0) {
				//paint orange for changed attributes
				for(String s: getChangedAttrs(right_node)) {
					if (s.equals("label")) {
						DotDiffUtil.paintLabelOrange(right_node);
					}
					else {
						DotDiffUtil.paintOrange(right_node);
					}
				}
				// In this nidi3 library adding a node = adding
				// all its links and the targets of the links recursively
				// A copy of the node is made in the following methods to avoid that

				// add left node (to compare original with changes)
				addNodeToSourceTemp(left_node);
				
				// right node copy (modified version)
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
			for (Link changed_link : getChangedLinks(left_node)) {
				//find the link target
				MutableNode linkTarget = findLinkTarget(context.getSourceGraph(), changed_link);
				MutableNode linkTarget_sourceTemp =
						findNodeInSourceTemp(linkTarget.name().value());
				if (linkTarget_sourceTemp == null) {
					linkTarget_sourceTemp = addNodeToSourceTemp(linkTarget);
				}
				// the link source is current left node
				MutableNode linkSource_sourceTemp =
						findNodeInSourceTemp(left_node.name().value());
				if (linkSource_sourceTemp == null) {
					linkSource_sourceTemp = addNodeToSourceTemp(left_node);
				}
				
				Link right_link = linkCrossCluster(
						source_temp,
						linkSource_sourceTemp.name().toString(),
						linkTarget_sourceTemp.name().toString());
				copyLinkAttributes(right_link, changed_link);
			}
			
			
			//for all changed links for the right node
			for (Link changed_link : getChangedLinks(right_node)) {
				//find target and add it to the right temp graph
				MutableNode linkTarget = findLinkTarget(context.getTargetGraph(), changed_link);

				MutableNode linkTarget_targetTemp =
						findNodeInTargetTemp(DotDiffIdUtil.getPrefix() + linkTarget.name().value());
				if (linkTarget_targetTemp == null) {
					linkTarget_targetTemp = addNodeToTargetTemp(linkTarget, ADD_MODE.NORMAL);
				}
				//add the source to the right temp graph too (i.e. right_node)
				MutableNode linkSource_targetTemp =
						findNodeInTargetTemp(DotDiffIdUtil.getPrefix() + right_node.name().toString());
				if (linkSource_targetTemp == null) {
					linkSource_targetTemp = addNodeToTargetTemp(left_node, ADD_MODE.NORMAL);
				}
				
				Link right_link = linkCrossCluster(
						target_temp,
						linkSource_targetTemp.name().toString(),
						linkTarget_targetTemp.name().toString());

				copyLinkAttributes(right_link, changed_link);
				DotDiffUtil.paintOrange(right_link);
			}
			
			//for all removed links
			for(Link removed_link: getRemovedLinks(left_node)) {
				/*
				 * add affected nodes and link to the left graph
				 */
				MutableNode leftLinkSource_sourceTemp = findNodeInSourceTemp(left_node.name().value());
				if (leftLinkSource_sourceTemp == null) {
					leftLinkSource_sourceTemp = addNodeToSourceTemp(left_node);
				}

				MutableNode leftLinkTarget = findLinkTarget(context.getSourceGraph(), removed_link);
				MutableNode leftLinkTarget_sourceTemp = findNodeInSourceTemp(leftLinkTarget.name().value());
				if (leftLinkTarget_sourceTemp == null) {
					leftLinkTarget_sourceTemp = addNodeToSourceTemp(leftLinkTarget);
				}
				
				Link left_link = linkCrossCluster(
						source_temp,
						leftLinkSource_sourceTemp.name().value(),
						leftLinkTarget_sourceTemp.name().value());
				copyLinkAttributes(left_link, removed_link);

				/*
				 * deal with the right graph (a bit harder depending on other changes)
				 */
				
				MutableNode rightNode_targetTemp = findNodeInTargetTemp(
						DotDiffIdUtil.getPrefix() + right_node.name().value());
				if (rightNode_targetTemp == null) {
					rightNode_targetTemp = addNodeToTargetTemp(right_node, ADD_MODE.NORMAL);
				}
				
				MutableNode rightLinkTarget = findLinkTarget(context.getTargetGraph(), removed_link);
				// the target of the removed link could have also been removed
				if (rightLinkTarget != null) {
					MutableNode rightLinkTarget_targetTemp = findNodeInTargetTemp(
							DotDiffIdUtil.getPrefix() + rightLinkTarget.name().value());
					if (rightLinkTarget_targetTemp == null) {
						rightLinkTarget_targetTemp =
								addNodeToTargetTemp(rightLinkTarget, ADD_MODE.NORMAL); // and if it is changed because of other things?
					}

					// the source of this link is rightNode (in targetTemp graph)
					Link right_link = linkCrossCluster(
							target_temp,
							rightNode_targetTemp.name().value(),
							rightLinkTarget_targetTemp.name().value());
					copyLinkAttributes(right_link, removed_link);
					DotDiffUtil.paintRed(right_link);
				}
				else {
					rightLinkTarget = findLinkTarget(context.getSourceGraph(), removed_link);
					MutableNode rightLinkTarget_targetTemp =
							addNodeToTargetTemp(rightLinkTarget, ADD_MODE.NORMAL); //removed style?

					// the source of this link is rightNode (in targetTemp graph)
					Link right_link = linkCrossCluster(
							target_temp,
							rightNode_targetTemp.name().toString(),
							rightLinkTarget_targetTemp.name().toString());
					copyLinkAttributes(right_link, removed_link);
					DotDiffUtil.paintRed(right_link);
				}
			}
			
			/*
			 * below handles added links
			 */

			// get right node copy and remove changed and unchanged links
			MutableNode right_node_copy = right_node.copy();
			right_node_copy.links().removeAll(getChangedLinks(right_node));
			right_node_copy.links().removeAll(getUnchangedLinks(right_node));
			
			// each link remaining in the right node copy is an added one
			for(Link right_link: right_node_copy.links()) {
				MutableNode linkSource = findLinkSource(context.getTargetGraph(), right_link);
				MutableNode linkSource_targetTemp =
						findNodeInTargetTemp(DotDiffIdUtil.getPrefix() + linkSource.name().value());
				if (linkSource_targetTemp == null) {
					linkSource_targetTemp = addNodeToTargetTemp(linkSource, ADD_MODE.NORMAL);
				}
				MutableNode linkTarget = findLinkTarget(context.getTargetGraph(), right_link);
				MutableNode linkTarget_targetTemp =
						findNodeInTargetTemp(DotDiffIdUtil.getPrefix() + linkTarget.name().value());
				if (linkTarget_targetTemp == null) {
					linkTarget_targetTemp = addNodeToTargetTemp(linkTarget, ADD_MODE.NORMAL);
				}

				Link link = linkCrossCluster(target_temp, linkSource_targetTemp.name().value(), linkTarget_targetTemp.name().value());
				copyLinkAttributes(link, right_link);
				DotDiffUtil.paintGreen(link);

				// for the added link, add nodes that existed in previous version to the view
				if (findNode(linkSource, addedNodes) == null) {
					MutableNode linkSource_sourceTemp = findNodeInSourceTemp(linkSource.name().value());
					if (linkSource_sourceTemp == null) {
						addNodeToSourceTemp(linkSource);
					}
				}
				if (findNode(linkTarget, addedNodes) == null) {
					MutableNode linkTarget_sourceTemp = findNodeInSourceTemp(linkTarget.name().value());
					if (linkTarget_sourceTemp == null) {
						addNodeToSourceTemp(linkTarget);
					}
				}
			}

			//remove left node and right node from their graphs (to reduce memory footprint)
			// (fonso) this only removes them if they are root nodes.
			//         is this removal dangerous for some strange cases?
			getSourceNodes().remove(left_node);
			getTargetNodes().remove(right_node);
		}
		else {
			// if node is deleted
			addRemovedNode(left_node); // (fonso) right now, not necessary
			addNodeToSourceTemp(left_node);
			
			addNodeToTargetTemp(left_node, ADD_MODE.REMOVED);
			getSourceNodes().remove(left_node); // (fonso) related with removes above: is this dangerous?
		}
	}
	
	// TODO: should we be careful here about not copying the same edge name
	//   multiple times into the same graph?
	private void copyLinkAttributes(Link leftLink, Link rightLink) {
		for(Entry<String, Object> attr : rightLink.attrs()) {
			leftLink.attrs().add(attr.getKey(), attr.getValue());
		}
	}

	public void compareAttribute(MutableNode source, MutableNode target, Entry<String, Object> attribute) {
		Entry<String, Object> correspond = findAttribute(target, attribute.getKey());
		
		// if attr does not exist - attr is removed
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
		DotDiffIdUtil.prefixNode(copy);
		MutableGraph g = mutGraph();
		if (mode == ADD_MODE.ADDED) {
			DotDiffUtil.paintGreen(g);
		}
		else if (mode == ADD_MODE.CHANGED) {
			DotDiffUtil.paintOrange(g);
		}
		else if (mode == ADD_MODE.REMOVED) {
			DotDiffUtil.paintRed(g);
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

	private boolean equalsByName(MutableNode left, MutableNode right) {
		return left.name().value().equals(right.name().value());
	}

	public MutableNode findNode(MutableNode node, Collection<MutableNode> nodeCollection) {
		for(MutableNode n: nodeCollection) {
			if (equalsByName(node, n)) {
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

	public MutableNode findLinkSource(MutableGraph graph, Link link) {
		String name = "";
		// obtain name for the target
		if (link.from() instanceof PortNode) {
			PortNode portNode = (PortNode) link.from();
			name = portNode.name().toString();
		}
		else if (link.from() instanceof MutableNode) {
			MutableNode mutableNode = (MutableNode) link.from();
			name = mutableNode.name().toString();
		}
		// get the node in the graph by name
		for (MutableNode node : graph.nodes()) {
			if (node.name().toString().equals(name)) {
				return node;
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
		for (MutableNode node : graph.nodes()) {
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
					if (n.name().toString().equals(to)) {
						target = g;
					}
				}
			}
			source.addLink(target);
			Link link = source.links().get(source.links().size() - 1);
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
