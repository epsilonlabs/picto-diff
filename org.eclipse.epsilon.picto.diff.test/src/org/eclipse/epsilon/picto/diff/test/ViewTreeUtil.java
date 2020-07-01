package org.eclipse.epsilon.picto.diff.test;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;

public class ViewTreeUtil {

	public static ViewTree getFromFile(String file, String format) throws Exception {
		String fileContents = new String(Files.readAllBytes(Paths.get(file)));
		ViewTree viewTree = new ViewTree();
		viewTree.setPromise(new StaticContentPromise(fileContents));
		viewTree.setFormat(format);
		return viewTree;
	}
}
