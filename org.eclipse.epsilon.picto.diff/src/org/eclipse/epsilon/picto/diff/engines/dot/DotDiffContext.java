package org.eclipse.epsilon.picto.diff.engines.dot;

import java.io.IOException;
import java.io.InputStream;

import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;

public class DotDiffContext {

	protected InputStream ss;
	protected InputStream ts;
	boolean fromStream = false;

	protected String sourceDot;
	protected String targetDot;
	protected MutableGraph sourceGraph;
	protected MutableGraph targetGraph;
	
	public DotDiffContext(String sourceDot, String targetDot) {
		this.sourceDot = sourceDot;
		this.targetDot = targetDot;
	}

	public DotDiffContext(InputStream sourceStream, InputStream targetStream) {
		this.ss = sourceStream;
		this.ts = targetStream;
		fromStream = true;
	}
	
	public boolean loadGraphs() throws IOException {
		if (fromStream) {
			sourceGraph = new Parser().read(ss);
			targetGraph = new Parser().read(ts);
			ss.close();
			ts.close();
		}
		else {
			sourceGraph = new Parser().read(sourceDot);
			targetGraph = new Parser().read(targetDot);
		}
		return true;
	}
	
	public MutableGraph getSourceGraph() {
		return sourceGraph;
	}
	
	public MutableGraph getTargetGraph() {
		return targetGraph;
	}
	
	public void clean() {
		sourceGraph = null;
		targetGraph = null;
	}
}
