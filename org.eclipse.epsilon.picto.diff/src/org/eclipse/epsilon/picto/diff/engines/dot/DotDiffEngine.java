package org.eclipse.epsilon.picto.diff.engines.dot;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.dot.util.DotDiffIdUtil;
import org.eclipse.epsilon.picto.diff.engines.dot.util.DotDiffUtil;
import org.eclipse.epsilon.picto.diff.engines.dot.util.PictoDiffValidator;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Link;
import guru.nidi.graphviz.model.LinkSource;
import guru.nidi.graphviz.model.LinkTarget;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import guru.nidi.graphviz.model.Port;
import guru.nidi.graphviz.model.PortNode;

public class DotDiffEngine implements DiffEngine {
	
	public static final String SVG_EVENTS_FILE = "transformations/svgEvents.html";
	public static String svgEvents;

	private ArrayList<String> feedbacks = new ArrayList<String>();
	public enum DISPLAY_MODE {ALL, CHANGED};
	private enum ADD_MODE {ADDED, CHANGED, REMOVED, NORMAL};
	protected DotDiffContext context = null;
	// if true, cluster graphs are linked instead of directly linking nodes
	// if linking clusters, ports would not work properly
	// TODO: determine if this is useful for some situation, and delete if not
	protected boolean linkClusters = false;
	
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
	
	public static void main(String[] args) throws Exception {

		String filesLocationFormat = "files/dotDiffEngine/%s";
		String outputFolder = "diffResult/dotDiffEngine";
		String outputLocationFormat = outputFolder + "/%s-diffResult.dot";

		File directory = new File(outputFolder);
		if (!directory.exists()) {
			directory.mkdirs();
		}

		String baselineDot = new String(
				Files.readAllBytes(Paths.get(String.format(filesLocationFormat, "baseline.dot"))));
		ViewTree baselineView = new ViewTree();
		baselineView.setPromise(new StaticContentPromise(baselineDot));
		baselineView.setFormat("graphviz-dot");

		List<String> modifiedFiles = Arrays.asList(
				"baseline-addProperty.dot",
				"baseline-addNode.dot",
				"baseline-addEdges.dot",
				"baseline-addEdgeFromNewNode.dot",
				"baseline-addEdgeToNewNode.dot",
				"baseline-removeNodes.dot",
				"baseline-removeEdge.dot",
				"baseline-modifyEdges.dot",
				"baseline-modifyEdgesToNewNodes.dot",
				"baseline-addEdgesToChangedNodes.dot");

		//		modifiedFiles = Arrays.asList("baseline-addEdgesToChangedNodes.dot");

		for (String file : modifiedFiles) {

			String modifiedFile = String.format(filesLocationFormat, file);
			String modifiedDot = new String(Files.readAllBytes(Paths.get(modifiedFile)));

			ViewTree modifiedView = new ViewTree();
			modifiedView.setFormat("graphviz-dot");
			modifiedView.setPromise(new StaticContentPromise(modifiedDot));

			ViewTree diffView = new ViewTree();
			DotDiffContext context = new DotDiffContext(baselineDot, modifiedDot);
			DotDiffEngine engine = new DotDiffEngine(context);
			try {
				engine.diff(diffView, baselineView, modifiedView);
				Files.write(Paths.get(String.format(outputLocationFormat, file)),
						diffView.getContent().getText().getBytes(),
						StandardOpenOption.CREATE,
						StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING);
			}
			catch (Exception ex) {
				System.out.println("-----------------------------------------");
				System.out.println(String.format("Modified file %s fails", file));
				ex.printStackTrace();
			}
		}
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
				result.graphAttrs().add(context.getSourceGraph().graphAttrs());
				
				source_temp = mutGraph();
				source_temp.setDirected(true);
				source_temp.setName("left");
				source_temp.graphAttrs().add(context.getSourceGraph().graphAttrs());
				source_temp.graphAttrs().add("label", "Previous Version");
				source_temp.setCluster(true);
				source_temp.addTo(result);
				
				target_temp = mutGraph();
				target_temp.setDirected(true);
				target_temp.setName("right");
				target_temp.graphAttrs().add(context.getTargetGraph().graphAttrs());
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
		processAddedNodes();
		// compare attributes first to avoid introducing items
		//   as unmodified by mistake when processing node links
		// also detects removed nodes
		for (MutableNode n : getUnmutableSourceNodes()) {
			compareNodeAttributes(n);
		}
		processRemovedNodes();
		for (MutableNode n : getUnmutableSourceNodes()) {
			compareNodeLinks(n);
		}
		if (getUnmutableSourceNodes().size() != 0) {
			addFeedback("unmutable source nodes > 0, something is wrong");
		}
		// add new links of newly added nodes (e.g. links where the source is
		//   an added node). Done after the general comparison to be sure that
		//   any other change over the already existing nodes is treated first
		processAddedNodesLinks();
	}

