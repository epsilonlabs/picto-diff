package org.eclipse.epsilon.picto.diff.engines.dot;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.eclipse.epsilon.picto.diff.engines.dot.util.GraphPromiseGenerator;
import org.eclipse.epsilon.picto.diff.engines.dot.util.SubGraphPromise;

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
	protected GraphPromiseGenerator source_pg;
	protected GraphPromiseGenerator target_pg;
	
	protected String serialise_image = null;
	protected String serialise_dot = null;
	
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

		source_pg = new GraphPromiseGenerator(sourceGraph.copy());
		target_pg = new GraphPromiseGenerator(targetGraph.copy());

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
	
	public void setSerialiseOptions(String img_destination, String dot_destination) {
		serialise_image = img_destination;
		serialise_dot = dot_destination;
	}
	
	public String getSerialise_image() {
		return serialise_image;
	}
	
	public String getSerialise_dot() {
		return serialise_dot;
	}
	
	public String getSourceGraphPromise() {
		return source_pg.getDotGraph();
	}
	
	public HashMap<String, SubGraphPromise> getSourcePromiseMap() {
		return source_pg.getPromiseMap();
	}
	
	public String getTargetGraphPromise() {
		return target_pg.getDotGraph();
	}
	
	public HashMap<String, SubGraphPromise> getTargetPromiseMap() {
		return target_pg.getPromiseMap();
	}
	
}
