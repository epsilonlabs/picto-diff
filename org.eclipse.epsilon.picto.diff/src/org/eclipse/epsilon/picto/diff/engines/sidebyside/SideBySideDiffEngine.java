package org.eclipse.epsilon.picto.diff.engines.sidebyside;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.epsilon.picto.PictoView;
import org.eclipse.epsilon.picto.ViewContent;
import org.eclipse.epsilon.picto.ViewRenderer;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;

public class SideBySideDiffEngine implements DiffEngine {

	private static final String HEADER_FILE = "transformations/sideBySideDiffHeader.html";
	private String headerText;

	@Override
	public boolean supports(String format) {
		return true;
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {

		loadHeaderText();

		PictoView pictoView = new PictoView();
		pictoView.setViewRenderer(new ViewRenderer(pictoView, null));
		Iterator<ViewContent> leftViewContents = left.getContents(pictoView).iterator();
		Iterator<ViewContent> rightViewContents = right.getContents(pictoView).iterator();

		ViewContent diffContent = null;
		ViewContent currentContent = null;
		while (leftViewContents.hasNext() && rightViewContents.hasNext()) {
			ViewContent leftContent = leftViewContents.next();
			ViewContent rightContent = rightViewContents.next();

			SideBySideViewContent newContent = new SideBySideViewContent(
					headerText, leftContent.getText(), rightContent.getText());
			newContent.setLabel(leftContent.getLabel());
			if (diffContent == null) {
				diffContent = newContent;
			}
			if (currentContent != null) {
				currentContent.setNext(newContent);
			}
			currentContent = newContent;
		}
		diffView.setFormat("html");
		diffView.setContent(diffContent);
	}

	// the generated html makes use of "text/template" scripts to populate
	//   the iframes that present the contents side by side. These kind of
	//   scripts are ignored by the browser, and their text can later be
	//   recovered from javascript. See the HEADER_FILE document for how
	//   that is done.

	// Working in Eclipse browser, chromium, firefox and microsoft edge

	// Two examples, one using these templates, and another one using
	//   iframes that load external html files, can be found in
	//   files/sideBySideDiff-{fileBasedIframes, templateContentIframes}.html

	private void loadHeaderText() throws IOException {
		if (PictoDiffPlugin.getDefault() == null) {
			// Standalone java
			headerText = new String(Files.readAllBytes(Paths.get(HEADER_FILE)));
		}
		else {
			// Eclipse plugin (works, but there is probably an easier way to do this?)
			headerText = new String(Files.readAllBytes(Paths.get(FileLocator.resolve(
					PictoDiffPlugin.getDefault().getBundle().getEntry(HEADER_FILE)).getPath())));
		}
	}

}
