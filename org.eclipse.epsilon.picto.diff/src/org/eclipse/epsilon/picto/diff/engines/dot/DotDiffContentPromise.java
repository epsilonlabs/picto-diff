package org.eclipse.epsilon.picto.diff.engines.dot;

import java.text.MessageFormat;

import org.eclipse.epsilon.picto.ContentPromise;

import guru.nidi.graphviz.engine.Format;

public class DotDiffContentPromise implements ContentPromise {

	/**
	 * Template-like html page.
	 *
	 * <p>Argument {0}: SVG string of the new version graph</p>
	 * <p>Argument {1}: SVG string of the old version graph</p>
	 * <p>Argument {2}: SVG events included in the webpage</p>
	 */
	private static final String HTML_PAGE_FORMAT = "<html><body>{0}{1}{2}</body></html>";
	
	/**
	 * Graph container.
	 *
	 * <p>Argument {0}: name of the graph</p>
	 * <p>Argument {1}: SVG string of the graph</p>
	 */
	private static final String GRAPH_FORMAT =
			"<div style=\"vertical-align: top; display: inline-block; border: dashed 2px; margin: 5px; padding: 5px;\">"+
			"<p style=\"text-align: center; margin: 5px auto;\">{0}</p>{1}</div>";
	
	protected String oldVersionGraph;
	protected String newVersionGraph;
	protected String content;
	protected DotDiffEngine engine;

	public DotDiffContentPromise(DotDiffEngine engine) {
		this.engine = engine;
	}

	@Override
	public String getContent() throws Exception {
		if (content == null) {
			engine.load();
			engine.compare();

			newVersionGraph = getFormattedGraph("New Version",
					engine.getNewVersion(Format.SVG), engine.newVersionEmpty());

			oldVersionGraph = getFormattedGraph("Old Version",
					engine.getOldVersion(Format.SVG), engine.oldVersionEmpty());

			content = MessageFormat.format(HTML_PAGE_FORMAT,
					newVersionGraph, oldVersionGraph, DotDiffEngine.getSvgEvents());
		}
		return content;
	}

	private String getFormattedGraph(String name, String graph, boolean emptyGraph) {
		if (emptyGraph) {
			return "";
		}
		return MessageFormat.format(GRAPH_FORMAT, name, graph);
	}

	public String getFormat() {
		return "html";
	}

}
