package org.eclipse.epsilon.picto.diff.source;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.dom.Parameter;
import org.eclipse.epsilon.picto.dom.Picto;
import org.eclipse.epsilon.picto.dom.PictoFactory;
import org.eclipse.epsilon.picto.source.SimpleSource;
import org.eclipse.ui.IEditorPart;

public class PictoDiffSource extends SimpleSource {

	@Override
	public String getFormat() {
		return "html";
	}

	@Override
	public String getFileExtension() {
		return "pictodiff";
	}
	
	@Override
	public Picto getRenderingMetadata(IEditorPart editorPart) {
		IFile diffFile = getFile(editorPart);
		if (diffFile.exists()) {
			Picto metadata = PictoFactory.eINSTANCE.createPicto();
			metadata.setTemplate(getFile(editorPart).getLocation().toOSString());
			metadata.setFormat(getFormat());
			// get the two internal picto files as parameters of the Picto dom
			// TODO: check that both files have compatible representations/technologies for diffing
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(diffFile.getContents(true)));
				Parameter pLeft = PictoFactory.eINSTANCE.createParameter();
				pLeft.setName("left");
				pLeft.setFile(reader.readLine());
				metadata.getParameters().add(pLeft);
				Parameter pRight = PictoFactory.eINSTANCE.createParameter();
				pRight.setName("right");
				pRight.setFile(reader.readLine());
				metadata.getParameters().add(pRight);
			} catch (Exception e) {
				return null;
			}
			return metadata;
		}
		return null;
	}
	
	@Override
	public boolean supports(IEditorPart editorPart) {
		if (!super.supports(editorPart)) return false;
		Picto picto = getRenderingMetadata(editorPart);
		return picto != null;
	}
	
	@Override
	public ViewTree getViewTree(IEditorPart editor) throws Exception {
		ViewTree viewTree = new ViewTree();
		String graph_format = "html";
		String graph_icon = "diagram-ff0000";

		Picto pictoDiff = getRenderingMetadata(editor);
		List<Parameter> parameters = pictoDiff.getParameters();
		Parameter pLeft = parameters.stream().filter(p -> p.getName().equals("left")).findFirst().get();
		Parameter pRight = parameters.stream().filter(p -> p.getName().equals("right")).findFirst().get();

		// 1. include the diff ViewTree 
		ArrayList<String> path = new ArrayList<String>();
		path.add("Model");
		path.add("Differences");
		viewTree.addPath(path, new StringContentPromise("Diff file content"), graph_format, graph_icon);

		// 2. get the ViewTrees from both picto files being included
		path.remove(1);
		path.add("Left Graph");
		viewTree.addPath(path, new StringContentPromise(
				String.format("Rendering of the %s file", pLeft.getFile())), graph_format, graph_icon);

		path.remove(1);
		path.add("Right Graph");
		viewTree.addPath(path, new StringContentPromise(
				String.format("Rendering of the %s file", pRight.getFile())), graph_format, graph_icon);

		return viewTree;
	}

}
