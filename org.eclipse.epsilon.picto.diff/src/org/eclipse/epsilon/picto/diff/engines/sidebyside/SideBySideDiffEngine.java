package org.eclipse.epsilon.picto.diff.engines.sidebyside;

import java.io.IOException;
import java.util.Iterator;

import org.eclipse.epsilon.picto.PictoView;
import org.eclipse.epsilon.picto.ViewContent;
import org.eclipse.epsilon.picto.ViewRenderer;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;

public class SideBySideDiffEngine implements DiffEngine {

	public static final int TEMPLATE_TOP = 0;
	public static final int TEMPLATE_MIDDLE = 1;
	public static final int TEMPLATE_BOTTOM = 2;

	private static final String TEMPLATE_FILE = "transformations/sideBySideDiffTemplate.html";
	private static final String TEMPLATE_DELIMITER = "@@@";

	private static String[] templateParts;

	@Override
	public boolean supports(String format) {
		return true;
	}

	@Override
	public void diff(ViewTree diffView, ViewTree oldView, ViewTree newView) throws Exception {

		loadTemplateParts();

		PictoView pictoView = new PictoView();
		pictoView.setViewRenderer(new ViewRenderer(null));
		Iterator<ViewContent> oldViewContents = oldView.getContents(pictoView).iterator();
		Iterator<ViewContent> newViewContents = newView.getContents(pictoView).iterator();

		ViewContent diffContent = null;
		ViewContent currentContent = null;
		while (oldViewContents.hasNext() && newViewContents.hasNext()) {
			ViewContent oldContent = oldViewContents.next();
			ViewContent newContent = newViewContents.next();

			// new view on the left, old view on the right (as EGit does)
			SideBySideViewContent combinedContent = new SideBySideViewContent(
					templateParts, newContent.getText(), oldContent.getText());
			combinedContent.setLabel(oldContent.getLabel());
			if (diffContent == null) {
				diffContent = combinedContent;
			}
			if (currentContent != null) {
				currentContent.setNext(combinedContent);
			}
			currentContent = combinedContent;
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

	private void loadTemplateParts() throws IOException {
		if (templateParts == null) {
			templateParts = PictoDiffPlugin.getFileContents(TEMPLATE_FILE).split(TEMPLATE_DELIMITER);
		}
	}

}
