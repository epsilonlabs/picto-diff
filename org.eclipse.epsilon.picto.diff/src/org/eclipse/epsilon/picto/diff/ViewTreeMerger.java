package org.eclipse.epsilon.picto.diff;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.dummy.DummyDiffEngineFactory;

public class ViewTreeMerger {

	static final List<DiffEngineFactory> DIFF_ENGINE_FACTORIES =
			Arrays.asList(
					new DotDiffEngineFactory(),
					new DummyDiffEngineFactory());

	public static void main(String[] args) throws Exception {

		String filesLocationFormat = "files/%s";
		String outputFolder = "diffResult";
		String outputLocationFormat = outputFolder + "/%s-diffResult.html";

		File directory = new File(outputFolder);
		if (!directory.exists()) {
			directory.mkdirs();
		}

		String baseline = new String(
				Files.readAllBytes(Paths.get(String.format(filesLocationFormat, "baseline.dot"))));
		ViewTree baselineView = new ViewTree();
		baselineView.setPromise(new StringContentPromise(baseline));
		baselineView.setFormat("graphviz-dot");

		List<String> modifiedFiles = Arrays.asList(
				"baseline-addProperty.dot",
				"baseline-addNode.dot",
				"baseline-addEdges.dot",
				"baseline-addEdgeToNewNode.dot",
				"baseline-removeNodes.dot",
				"baseline-removeEdge.dot",
				"baseline-modifyEdges.dot",
				"baseline-modifyEdgesToNewNodes.dot");

		//		modifiedFiles = Arrays.asList("baseline-addEdges.dot");

		for (String file : modifiedFiles) {

			String modifiedFile = String.format(filesLocationFormat, file);
			String modifiedDot = new String(Files.readAllBytes(Paths.get(modifiedFile)));
			ViewTree modifiedView = new ViewTree();
			modifiedView.setFormat("graphviz-dot");
			modifiedView.setPromise(new StringContentPromise(modifiedDot));

			ViewTree diffView = new ViewTree();
			try {
				diff(diffView, baselineView, modifiedView);
				Files.write(Paths.get(String.format(outputLocationFormat, file)),
						diffView.getPromise().getContent().getBytes(),
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

	public static ViewTree diffMerge(ViewTree left, ViewTree right) throws Exception {
		return diffMerge(new ViewTree(), Arrays.asList(""), left, right);
	}

	private static ViewTree diffMerge(ViewTree mergedViewTree, List<String> currentPath,
			ViewTree left, ViewTree right) throws Exception {

		if (currentPath.size() == 1) {
			// compare roots of the viewtrees
			diff(mergedViewTree, left, right);
		}

		ViewTree currentLeft = left.forPath(currentPath);
		ViewTree currentRight = right.forPath(currentPath);
		List<ViewTree> remainingRightChildren = new ArrayList<>(currentRight.getChildren());

		for (ViewTree leftChild : currentLeft.getChildren()) {
			ViewTree counterpart = null;
			ViewTree diffView = null;
			for (ViewTree rightChild : remainingRightChildren) {
				if (leftChild.getName().equals(rightChild.getName())) {
					counterpart = rightChild;
					remainingRightChildren.remove(counterpart);
					diffView = copy(leftChild);
					diff(diffView, leftChild, counterpart);
					if (!leftChild.getChildren().isEmpty()) {
						List<String> newPath = new ArrayList<>(currentPath);
						newPath.add(leftChild.getName());
						diffView = diffMerge(diffView, newPath, left, right);
					}
					break;
				}
			}
			if (counterpart == null) {
				// deleted elements
				diffView = copy(leftChild);
				diffView.setName(String.format("%s (Deleted)", leftChild.getName()));
			}
			append(mergedViewTree, diffView, "");
		}

		for (ViewTree remainingChild : remainingRightChildren) {
			// new elements
			ViewTree newChild = copy(remainingChild);
			newChild.setName(String.format("%s (New)", remainingChild.getName()));
			newChild.getChildren().addAll(remainingChild.getChildren());
			append(mergedViewTree, newChild, "");
		}

		return mergedViewTree;
	}

	public static void append(ViewTree left, ViewTree right, String leafName) {

		ViewTree child = null;
		// don't create an extra children level if the appended viewtree is anonymous
		// or if the provided leaf name is empty
		if (right.getName().equals("") || leafName.equals("")) {
			child = copy(right);
			if (leafName.equals("")) {
				child.setName(right.getName());
			}
			else {
				child.setName(leafName);
			}
			child.getChildren().addAll(right.getChildren());
		}
		else {
			child = new ViewTree(leafName);
			child.getChildren().add(right);
		}
		left.getChildren().add(child);
	}

	/**
	 * Copy Viewtree (ignores children)
	 */
	private static ViewTree copy(ViewTree viewTree) {
		ViewTree copy = new ViewTree(viewTree.getName());
		copy.setPromise(viewTree.getPromise());
		copy.setName(viewTree.getName());
		copy.setFormat(viewTree.getFormat());
		copy.setIcon(viewTree.getIcon());
		return copy;
	}

	private static void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {
		if (left.getPromise() == null && right.getPromise() == null) {
			if (!left.getName().equals("") && !right.getName().equals("")) {
				diffView.setPromise(new StringContentPromise("Both viewtrees empty"));
				diffView.setFormat("text");
			}
		}
		else if (left.getPromise() == null || right.getPromise() == null) {
			diffView.setPromise(new StringContentPromise("Some viewtree empty"));
			diffView.setFormat("text");
		}
		else if (left.getPromise().getContent().equals(right.getPromise().getContent())) {
			diffView.setName(String.format("%s (Same)", diffView.getName()));
		}
		else {
			diffView.setName(String.format("%s (Modified)", diffView.getName()));
			for (DiffEngineFactory engineFactory : DIFF_ENGINE_FACTORIES) {
				DiffEngine engine = engineFactory.createDiffEngine();
				if (engine.supports(left.getFormat())) {
					engine.diff(diffView, left, right);
					break;
				}
			}
		}
	}
}
