package org.eclipse.epsilon.picto.diff;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffEngine;
import org.eclipse.epsilon.picto.diff.engines.dummy.DummyDiffEngine;

public class ViewTreeMerger {

	static final List<DiffEngine> DIFF_ENGINES =
			Arrays.asList(
					new DotDiffEngine(),
					new DummyDiffEngine());

	public static void main(String[] args) throws Exception {

		String dot1 = new String(Files.readAllBytes(Paths.get("files/simple_filesystem.dot")));
		String dot2 = new String(Files.readAllBytes(Paths.get("files/simple_filesystem2.dot")));

		ViewTree v1 = new ViewTree();
		v1.setPromise(new StringContentPromise(dot1));
		v1.setFormat("graphviz-dot");

		ViewTree v2 = new ViewTree();
		v2.setFormat("graphviz-dot");
		v2.setPromise(new StringContentPromise(dot2));

		ViewTree diffView = new ViewTree();
		diff(diffView, v1, v2);

		System.out.println(diffView.getPromise().getContent());
		Files.write(Paths.get("example/simple_filesystemdiff.html"),
				diffView.getPromise().getContent().getBytes(),
				StandardOpenOption.CREATE);
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
			for (DiffEngine engine : DIFF_ENGINES) {
				if (engine.supports(left.getFormat())) {
					engine.diff(diffView, left, right);
					break;
				}
			}
		}
	}
}
