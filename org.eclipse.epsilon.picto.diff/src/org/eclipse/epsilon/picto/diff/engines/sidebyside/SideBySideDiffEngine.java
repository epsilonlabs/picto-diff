package org.eclipse.epsilon.picto.diff.engines.sidebyside;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewContent;
import org.eclipse.epsilon.picto.ViewRenderer;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.PictoDiffPlugin;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;

public class SideBySideDiffEngine implements DiffEngine {

	@Override
	public boolean supports(String format) {
		return true;
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {

		ViewContent leftViewContent = left.getContent().getFinal(new ViewRenderer(null));
		ViewContent rightViewContent = right.getContent().getFinal(new ViewRenderer(null));

		// the generated html makes use of "text/template" scripts to populate
		//   the iframes that present the contents side by side. These kind of
		//   scripts are ignored by the browser, and their text can later be
		//   recovered from javascript. See the "headerFile" document for how
		//   that is done.

		// Tested in Eclipse browser, chromium, firefox and microsoft edge

		// Two examples, one using these templates, and another one using
		//   iframes that load external html files, can be found in
		//   files/sideBySideDiff-{fileBasedIframes, templateContentIframes}.html

		String headerFile = "transformations/sideBySideDiffHeader.html";
		if (PictoDiffPlugin.getDefault() == null) {
			// Standalone java
			headerFile = new String(Files.readAllBytes(Paths.get(headerFile)));
		}
		else {
			// Eclipse plugin (works, but there is probably an easier way to do this?)
			headerFile = new String(Files.readAllBytes(Paths.get(
					FileLocator.resolve(PictoDiffPlugin.getDefault().getBundle().getEntry(headerFile)).getPath())));
		}

		String leftText = String.format(
				"<script id=\"leftIframeContent\" type=\"text/template\">\n%s</script>",
				leftViewContent.getText());
		String rightText = String.format(
				"<script id=\"rightIframeContent\" type=\"text/template\">\n%s</script>",
				rightViewContent.getText());
		
		String sideBySideHHtml =
				String.format("<html>%s%s%s</html>", headerFile, leftText, rightText);

		diffView.setFormat("html");
		diffView.setPromise(new StringContentPromise(sideBySideHHtml));
	}

}
