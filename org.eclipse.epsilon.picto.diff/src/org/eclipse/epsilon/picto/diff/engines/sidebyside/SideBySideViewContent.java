package org.eclipse.epsilon.picto.diff.engines.sidebyside;

import java.util.Collections;

import org.eclipse.epsilon.picto.PictoView;
import org.eclipse.epsilon.picto.ViewContent;
import org.eclipse.epsilon.picto.ViewRenderer;

public class SideBySideViewContent extends ViewContent {

	private String headerText;
	private String leftText;
	private String rightText;

	public SideBySideViewContent(String headerText, String leftText, String rightText) {
		super("html", "", null, Collections.emptyList(), Collections.emptyList(), Collections.emptySet());
		this.headerText = headerText;
		this.leftText = leftText;
		this.rightText = rightText;
		this.text = initialiseText();
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String initialiseText() {
		String leftFormatted = String.format(
				"<script id=\"leftIframeContent\" type=\"text/template\">\n%s</script>",
				leftText);
		String rightFormatted = String.format(
				"<script id=\"rightIframeContent\" type=\"text/template\">\n%s</script>",
				rightText);
		return String.format("<html>%s%s%s</html>", headerText, leftFormatted, rightFormatted);
	}

	/**
	 * Preformat iframe contents to show source text
	 */
	public String getSourceText(ViewRenderer renderer) {
		String leftFormatted = String.format(
				"<script id=\"leftIframeContent\" type=\"text/template\">\n%s</script>",
				renderer.getVerbatim(leftText));
		String rightFormatted = String.format(
				"<script id=\"rightIframeContent\" type=\"text/template\">\n%s</script>",
				renderer.getVerbatim(rightText));
		return String.format("<html>%s%s%s</html>", headerText, leftFormatted, rightFormatted);
	}

	@Override
	public ViewContent getSourceContent(PictoView pictoView) {
		return new ViewContent("html", getSourceText(pictoView.getViewRenderer()), file, layers, patches, baseUris);
	}
}
