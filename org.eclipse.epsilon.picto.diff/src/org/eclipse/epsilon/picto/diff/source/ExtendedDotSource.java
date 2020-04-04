package org.eclipse.epsilon.picto.diff.source;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.dot.util.GraphPromiseGenerator;
import org.eclipse.epsilon.picto.diff.engines.dot.util.SubGraphPromise;
import org.eclipse.epsilon.picto.source.DotSource;
import org.eclipse.ui.IEditorPart;

import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;

public class ExtendedDotSource extends DotSource {

	@Override
	public String getFileExtension() {
		return "edot";
	}

	@Override
	public String getFormat() {
		return "graphviz-dot";
	}

	protected String getIcon() {
		return "diagram-ff0000";
	}

	@Override
	public ViewTree getViewTree(IEditorPart editor) throws Exception {
		IFile iFile = waitForFile(editor);
		if (iFile == null)
			return createEmptyViewTree();

		InputStream fileStream = new FileInputStream(iFile.getLocation().toOSString());
		MutableGraph graph = new Parser().read(fileStream);
		GraphPromiseGenerator promise = new GraphPromiseGenerator(graph.copy());

		ViewTree viewTree = new ViewTree();
		ArrayList<String> paths = new ArrayList<String>();

		paths.add("Graph");
		StringContentPromise graphPromise = new StringContentPromise(promise.getDotGraph());
		viewTree.add(paths, new ViewTree(graphPromise, getFormat(), getIcon(), Collections.emptyList(), new ArrayList<>()));
		paths.remove(0);

		paths.add("Nodes");
		HashMap<String, SubGraphPromise> source_map = promise.getPromiseMap();
		List<String> nodeNames = new ArrayList<>(source_map.keySet());
		Collections.sort(nodeNames);
		for (String key : nodeNames) {
			paths.add(key);
			viewTree.add(
					paths, new ViewTree(source_map.get(key), getFormat(), getIcon(), Collections.emptyList(), new ArrayList<>()));
			paths.remove(paths.size() - 1);
		}
		return viewTree;
	}
}
