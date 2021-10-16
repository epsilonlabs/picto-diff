package org.eclipse.epsilon.picto.diff.engines.sidebyside;

import java.util.Collections;

import org.eclipse.epsilon.picto.PictoView;
import org.eclipse.epsilon.picto.ViewContent;
import org.eclipse.epsilon.picto.ViewRenderer;

public class SideBySideViewContent extends ViewContent {

	protected String[] templateParts;
	protected String leftText;
	protected String rightText;
	protected String sourceText;

	public SideBySideViewContent(String[] templateParts, String leftText, String rightText) {
		super("html", null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptySet());

		this.templateParts = templateParts;
		this.leftText = leftText;
		this.rightText = rightText;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	@Override
	public String getText() {
		if (text == null) {
			text = new StringBuilder()
					.append(templateParts[SideBySideDiffEngine.TEMPLATE_TOP])
					.append(leftText)
					.append(templateParts[SideBySideDiffEngine.TEMPLATE_MIDDLE])
					.append(rightText)
					.append(templateParts[SideBySideDiffEngine.TEMPLATE_BOTTOM])
					.toString();
		}
		return text;
	}

	/**
	 * Preformat iframe contents to show source text
	 */
	public String getSourceText(ViewRenderer renderer) {
		if (sourceText == null) {
			sourceText = new StringBuilder()
					.append(templateParts[SideBySideDiffEngine.TEMPLATE_TOP])
					.append(renderer.getVerbatim(leftText))
					.append(templateParts[SideBySideDiffEngine.TEMPLATE_MIDDLE])
					.append(renderer.getVerbatim(rightText))
					.append(templateParts[SideBySideDiffEngine.TEMPLATE_BOTTOM])
					.toString();
		}
		return sourceText;
	}

	@Override
	public ViewContent getSourceContent(PictoView pictoView) {
		return new ViewContent("html", getSourceText(pictoView.getViewRenderer()), file, layers, patches, baseUris);
	}
}
