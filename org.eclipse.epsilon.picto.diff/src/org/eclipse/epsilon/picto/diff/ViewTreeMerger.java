package org.eclipse.epsilon.picto.diff;

import org.eclipse.epsilon.picto.ViewTree;

public class ViewTreeMerger {

	public static ViewTree merge(ViewTree left, ViewTree right) {
		ViewTree result = new ViewTree("Model Diff View");

		result = append(result, calculateDifference(left, right), "Differences");
		result = append(result, left, "Left Model");
		result = append(result, right, "Right Model");

		return result;
	}

	protected static ViewTree append(ViewTree left, ViewTree right, String branchName) {

		ViewTree result = new ViewTree(left.getName());
		result.setPromise(left.getPromise());
		result.setFormat(left.getFormat());
		result.setIcon(result.getIcon());
		result.getChildren().addAll(left.getChildren());

		ViewTree child = new ViewTree(branchName);
		child.getChildren().add(right);

		result.getChildren().add(child);

		return result;
	}

	protected static ViewTree calculateDifference(ViewTree left, ViewTree right) {
		return new ViewTree("Difference");
	}
}
