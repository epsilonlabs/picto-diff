package org.eclipse.epsilon.picto.diff.source;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.compare.structuremergeviewer.ICompareInputChangeListener;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.FileWrapperEditorPart;
import org.eclipse.epsilon.picto.diff.FileWrapper;
import org.eclipse.epsilon.picto.diff.ViewTreeMerger;
import org.eclipse.epsilon.picto.dom.Picto;
import org.eclipse.epsilon.picto.source.EditingDomainProviderSource;
import org.eclipse.epsilon.picto.source.PictoSource;
import org.eclipse.epsilon.picto.source.PictoSourceExtensionPointManager;
import org.eclipse.epsilon.picto.transformers.ExternalContentTransformation;
import org.eclipse.ui.IEditorPart;

public class CompareEditorSource implements PictoSource {

	private static Object LOCK = new Object();
	private static long COMPARE_TIMEOUT = 1000;

	protected ICompareInputChangeListener changeListener = new ICompareInputChangeListener() {
		@Override
		public void compareInputChanged(ICompareInput source) {
			synchronized (LOCK) {
				LOCK.notify();
			}
		}
	};

	protected ICompareInput currentInput = null;

	@Override
	public boolean supports(IEditorPart editorPart) {
		return editorPart.getEditorInput() instanceof CompareEditorInput;
	}

	@Override
	public ViewTree getViewTree(IEditorPart editorPart) throws Exception {

		Object result = waitForCompareResult(editorPart);
		if (!(result instanceof ICompareInput)) {
			return createMessageViewTree("No ICompareInput to create the visualisation found");
		}
		
		// the compare result object is reused between editions of the same comparison,
		//   so we need to wait until we are notified of a compare input change
		// the COMPARE_TIMEOUT timeout is there in case the user refreshes the picto view,
		//   as I have not found a better way to support that case
		if (result == currentInput) {
			synchronized (LOCK) {
				LOCK.wait(COMPARE_TIMEOUT);
			}
		}
		else {
			if (currentInput != null) {
				currentInput.removeCompareInputChangeListener(changeListener);
			}
			currentInput = (ICompareInput) result;
			currentInput.addCompareInputChangeListener(changeListener);
		}

		if (!(currentInput.getLeft() instanceof IStreamContentAccessor) ||
				!(currentInput.getRight() instanceof IStreamContentAccessor)) {
			return createMessageViewTree("No contents could be gathered from the compared versions");
		}

		if (currentInput.getLeft() instanceof ResourceNode) {
			ResourceNode leftNode = (ResourceNode) currentInput.getLeft();
			Picto externalMetadata = getPictoExternalMetadata(leftNode);

			if (externalMetadata != null) {
				return mergeViewTrees(
						getViewTree(leftNode, externalMetadata),
						getViewTree(currentInput.getRight(), externalMetadata));
			}
		}

		return mergeViewTrees(getViewTree(currentInput.getLeft()), getViewTree(currentInput.getRight()));
	}

	protected ViewTree mergeViewTrees(ViewTree newTree, ViewTree oldTree) throws Exception {

		ViewTree mergedDiffViewTree = ViewTreeMerger.diffMerge(oldTree, newTree);

		ViewTree result = new ViewTree();
		// set here base uri to find pictodiff icons
		result.getBaseUris().add(new java.net.URI("platform:/plugin/org.eclipse.epsilon.picto.diff/icons/"));
		ViewTreeMerger.append(result, mergedDiffViewTree, "Differences");
		ViewTreeMerger.append(result, newTree, "Current Version");
		ViewTreeMerger.append(result, oldTree, "Previous Version");

		return result;
	}

	/**
	 * Waits until a compare result is available
	 */
	protected Object waitForCompareResult(IEditorPart editorPart) {
		int attempts = 0;
		int maxAttempts = 50;
		CompareEditorInput input =  (CompareEditorInput) editorPart.getEditorInput();
		Object result = input.getCompareResult();
		while (result == null && attempts < maxAttempts) {
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
			}
			result = input.getCompareResult();
			attempts++;
		}
		return result;
	}

	protected ViewTree createMessageViewTree(String message) {
		ViewTree viewTree = new ViewTree();
		viewTree.setPromise(new StaticContentPromise(message));
		viewTree.setFormat("text");
		return viewTree;
	}

	protected Picto getPictoExternalMetadata(ResourceNode node) {
		IContainer parent = node.getResource().getParent();
		IPath pictoPath = Path.fromPortableString(node.getName() + ".picto");
		// if there is an external picto file
		if (parent != null && parent.exists(pictoPath)) {
			Picto metadata = new EditingDomainProviderSource().getRenderingMetadata(parent.getFile(pictoPath));
			if (metadata != null && !metadata.isStandalone()) {
				return metadata;
			}
		}
		return null;
	}

	/**
	 * Get viewtree based on element contents and extension
	 */
	protected ViewTree getViewTree(ITypedElement elem) throws Exception {
		ByteArrayOutputStream stream = getStreamContents(
				((IStreamContentAccessor) elem).getContents());

		File file = ExternalContentTransformation.createTempFile(elem.getName(), stream.toByteArray()).toFile();
		FileWrapperEditorPart editor = new FileWrapperEditorPart(new FileWrapper(file));

		PictoSource source = getPictoSource(editor);
		if (source != null) {
			return source.getViewTree(editor);
		}

		return new ViewTree();
	}

	protected ByteArrayOutputStream getStreamContents(InputStream contents) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while ((length = contents.read(buffer)) != -1) {
			result.write(buffer, 0, length);
		}
		return result;
	}

	/**
	 * Get Viewtree based on the provided external metadata
	 */
	protected ViewTree getViewTree(ITypedElement elem, Picto externalMetadata) throws Exception {
		ResourceSet resourceSet = new ResourceSetImpl();

		if (elem instanceof ResourceNode) {
			ResourceNode node = (ResourceNode) elem;

			Resource resource = resourceSet.getResource(
					URI.createFileURI(node.getResource().getLocation().toOSString()), true);
			resource.load(null);

			return new MockExternalMetadataSource(
					node.getResource(), resource, externalMetadata).getViewTree();
		}

		// else: use a temp file
		File tempFile =
				ExternalContentTransformation.createTempFile(
						elem.getName(),
						getStreamContents(((IStreamContentAccessor) elem).getContents()).toByteArray())
				.toFile();

		Resource resource = resourceSet.getResource(
				URI.createFileURI(tempFile.getAbsolutePath()), true);
		resource.load(null);

		return new MockExternalMetadataSource(
				new FileWrapper(tempFile), resource, externalMetadata).getViewTree();
	}

	protected PictoSource getPictoSource(IEditorPart editorPart) {
		List<PictoSource> sources =
				new PictoSourceExtensionPointManager().getExtensions();
		for (PictoSource source : sources) {
			if (source.supports(editorPart)) {
				return source;
			}
		}
		return null;
	}

	@Override
	public void showElement(String id, String uri, IEditorPart editor) {
	}

	@Override
	public void dispose() {
	}
}
