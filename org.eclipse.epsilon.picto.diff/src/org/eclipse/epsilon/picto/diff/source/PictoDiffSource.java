package org.eclipse.epsilon.picto.diff.source;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.FileWrapperEditorPart;
import org.eclipse.epsilon.picto.diff.ViewTreeMerger;
import org.eclipse.epsilon.picto.dom.Parameter;
import org.eclipse.epsilon.picto.dom.Picto;
import org.eclipse.epsilon.picto.dom.PictoFactory;
import org.eclipse.epsilon.picto.source.PictoSource;
import org.eclipse.epsilon.picto.source.PictoSourceExtensionPointManager;
import org.eclipse.epsilon.picto.source.StandalonePictoSource;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

public class PictoDiffSource extends StandalonePictoSource {

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
		Picto metadata = null;
		if (diffFile.exists()) {
			metadata = super.getRenderingMetadata(editorPart);
			if (metadata == null || !metadata.isStandalone()) {
				metadata = getFromSimpleFile(diffFile);
			}
		}
		return metadata;
	}

	public Picto getFromSimpleFile(IFile diffFile) {
		Picto metadata = PictoFactory.eINSTANCE.createPicto();
		metadata.setTemplate(diffFile.getLocation().toOSString());
		metadata.setFormat(getFormat());
		metadata.setStandalone(true);
		// get the two internal picto files as parameters of the Picto dom
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
		}
		catch (Exception e) {
			return null;
		}
		return metadata;
	}

	@Override
	public boolean supports(IEditorPart editorPart) {
		// no calls to super as long as we want support for simple diff files
		if (supportsEditorType(editorPart)) {
			Picto picto = getRenderingMetadata(editorPart);
			return picto != null && picto.isStandalone();
		}
		return false;
	}

	@Override
	public ViewTree getViewTree(IEditorPart editor) throws Exception {

		Picto pictoDiff = getRenderingMetadata(editor);
		List<Parameter> parameters = pictoDiff.getParameters();
		Parameter pLeft = parameters.stream().filter(p -> p.getName().equals("left")).findFirst().get();
		Parameter pRight = parameters.stream().filter(p -> p.getName().equals("right")).findFirst().get();

		IProject project = null;
		if (editor.getEditorInput() instanceof IFileEditorInput) {
			IFileEditorInput input = (IFileEditorInput) editor.getEditorInput();
			project = input.getFile().getProject();
		}
		if (project == null) {
			return createEmptyViewTree();
		}
		FileWrapperEditorPart leftWrapper =
				new FileWrapperEditorPart(project.getFile(new Path(pLeft.getFile())));
		FileWrapperEditorPart rightWrapper =
				new FileWrapperEditorPart(project.getFile(new Path(pRight.getFile())));

		PictoSource leftPictoSource = getSource(leftWrapper);
		PictoSource rightPictoSource = getSource(rightWrapper);

		ViewTree leftViewTree = leftPictoSource.getViewTree(leftWrapper);
		ViewTree rightViewTree = rightPictoSource.getViewTree(rightWrapper);
		ViewTree mergedDiffViewTree = ViewTreeMerger.diffMerge(leftViewTree, rightViewTree);

		ViewTree viewTree = new ViewTree();
		ViewTreeMerger.append(viewTree, mergedDiffViewTree, "Differences");
		ViewTreeMerger.append(viewTree, leftViewTree, "Original Left");
		ViewTreeMerger.append(viewTree, rightViewTree, "Original Right");

		return viewTree;
	}

	protected PictoSource getSource(IEditorPart editorPart) {
		List<PictoSource> sources =
				new PictoSourceExtensionPointManager().getExtensions();
		for (PictoSource source : sources) {
			if (source.supports(editorPart)) {
				return source;
			}
		}
		return null;
	}

}
