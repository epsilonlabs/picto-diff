package org.eclipse.epsilon.picto.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewContent;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.dot.DotDiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.dummy.DummyDiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.html.HtmlDiffEngineFactory;
import org.eclipse.epsilon.picto.diff.engines.sidebyside.SideBySideDiffEngineFactory;

public class ViewTreeMerger {

	static final List<DiffEngineFactory> DIFF_ENGINE_FACTORIES =
			Arrays.asList(
					new DotDiffEngineFactory(),
					new HtmlDiffEngineFactory(),
					new SideBySideDiffEngineFactory());

	public static ViewTree diffMerge(ViewTree oldTree, ViewTree newTree,
			String manualDiffEngine) throws Exception {

		ViewTree diffView = new ViewTree();
		diffMerge(diffView, Arrays.asList(""),	oldTree, newTree,
				getDiffEngineFactory(manualDiffEngine));
		return diffView;
	}

	private static void diffMerge(ViewTree mergedView, List<String> currentPath,
			ViewTree oldTree, ViewTree newTree, DiffEngineFactory engineFactory) throws Exception {

		if (currentPath.size() == 1) {
			// compare roots of the viewtrees
			diff(mergedView, oldTree, newTree, engineFactory);
		}

		ViewTree currentOld = oldTree.forPath(currentPath);
		ViewTree currentNew = newTree.forPath(currentPath);
		List<ViewTree> remainingNewChildren = new ArrayList<>(currentNew.getChildren());

		for (ViewTree oldChild : currentOld.getChildren()) {
			ViewTree counterpart = null;
			for (ViewTree newChild : remainingNewChildren) {
				if (oldChild.getName().equals(newChild.getName())) {
					counterpart = newChild;
					remainingNewChildren.remove(counterpart);
					ViewTree diffView = copy(oldChild);
					diff(diffView, oldChild, counterpart, engineFactory);
					if (!oldChild.getChildren().isEmpty()) {
						List<String> newPath = new ArrayList<>(currentPath);
						newPath.add(oldChild.getName());
						diffMerge(diffView, newPath, oldTree, newTree, engineFactory);
					}
					mergedView.getChildren().add(diffView);
					break;
				}
			}
			if (counterpart == null) {
				// deleted elements
				ViewTree deletedView = copy(oldChild);
				deletedView.setIcon("pdiff-deleted");
				copyChildrenWithIcon(deletedView, oldChild.getChildren(), "pdiff-deleted");
				mergedView.getChildren().add(deletedView);
			}
		}

		// any remaining children is a new element
		copyChildrenWithIcon(mergedView, remainingNewChildren, "pdiff-added");
	}

	private static void copyChildrenWithIcon(ViewTree newView,
			List<ViewTree> copiedChildren, String icon) {
		
		for (ViewTree copiedChild : copiedChildren) {
			ViewTree newChild = copy(copiedChild);
			newChild.setIcon(icon);
			newView.getChildren().add(newChild);
			copyChildrenWithIcon(newChild, copiedChild.getChildren(), icon);
		}
	}

	/**
	 * Append an existing ViewTree as child of another
	 * 
	 * @param left     Parent ViewTree
	 * @param right    The ViewTree to append as a child
	 * @param leafName An optional name, ignored if empty
	 */
	public static void append(ViewTree left, ViewTree right, String leafName) {

		ViewTree child = null;
		// don't create an extra children level if the appended viewtree is anonymous
		// or if the provided leaf name is empty
		if (right.getName().equals("") || leafName.equals("")) {
			child = copy(right);
			if (!leafName.equals("")) {
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

	private static void diff(ViewTree diffView, ViewTree oldView, ViewTree newView,
			DiffEngineFactory manualEngineFactory) throws Exception {

		if (isContentEmpty(oldView) && isContentEmpty(newView)) {
			if (!oldView.getName().equals("") && !newView.getName().equals("")) {
				diffView.setPromise(new StaticContentPromise(""));
				diffView.setFormat("text");
			}
		}
		else if (isContentEmpty(oldView) || isContentEmpty(newView)) {
			if (isContentEmpty(oldView)) {
				diffView.setPromise(new StaticContentPromise("Empty view on previous version"));
			}
			else {
				diffView.setPromise(new StaticContentPromise("Empty view on current version"));
			}
			diffView.setFormat("text");
		}
		else {
			ViewTree oldViewCopy = prepareForDiff(oldView);
			ViewTree newViewCopy = prepareForDiff(newView);

			if (contentEquals(oldViewCopy, newViewCopy)) {
				diffView.setIcon("pdiff-unchanged");
			}
			else {
				diffView.setIcon("pdiff-changed");
				DiffEngine engine = null;
				if (manualEngineFactory != null) {
					DiffEngine candidate = manualEngineFactory.createDiffEngine();
					if (candidate.supports(oldViewCopy.getFormat())) {
						engine = candidate;
					}
				}
				if (engine == null) {
					for (DiffEngineFactory factory : DIFF_ENGINE_FACTORIES) {
						DiffEngine candidate = factory.createDiffEngine();
						if (candidate.supports(oldViewCopy.getFormat())) {
							engine = candidate;
							break;
						}
					}
				}
				// engine cannot be null (at least SideBySideDiffEngine supports it)
				engine.diff(diffView, oldViewCopy, newViewCopy);
			}
		}
	}

	private static boolean isContentEmpty(ViewTree viewTree) {
		return viewTree.getContent().getText().equals("");
	}

	/**
	 * Remove any content text that might cause false positives when looking for
	 * changes between two viewTree versions
	 * 
	 * Also, remove links with editors and viewTree paths that would not work if
	 * still present in the merged tree
	 */
	private static ViewTree prepareForDiff(ViewTree viewTree) {
		ViewTree copy = copy(viewTree);
		ViewContent viewContent = copy.getContent();

		String text = viewContent.getText();

		String showViewPattern = "javascript:top.showView\\([^\"]*";
		text = text.replaceAll(showViewPattern, "");

		String showElementPattern = "javascript:top\\.showElement\\([^\"]*";
		text = text.replaceAll(showElementPattern, "");

		ViewContent viewContentCopy = new ViewContent(
				viewContent.getFormat(), text, viewContent.getFile(),
				viewContent.getLayers(), viewContent.getPatches(),
				viewContent.getBaseUris());
		copy.setContent(viewContentCopy);

		return copy;
	}

	private static boolean contentEquals(ViewTree left, ViewTree right) throws Exception {
		return left.getContent().getText().equals(right.getContent().getText());
	}

	private static DiffEngineFactory getDiffEngineFactory(String diffEngineName) {
		if (diffEngineName != null) {
			if (diffEngineName.equalsIgnoreCase("dot")) {
				return new DotDiffEngineFactory();
			}
			if (diffEngineName.equalsIgnoreCase("html")) {
				return new HtmlDiffEngineFactory();
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
