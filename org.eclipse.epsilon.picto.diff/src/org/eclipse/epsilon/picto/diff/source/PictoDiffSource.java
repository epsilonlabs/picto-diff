package org.eclipse.epsilon.picto.diff.source;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

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

	public static final String OLD_VERSION_PARAMETER = "previous";
	public static final String NEW_VERSION_PARAMETER = "current";

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
		metadata.setTransformation(diffFile.getLocation().toOSString());
		metadata.setFormat(getFormat());
		metadata.setStandalone(true);
		// get the two internal picto files as parameters of the Picto dom
		// TODO: change "left" and "right" parameter names to more meaningful ones
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(diffFile.getContents(true)));
			Parameter pOld = PictoFactory.eINSTANCE.createParameter();
			pOld.setName(OLD_VERSION_PARAMETER);
			pOld.setFile(reader.readLine());
			metadata.getParameters().add(pOld);
			Parameter pNew = PictoFactory.eINSTANCE.createParameter();
			pNew.setName(NEW_VERSION_PARAMETER);
			pNew.setFile(reader.readLine());
			metadata.getParameters().add(pNew);
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
		Parameter pOld = getParameter(pictoDiff, "previous");
		Parameter pNew = getParameter(pictoDiff, "current");

		String diffEngine = null;
		Parameter diffEnginePar = getParameter(pictoDiff, "diffEngine");
		if (diffEnginePar != null) {
			diffEngine = (String) diffEnginePar.getValue();
		}

		IProject project = null;
		if (editor.getEditorInput() instanceof IFileEditorInput) {
			IFileEditorInput input = (IFileEditorInput) editor.getEditorInput();
			project = input.getFile().getProject();
		}
		if (project == null) {
			return createEmptyViewTree();
		}
		FileWrapperEditorPart oldVersionWrapper =
				new FileWrapperEditorPart(project.getFile(new Path(pOld.getFile())));
		FileWrapperEditorPart newVersionWrapper =
				new FileWrapperEditorPart(project.getFile(new Path(pNew.getFile())));

		PictoSource oldVersionSource = getSource(oldVersionWrapper);
		PictoSource newVersionSource = getSource(newVersionWrapper);

		ViewTree oldTree = oldVersionSource.getViewTree(oldVersionWrapper);
		ViewTree newTree = newVersionSource.getViewTree(newVersionWrapper);
		ViewTree mergedDiffViewTree = ViewTreeMerger.diffMerge(oldTree, newTree, diffEngine);

		ViewTree viewTree = new ViewTree();
		// set here base uri to find pictodiff icons
		viewTree.getBaseUris().add(new URI("platform:/plugin/org.eclipse.epsilon.picto.diff/icons/"));
		ViewTreeMerger.append(viewTree, mergedDiffViewTree, "Differences");
		ViewTreeMerger.append(viewTree, newTree, "Current Version");
		ViewTreeMerger.append(viewTree, oldTree, "Previous Version");

		return viewTree;
	}

	protected Parameter getParameter(Picto picto, String parameterName) {
		try {
			return picto.getParameters().stream()
					.filter(p -> p.getName().equals(parameterName)).findFirst().get();
		}
		catch (NoSuchElementException e) {
			return null;
		}
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
