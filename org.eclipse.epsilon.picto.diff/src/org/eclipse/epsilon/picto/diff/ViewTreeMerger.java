package org.eclipse.epsilon.picto.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;

public class ViewTreeMerger {

	public static void main(String[] args) throws Exception {

		ViewTree pathTree = new ViewTree();
		pathTree.addPath(Arrays.asList("e1", "e2"),
				new StringContentPromise("c1"), "text", "", Collections.emptyList());
		pathTree.addPath(Arrays.asList("e1", "e3", "e4"),
				new StringContentPromise("c2"), "text", "", Collections.emptyList());
		pathTree.addPath(Arrays.asList("e1", "e3", "e5"),
				new StringContentPromise("This is a very very long content in a view tree"), "text", "",
				Collections.emptyList());
		System.out.println(pathTree);

	}

	public static ViewTree diffMerge(ViewTree left, ViewTree right) throws Exception {
		return diffMerge(new ViewTree(), Arrays.asList(""), left, right);
	}

	private static ViewTree diffMerge(ViewTree mergedViewTree, List<String> currentPath,
			ViewTree left, ViewTree right) throws Exception {

		ViewTree currentLeft = left.forPath(currentPath);
		ViewTree currentRight = right.forPath(currentPath);
		List<ViewTree> remainingRightChildren = new ArrayList<>(currentRight.getChildren());

		for (ViewTree leftChild : currentLeft.getChildren()) {
			ViewTree counterpart = null;
			ViewTree diff = null;
			for (ViewTree rightChild : remainingRightChildren) {
				if (leftChild.getName().equals(rightChild.getName())) {
					counterpart = rightChild;
					remainingRightChildren.remove(counterpart);
					diff = copy(leftChild);
					if (leftChild.getPromise() == null && counterpart.getPromise() == null) {
						diff.setPromise(new StringContentPromise("Both viewtrees empty"));
						diff.setFormat("text");
					}
					else if (leftChild.getPromise() == null || counterpart.getPromise() == null) {
						diff.setPromise(new StringContentPromise("Some viewtree empty"));
						diff.setFormat("text");
					}
					else if (leftChild.getPromise().getContent().equals(counterpart.getPromise().getContent())) {
						diff.setName(String.format("%s (Same)", diff.getName()));
					}
					else {
						diff.setName(String.format("%s (Modified)", diff.getName()));
						diff.setPromise(new StringContentPromise(String.format("%s: Content differs", leftChild.getName())));
						diff.setFormat("text");
					}
					if (!leftChild.getChildren().isEmpty()) {
						List<String> newPath = new ArrayList<>(currentPath);
						newPath.add(leftChild.getName());
						diff = diffMerge(diff, newPath, left, right);
					}
					break;
				}
			}
			if (counterpart == null) {
				// deleted elements
				diff = copy(leftChild);
				diff.setName(String.format("%s (Deleted)", leftChild.getName()));
			}
			append(mergedViewTree, diff, "");
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

}
