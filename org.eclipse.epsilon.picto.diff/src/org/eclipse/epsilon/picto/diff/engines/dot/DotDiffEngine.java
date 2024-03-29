package org.eclipse.epsilon.picto.diff.engines.dot;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
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
	
	private static final String SVG_EVENTS_FILE = "transformations/svgEvents.html";
	private static String svgEvents;

	private static final double GRAPH_SCALE = 1.3;

	private enum ADD_MODE {ADDED, CHANGED, REMOVED, NORMAL};
	protected DotDiffContext context = null;

	protected MutableGraph source_temp;
	protected MutableGraph target_temp;

	protected PictoDiffValidator graphValidator = new PictoDiffValidator();
	
	protected Set<MutableNode> changedNodes = new HashSet<>();
	protected Set<MutableNode> addedNodes = new HashSet<>();
	protected Set<MutableNode> removedNodes = new HashSet<>();
	
	protected Map<MutableNode, Set<Link>> unchangedLinks = new HashMap<>();
	protected Map<MutableNode, Set<Link>> addedLinks = new HashMap<>();
	protected Map<MutableNode, Set<Link>> removedLinks = new HashMap<>();
	protected Map<MutableNode, Set<Link>> changedLinks = new HashMap<>();
	
	protected Map<MutableNode, Set<String>> addedAttrs = new HashMap<>();
	protected Map<MutableNode, Set<String>> removedAttrs = new HashMap<>();
	protected Map<MutableNode, Set<String>> changedAttrs = new HashMap<>();
	protected Map<MutableNode, Set<String>> unchangedAttrs = new HashMap<>();
	
	public static void main(String[] args) throws Exception {

		String filesLocationFormat = "files/dotDiffEngine/%s";
		String outputFolder = "diffResult/dotDiffEngine";
		String outputLocationFormat = outputFolder + "/%s-diffResult.html";

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
				source_temp = mutGraph();
				source_temp.setDirected(true);
				source_temp.graphAttrs().add(context.getSourceGraph().graphAttrs());
				
				target_temp = mutGraph();
				target_temp.setDirected(true);
				target_temp.graphAttrs().add(context.getTargetGraph().graphAttrs());
			}
		} catch (IOException e) {
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
		// add new links of newly added nodes (e.g. links where the source is
		//   an added node). Done after the general comparison to be sure that
		//   any other change over the already existing nodes is treated first
		processAddedNodesLinks();

		// include unchanged links between nodes of the previous version graph,
		//     to improve the autolayout of the diagram (with these new links we
		//     wish to obtain diagrams that resemble better the original ones
		// this method only adds links between the existing nodes that have been
		//     added due to the detected changes
		includeExtraLinksInSourceTemp();

		// same thing, for the current version graph
		includeExtraLinksInTargetTemp();
	}

	private void includeExtraLinksInTargetTemp() {
		for (MutableNode node_targetTemp : getAllNodes(target_temp)) {
			MutableNode node_target =
					findNode(node_targetTemp.name().value(), getUnmutableTargetNodes());
			for (Link link : node_target.links()) {
				MutableNode linkTarget = findLinkTarget(context.targetGraph, link);
				MutableNode linkTarget_targetTemp = findNodeInTargetTemp(linkTarget);
				if (linkTarget_targetTemp != null &&
						findLink(target_temp,
								node_targetTemp,
								(String) link.attrs().get("name")) == null) {
					Link newLink = link(target_temp,
							node_targetTemp,
							linkTarget_targetTemp,
							link.from(), link.to());
					copyLinkAttributes(newLink, link);
				}
			}
		}
	}

	private void includeExtraLinksInSourceTemp() {
		for (MutableNode node_sourceTemp : getAllNodes(source_temp)) {
			MutableNode node_source =
					findNode(node_sourceTemp, getUnmutableSourceNodes());
			for (Link link : node_source.links()) {
				MutableNode linkTarget = findLinkTarget(context.sourceGraph, link);
				MutableNode linkTarget_sourceTemp = findNodeInSourceTemp(linkTarget);
				if (linkTarget_sourceTemp != null &&
						findLink(source_temp, node_sourceTemp,
								(String) link.attrs().get("name")) == null) {
					Link newLink = link(source_temp,
							node_sourceTemp, linkTarget_sourceTemp,
							link.from(), link.to());
					copyLinkAttributes(newLink, link);
					// mark link as deleted if the node is also marked as such
					if (removedNodes.contains(node_source)) {
						DotDiffUtil.paintDeleted(newLink);
					}
				}
			}
		}
	}

	private Collection<MutableNode> getAllNodes(MutableGraph graph) {
		Set<MutableNode> nodes = new HashSet<>();
		nodes.addAll(graph.nodes());
		for (MutableGraph subgraph : graph.graphs()) {
			nodes.addAll(getAllNodes(subgraph));
		}
		return nodes;
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
			addIfNotFoundInSourceTemp(node, ADD_MODE.REMOVED);
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
				changedNodes.add(right_node);
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
			removedNodes.add(left_node);
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
			// mark link deletions in the previous graph to balance information density
			DotDiffUtil.paintDeleted(left_link);
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

			addAddedLink(right_node_copy, right_link);

			// the source is the right node
			MutableNode linkSource = right_node;
			MutableNode linkSource_targetTemp =
					addIfNotFoundInTargetTemp(linkSource, ADD_MODE.NORMAL);

			MutableNode linkTarget = findLinkTarget(context.getTargetGraph(), right_link);
			MutableNode linkTarget_targetTemp =
					addIfNotFoundInTargetTemp(linkTarget, ADD_MODE.NORMAL);

			Link link = link(target_temp,
					linkSource_targetTemp, linkTarget_targetTemp,
					right_link.from(), right_link.to());
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
	
	public MutableGraph clusterWrap(MutableNode node, ADD_MODE mode) {
		MutableGraph g = mutGraph();
		switch (mode) {
		case ADDED:
			DotDiffUtil.paintAdded(g);
			break;
		case CHANGED:
			DotDiffUtil.paintChanged(g);
			break;
		case REMOVED:
			DotDiffUtil.paintDeleted(g);
			break;
		case NORMAL: // unchanged
		}
		g.graphAttrs().add("label", "");
		g.setCluster(true);
		g.rootNodes().add(node);
		g.setName(node.name().toString());
		return g;
	}

	public MutableNode addNodeToSourceTemp(MutableNode node) {
		return addNodeToSourceTemp(node, ADD_MODE.NORMAL);
	}

	public MutableNode addNodeToSourceTemp(MutableNode node, ADD_MODE mode) {
		MutableNode copy = getNodeCopy(node);
		MutableGraph g = clusterWrap(copy, mode);
		source_temp.graphs().add(g);
		return copy;
	}
	
	public MutableNode addNodeToTargetTemp(MutableNode node, ADD_MODE mode) {
		MutableNode copy = getNodeCopy(node);
		MutableGraph g = clusterWrap(copy, mode);
		target_temp.graphs().add(g);
		return copy;
	}
	
	/**
	 * Add if not found in the sourceTemp graph
	 */
	public MutableNode addIfNotFoundInSourceTemp(MutableNode node) {
		return addIfNotFoundInSourceTemp(node, ADD_MODE.NORMAL);
	}

	public MutableNode addIfNotFoundInSourceTemp(MutableNode node, ADD_MODE mode) {
		MutableNode node_sourceTemp = findNodeInSourceTemp(node);
		if (node_sourceTemp == null) {
			node_sourceTemp = addNodeToSourceTemp(node, mode);
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
		return findNodeByName(node.name().value(), target_temp);
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
		return findNode(node.name().value(), nodeCollection);
	}

	private MutableNode findNode(String name, Collection<MutableNode> nodeCollection) {
		for (MutableNode n : nodeCollection) {
			if (name.equals(n.name().value())) {
				return n;
			}
		}
		return null;
	}

	/**
	 * This method does an exhaustive search, including the special nodes that are
	 * added to the temp graphs when cross-cluster links are created. This
	 * special nodes are needed to prevent that the linked clusters are
	 * automatically merged when generating the dot (don't know why that happens)
	 */
	public Link findLink(MutableGraph graph, MutableNode node, String name) {
		Link link = findLink(node, name);
		if (link != null) {
			return link;
		}
		for (MutableNode otherNode : getAllNodes(graph)) {
			if (equalsByName(node, otherNode)) {
				link = findLink(otherNode, name);
				if (link != null) {
					return link;
				}
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

	private void addUnchangedLink(MutableNode node, Link link) {
		Set<Link> links = unchangedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
			links.add(link);
			unchangedLinks.put(node, links);
		}
		else {
			links.add(link);	
		}
	}
	
	private void addAddedLink(MutableNode node, Link link) {
		Set<Link> links = addedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
			links.add(link);
			addedLinks.put(node, links);
		}
		else {
			links.add(link);
		}
	}

	private void addChangedLink(MutableNode node, Link link) {
		Set<Link> links = changedLinks.get(node);
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
		Set<Link> links = removedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
			links.add(link);
			removedLinks.put(node, links);
		}
		else {
			links.add(link);
		}
	}
	
	public Set<Link> getUnchangedLinks(MutableNode node) {
		Set<Link> links = unchangedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	public Set<Link> getChangedLinks(MutableNode node) {
		Set<Link> links = changedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	public Set<Link> getAddedLinks(MutableNode node) {
		Set<Link> links = addedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	public Set<Link> getRemovedLinks(MutableNode node) {
		Set<Link> links = removedLinks.get(node);
		if (links == null) {
			links = new HashSet<Link>();
		}
		return links;
	}
	
	private void addChangedAttr(MutableNode node, String attr) {
		Set<String> attrs = changedAttrs.get(node);
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
		Set<String> attrs = unchangedAttrs.get(node);
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
		Set<String> attrs = removedAttrs.get(node);
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
		Set<String> attrs = addedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
			attrs.add(attr);
			addedAttrs.put(node, attrs);
		}
		else {
			attrs.add(attr);
		}
	}
	
	public Set<String> getChangedAttrs(MutableNode node) {
		Set<String> attrs = changedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
		}
		return attrs;
	}
	
	public Set<String> getAddedAttrs(MutableNode node) {
		Set<String> attrs = addedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
		}
		return attrs;
	}
	
	public Set<String> getRemovedAttrs(MutableNode node) {
		Set<String> attrs = removedAttrs.get(node);
		if (attrs == null) {
			attrs = new HashSet<String>();
		}
		return attrs;
	}
	
	public String getOldVersion(Format format) {
		return Graphviz.fromGraph(source_temp).scale(GRAPH_SCALE).render(format).toString();
	}
	
	public String getNewVersion(Format format) {
		return Graphviz.fromGraph(target_temp).scale(GRAPH_SCALE).render(format).toString();
	}

	public boolean oldVersionEmpty() {
		return getAllNodes(source_temp).isEmpty();
	}

	public boolean newVersionEmpty() {
		return getAllNodes(target_temp).isEmpty();
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

	@Override
	public boolean supports(String format) {
		return format.equals("graphviz-dot");
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {
		this.context = new DotDiffContext(left.getContent().getText(), right.getContent().getText());
		DotDiffContentPromise promise = new DotDiffContentPromise(this);
		diffView.setPromise(promise);
		diffView.setFormat(promise.getFormat());
	}

	public static String getSvgEvents() throws IOException {
		if (svgEvents == null) {
			svgEvents = PictoDiffPlugin.getFileContents(SVG_EVENTS_FILE);
		}
		return svgEvents;
	}

	public Set<MutableNode> getChangedNodes() {
		return changedNodes;
	}

	public Set<MutableNode> getAddedNodes() {
		return addedNodes;
	}

	public Set<MutableNode> getRemovedNodes() {
		return removedNodes;
	}

	public Map<MutableNode, Set<Link>> getAddedLinks() {
		return addedLinks;
	}

	public Map<MutableNode, Set<Link>> getRemovedLinks() {
		return removedLinks;
	}
}
