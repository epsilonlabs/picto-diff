package org.eclipse.epsilon.picto.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.dummy.DummyDiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.sidebyside.SideBySideDiffEngineFactory;

public class ViewTreeMerger {

	static final List<DiffEngineFactory> DIFF_ENGINE_FACTORIES =
			Arrays.asList(
					new DotDiffEngineFactory(),
					new SideBySideDiffEngineFactory());

	public static ViewTree diffMerge(ViewTree left, ViewTree right, String diffEngineName) throws Exception {
		return diffMerge(new ViewTree(), Arrays.asList(""), left, right, getDiffEngineFactory(diffEngineName));
	}

	private static ViewTree diffMerge(ViewTree mergedViewTree, List<String> currentPath,
			ViewTree left, ViewTree right, DiffEngineFactory engineFactory) throws Exception {

		if (currentPath.size() == 1) {
			// compare roots of the viewtrees
			diff(mergedViewTree, left, right, engineFactory);
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
					diff(diffView, leftChild, counterpart, engineFactory);
					if (!leftChild.getChildren().isEmpty()) {
						List<String> newPath = new ArrayList<>(currentPath);
						newPath.add(leftChild.getName());
						diffView = diffMerge(diffView, newPath, left, right, engineFactory);
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
		// careful with programmatically generated content (setting promises
		//   overrides cachedContent, i.e. any manually set content)
		if (viewTree.getPromise() != null) {
			copy.setPromise(viewTree.getPromise());
		}
		else if (viewTree.getCachedContent() != null) {
			copy.setContent(viewTree.getCachedContent());
		}
		copy.setName(viewTree.getName());
		copy.setFormat(viewTree.getFormat());
		copy.setIcon(viewTree.getIcon());
		return copy;
	}

	private static void diff(ViewTree diffView, ViewTree left, ViewTree right, DiffEngineFactory engineFactory) throws Exception {
		if (left.getPromise() == null && right.getPromise() == null) {
			if (!left.getName().equals("") && !right.getName().equals("")) {
				diffView.setPromise(new StaticContentPromise("Both viewtrees empty"));
				diffView.setFormat("text");
			}
		}
		else if (left.getPromise() == null || right.getPromise() == null) {
			diffView.setPromise(new StaticContentPromise("Some viewtree empty"));
			diffView.setFormat("text");
		}
		else if (left.getPromise().getContent().equals(right.getPromise().getContent())) {
			diffView.setName(String.format("%s (Same)", diffView.getName()));
		}
		else {
			diffView.setName(String.format("%s (Modified)", diffView.getName()));
			if (engineFactory != null) {
				DiffEngine engine = engineFactory.createDiffEngine();
				if (engine.supports(left.getFormat())) {
					engine.diff(diffView, left, right);
				}
				else {
					diffView.setPromise(new StaticContentPromise(engine.getClass().getName() +
							" does not support the format of the compared files"));
					diffView.setFormat("text");
				}
			}
			else {
				for (DiffEngineFactory factory : DIFF_ENGINE_FACTORIES) {
					DiffEngine engine = factory.createDiffEngine();
					if (engine.supports(left.getFormat())) {
						engine.diff(diffView, left, right);
						break;
					}
				}
			}
		}
	}

	private static DiffEngineFactory getDiffEngineFactory(String diffEngineName) {
		if (diffEngineName != null) {
			if (diffEngineName.equalsIgnoreCase("dot")) {
				return new DotDiffEngineFactory();
			}
			if (diffEngineName.equalsIgnoreCase("sidebyside")) {
				return new SideBySideDiffEngineFactory();
			}
			if (diffEngineName.equalsIgnoreCase("dummy")) {
				return new DummyDiffEngineFactory();
			}
		}
		return null;
	}
}
