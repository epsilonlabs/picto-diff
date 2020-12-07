package org.eclipse.epsilon.picto.diff.source;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.FileWrapperEditorPart;
import org.eclipse.epsilon.picto.dom.Picto;
import org.eclipse.epsilon.picto.source.EglPictoSource;
import org.eclipse.ui.IEditorPart;

public class MockExternalMetadataSource extends EglPictoSource {

	protected IResource iResource;
	protected Resource resource;
	protected Picto metadata;

	public MockExternalMetadataSource(IResource iResource, Resource resource, Picto metadata) {
		this.iResource = iResource;
		this.resource = resource;
		this.metadata = metadata;
	}

	@Override
	protected Resource getResource(IEditorPart editorPart) {
		return resource;
	}

	@Override
	protected IFile getFile(IEditorPart editorPart) {
		return (IFile) iResource;
	}

	@Override
	public Picto getRenderingMetadata(IEditorPart editorPart) {
		return metadata;
	}

	@Override
	protected boolean supportsEditorType(IEditorPart editorPart) {
		return true;
	}

	@Override
	public void showElement(String id, String uri, IEditorPart editor) {
	}

	public ViewTree getViewTree() throws Exception {
		return getViewTree(new FileWrapperEditorPart((IFile) iResource));
	}
}
