package org.eclipse.epsilon.picto.diff.source;

import org.eclipse.core.resources.IFile;
import org.eclipse.epsilon.picto.diff.FileWrapperEditorPart;
import org.eclipse.epsilon.picto.source.FlexmiSource;
import org.eclipse.ui.IEditorPart;

/**
 * A Flexmi picto source adapted for picto diff
 */
public class FlexmiDiffSource extends FlexmiSource {

	@Override
	public boolean supports(IEditorPart editorPart) {
		if (editorPart instanceof FileWrapperEditorPart) {
			return "flexmi".equalsIgnoreCase(
					((FileWrapperEditorPart) editorPart).getFile().getFileExtension());
		}
		return false;
	}

	@Override
	public IFile getFile(IEditorPart editorPart) {
		return ((FileWrapperEditorPart) editorPart).getFile();
	}
}
