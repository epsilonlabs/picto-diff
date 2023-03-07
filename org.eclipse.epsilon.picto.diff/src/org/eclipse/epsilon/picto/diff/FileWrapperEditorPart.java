package org.eclipse.epsilon.picto.diff;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.FileInPlaceEditorInput;

public class FileWrapperEditorPart implements IEditorPart {

	private IFile file;

	public FileWrapperEditorPart(IFile file) {
		this.file = file;
	}

	@Override
	public IEditorInput getEditorInput() {
		return new FileInPlaceEditorInput(file);
	}
	
	public IFile getFile() {
		return file;
	}

	@Override
	public String getTitle() {
		return file != null ? file.getName() : null;
	}

	@Override
	public void addPropertyListener(IPropertyListener listener) {
		// TODO Auto-generated method stub
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public IWorkbenchPartSite getSite() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Image getTitleImage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getTitleToolTip() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void removePropertyListener(IPropertyListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		// TODO Auto-generated method stub
	}

	@Override
	public void doSaveAs() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isDirty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSaveAsAllowed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSaveOnCloseNeeded() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public IEditorSite getEditorSite() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		// TODO Auto-generated method stub
	}

}