	private void processAddedNodes() {
		detectAddedNodes();
		for (MutableNode n : addedNodes) {
			addNodeToTargetTemp(n, ADD_MODE.ADDED);
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

	private void processAddedNodesLinks() {
		for (MutableNode addedNode : addedNodes) {
			// invariant: all added nodes are already in targetTemp graph, so just find
			MutableNode addedNode_targetTemp = findNodeInTargetTemp(addedNode);
			for (Link addedLink : addedNode.links()) {
				MutableNode linkTarget =
						findLinkTarget(context.getTargetGraph(), addedLink);
				// add referenced elements to sourceTemp (if they are not new too)
				// TODO: is this necessary / wanted?
				if (findNode(linkTarget, addedNodes) == null) {
					addIfNotFoundInSourceTemp(linkTarget);
				}

				// add referenced elements to target temp
				MutableNode linkTarget_targetTemp =
						addIfNotFoundInTargetTemp(linkTarget, ADD_MODE.NORMAL);

				Link link = null;
				link = link(target_temp,
						addedNode_targetTemp, linkTarget_targetTemp,
						addedLink.from(), addedLink.to());
				copyLinkAttributes(link, addedLink);
				DotDiffUtil.paintAdded(link);
			}
		}
	}

	private void processRemovedNodes() {
		for (MutableNode node : removedNodes) {
			addIfNotFoundInSourceTemp(node);
			addIfNotFoundInTargetTemp(node, ADD_MODE.REMOVED);
			getSourceNodes().remove(node); // (fonso) : is this dangerous?
		}
	}

	private void compareNodeAttributes(MutableNode left_node) {
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
						DotDiffUtil.paintLabelChanged(right_node);
					}
					else {
						DotDiffUtil.paintChanged(right_node);
					}
				}
				// In this nidi3 library adding a node = adding
				// all its links and the targets of the links recursively
				// A copy of the node is made in the following methods to avoid that

				// add left node (to compare original with changes)
				addIfNotFoundInSourceTemp(left_node);
				
				// right node copy (modified version)
				addIfNotFoundInTargetTemp(right_node, ADD_MODE.CHANGED);
			}
		}
		else {
			// node is deleted
			addRemovedNode(left_node); // (fonso) right now, not necessary
		}

	}

	public void compareNodeLinks(MutableNode left_node) {
		//get counter part node
		MutableNode right_node = findNode(left_node, context.getTargetGraph().nodes());

		// if left_node does not exist in right graph, none of its links are processed
		if (right_node == null) {
			return;
		}

		//compare all links of the left node
		for (Link left_link : left_node.links()) {
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

		//for all changed links for the left node (sourceTemp, original graph)
		for (Link changed_link : getChangedLinks(left_node)) {
			//find the link target
			MutableNode linkTarget = findLinkTarget(context.getSourceGraph(), changed_link);
			MutableNode linkTarget_sourceTemp = addIfNotFoundInSourceTemp(linkTarget);

			// the link source is current left node
			MutableNode linkSource_sourceTemp = addIfNotFoundInSourceTemp(left_node);
			
			Link right_link = link(source_temp,
					linkSource_sourceTemp, linkTarget_sourceTemp,
					changed_link.from(), changed_link.to());
			copyLinkAttributes(right_link, changed_link);
		}

		//for all changed links for the right node (targetTemp, graph with changes)
		for (Link changed_link : getChangedLinks(right_node)) {
			//find target and add it to the right temp graph
			MutableNode linkTarget = findLinkTarget(context.getTargetGraph(), changed_link);
			MutableNode linkTarget_targetTemp = addIfNotFoundInTargetTemp(linkTarget, ADD_MODE.NORMAL);

			//add the source to the right temp graph too (i.e. right_node)
			MutableNode linkSource_targetTemp = addIfNotFoundInTargetTemp(right_node, ADD_MODE.NORMAL);

			Link right_link = link(target_temp,
					linkSource_targetTemp, linkTarget_targetTemp,
					changed_link.from(), changed_link.to());
			copyLinkAttributes(right_link, changed_link);
			DotDiffUtil.paintChanged(right_link);
		}

		//for all removed links
		for (Link removed_link : getRemovedLinks(left_node)) {
			/*
			 * add affected nodes and link to the left graph
			 */
			MutableNode leftLinkSource_sourceTemp = addIfNotFoundInSourceTemp(left_node);

			MutableNode leftLinkTarget = findLinkTarget(context.getSourceGraph(), removed_link);
			MutableNode leftLinkTarget_sourceTemp = addIfNotFoundInSourceTemp(leftLinkTarget);

			Link left_link = link(source_temp,
					leftLinkSource_sourceTemp, leftLinkTarget_sourceTemp,
					removed_link.from(), removed_link.to());
			copyLinkAttributes(left_link, removed_link);

			/*
			 * deal with the right graph (a bit harder depending on other changes)
			 */
			MutableNode rightNode_targetTemp = addIfNotFoundInTargetTemp(right_node, ADD_MODE.NORMAL);

			MutableNode rightLinkTarget = findLinkTarget(context.getTargetGraph(), removed_link);
			// the target of the removed link could have also been removed
			if (rightLinkTarget != null) {
				MutableNode rightLinkTarget_targetTemp =
						addIfNotFoundInTargetTemp(rightLinkTarget, ADD_MODE.NORMAL);

				// the source of this link is rightNode (in targetTemp graph)
				Link right_link = link(target_temp,
						rightNode_targetTemp, rightLinkTarget_targetTemp,
						removed_link.from(), removed_link.to());
				copyLinkAttributes(right_link, removed_link);
				DotDiffUtil.paintDeleted(right_link);
			}
			else {
				rightLinkTarget = findLinkTarget(context.getSourceGraph(), removed_link);
				MutableNode rightLinkTarget_targetTemp =
						addIfNotFoundInTargetTemp(rightLinkTarget, ADD_MODE.NORMAL);

				// the source of this link is rightNode (in targetTemp graph)
				Link right_link = link(target_temp,
						rightNode_targetTemp, rightLinkTarget_targetTemp,
						removed_link.from(), removed_link.to());
				copyLinkAttributes(right_link, removed_link);
				DotDiffUtil.paintDeleted(right_link);
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
		for (Link right_link : right_node_copy.links()) {

			// the source is the right node
			MutableNode linkSource = right_node;
			MutableNode linkSource_targetTemp =
					addIfNotFoundInTargetTemp(linkSource, ADD_MODE.NORMAL);

			MutableNode linkTarget = findLinkTarget(context.getTargetGraph(), right_link);
			MutableNode linkTarget_targetTemp =
					addIfNotFoundInTargetTemp(linkTarget, ADD_MODE.NORMAL);

			Link link = linkCrossCluster(
					target_temp, linkSource_targetTemp, linkTarget_targetTemp);
			copyLinkAttributes(link, right_link);
			DotDiffUtil.paintAdded(link);

			// for the added link, add nodes that existed in previous version to the view (sourceTemp)
			if (findNode(linkSource, addedNodes) == null) {
				addIfNotFoundInSourceTemp(linkSource);
			}
			if (findNode(linkTarget, addedNodes) == null) {
				addIfNotFoundInSourceTemp(linkTarget);
			}
		}

		//remove left node and right node from their graphs (to reduce memory footprint)
		// (fonso) this only removes them if they are root nodes.
		//         is this removal dangerous for some strange cases?
		getSourceNodes().remove(left_node);
		getTargetNodes().remove(right_node);
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
			DotDiffUtil.paintAdded(g);
		}
		else if (mode == ADD_MODE.CHANGED) {
			DotDiffUtil.paintChanged(g);
		}
		else if (mode == ADD_MODE.REMOVED) {
			DotDiffUtil.paintDeleted(g);
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
	
	/**
	 * Add if not found in the sourceTemp graph
	 */
	public MutableNode addIfNotFoundInSourceTemp(MutableNode node) {
		MutableNode node_sourceTemp = findNodeInSourceTemp(node);
		if (node_sourceTemp == null) {
			node_sourceTemp = addNodeToSourceTemp(node);
		}
		return node_sourceTemp;
	}

	/**
	 * Add if not found in the targetTemp graph with the provided addition mode
	 */
	public MutableNode addIfNotFoundInTargetTemp(MutableNode node, ADD_MODE addMode) {
		MutableNode node_targetTemp = findNodeInTargetTemp(node);
		if (node_targetTemp == null) {
			node_targetTemp = addNodeToTargetTemp(node, addMode);
		}
		return node_targetTemp;
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
	
	private MutableNode findNodeByName(String nodeName, MutableGraph graph) {
		for (MutableGraph g : graph.graphs()) {
			for (MutableNode otherNode : g.rootNodes()) {
				if (otherNode.name().value().equals(nodeName)) {
					return otherNode;
				}
			}
		}
		return null;
	}

	public MutableNode findNodeInSourceTemp(MutableNode node) {
		return findNodeByName(node.name().value(), source_temp);
	}
	
	public MutableNode findNodeInTargetTemp(MutableNode node) {
		String prefixedName = DotDiffIdUtil.getPrefixedName(node);
		return findNodeByName(prefixedName, target_temp);
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

	private MutableNode findNode(MutableNode node, Collection<MutableNode> nodeCollection) {
		for (MutableNode n : nodeCollection) {
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

	// (fonso): maintained in case that fine-grain treatment of attrs is improved
	@SuppressWarnings("unused")
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
	
	public String getSVGString() {
		 return Graphviz.fromGraph(result).render(Format.SVG).toString();
	}
	
	public String getDotString() {
		return Graphviz.fromGraph(result).render(Format.DOT).toString();
	}
	
	private Link linkCrossCluster(MutableGraph graph, MutableNode fromNode, MutableNode toNode) {
		if(linkClusters) {
			//the mechanism is funny, may need to report an issue.
			MutableGraph source = null;
			MutableGraph target = null;
			for(MutableGraph g: graph.graphs()) {
				if (source != null & target != null) {	
					break;
				}
				for (MutableNode node : g.nodes()) {
					if (equalsByName(node, fromNode)) {
						source = g;
					}
					if (equalsByName(node, toNode)) {
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
			// fonso: I think the funny part here refers to the clusters of each
			//   node being automatically merged when using addLink directly
			//   over fromNode, hence the need of creating an intermediate node.
			//   I might need to ask Will about this.
			MutableNode node = mutNode(fromNode.name().value());
			node.addLink(toNode.name().value());
			Link link = node.links().get(0);
			graph.rootNodes().add(node);
			return link;
		}
	}
	
	/**
	 * This linking method takes extra precautions to correctly link nodes
	 * through ports (if present)
	 * 
	 * @param graph      The graph containing the nodes
	 * @param sourceNode Source node
	 * @param targetNode Target node
	 * @param linkSource Link source (might be a port)
	 * @param linkTarget Link target (might be a port)
	 * @return
	 */
	private Link link(MutableGraph graph,
			MutableNode sourceNode, MutableNode targetNode,
			LinkSource linkSource, LinkTarget linkTarget) {

		//the mechanism is funny, may need to report an issue. (Will's)
		// fonso: I think the funny part here refers to the clusters of each
		//   node being strangely merged when using addLink directly
		//   over sourceNode, hence the need of creating an aux node.
		//   Also, we need to add this node to the graph, or the link won't show.
		//   I might need to ask Will about this.

		// Note: linkSource and linkTarget are obtained from the original link,
		// i.e. they are elements from the original graphs (the ones living in
		// DotDiffContext). These elements should be used to get the port details
		// but never as source of target of the new link 

		// Strangely, a node does not have a list of ports, the port information
		// is only present in the linksource and linktarget elems (or, in the 
		// node's label, but that would require parsing it).

		MutableNode auxNode = mutNode(sourceNode.name().value());
		graph.rootNodes().add(auxNode);
		if (linkSource instanceof PortNode) {
			// linkSource is a port of sourceNode
			Port sourcePort = ((PortNode) linkSource).port();
			PortNode sourcePortNode = auxNode.port(sourcePort.record(), sourcePort.compass());
			sourcePortNode.addTo(graph);
			if (linkTarget instanceof PortNode) {
				// linkTarget is a port of targetNode
				Port targetPort = ((PortNode) linkTarget).port();
				PortNode targetPortNode = targetNode.port(targetPort.record(), targetPort.compass());
				auxNode.addLink(sourcePortNode.linkTo(targetPortNode));
			}
			else {
				// linkTarget == targetNode
				auxNode.addLink(sourcePortNode.linkTo(targetNode));
			}
		}
		else {
			// linkSource == sourceNode
			if (linkTarget instanceof PortNode) {
				// linkTarget is a port of targetNode
				Port targetPort = ((PortNode) linkTarget).port();
				PortNode targetPortNode = targetNode.port(targetPort.record(), targetPort.compass());
				auxNode.addLink(targetPortNode);
			}
			else {
				// linkTarget == targetNode
				auxNode.addLink(targetNode);
			}
		}
		// at this point auxNode contains a single link (the created one)
		//   no mather the route followed above
		return auxNode.links().get(0);
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
		diffView.setPromise(new DotDiffContentPromise(this));
		diffView.setFormat("graphviz-dot");
	}

	public static String getSvgEvents() throws IOException {
		if (svgEvents == null) {
			if (PictoDiffPlugin.getDefault() == null) {
				// Standalone java
				svgEvents = new String(Files.readAllBytes(Paths.get(SVG_EVENTS_FILE)));
			}
			else {
				// Eclipse plugin (works, but there is probably an easier way to do this?)
				svgEvents = new String(Files.readAllBytes(Paths.get(FileLocator.resolve(
						PictoDiffPlugin.getDefault().getBundle().getEntry(SVG_EVENTS_FILE)).getPath())));
			}
		}
		return svgEvents;
	}
}
