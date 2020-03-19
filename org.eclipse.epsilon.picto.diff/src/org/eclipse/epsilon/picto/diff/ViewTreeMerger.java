package org.eclipse.epsilon.picto.diff;

import java.util.List;

import org.eclipse.epsilon.picto.ViewTree;

public class ViewTreeMerger {

//	public static ViewTree merge(ViewTree left, ViewTree right) {
//		ViewTree result = new ViewTree("Model Diff View");
//
//		result = append(result, calculateDifference(left, right), "Differences");
//		result = append(result, left, "Left Model");
//		result = append(result, right, "Right Model");
//
//		return result;
//	}

	public static void append(ViewTree left, ViewTree right, List<String> parentPath, String leafName) {

		// don't create an extra children level if the appended viewtree is anonymous
		ViewTree child = new ViewTree(leafName);
		if (right.getName().equals("")) {
			child.setPromise(right.getPromise());
			child.setFormat(right.getFormat());
			child.setIcon(right.getIcon());
		}
		else {
			child.getChildren().add(right);
		}
		left.getChildren().add(child);
	}

	protected static ViewTree calculateDifference(ViewTree left, ViewTree right) {
		return new ViewTree("Difference");
	}
}
