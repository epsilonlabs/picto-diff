package org.eclipse.epsilon.picto.diff.engines.dot;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.eclipse.epsilon.picto.diff.engines.dot.util.GraphPromiseGenerator;

import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;

public class DotDiffContext {

	protected InputStream ss;
	protected InputStream ts;
	protected String sourceFile;
	protected String targetFile;
	protected MutableGraph sourceGraph;
	protected MutableGraph targetGraph;
	protected GraphPromiseGenerator source_pg;
	protected GraphPromiseGenerator target_pg;
	
	protected String serialise_image = null;
	protected String serialise_dot = null;
	
	public DotDiffContext(String sourceFile, String targetFile) {
		this.sourceFile = sourceFile;
		this.targetFile = targetFile;
	}
	
	public boolean loadGraphs() throws IOException {
 		ss = new FileInputStream(sourceFile);
		sourceGraph = new Parser().read(ss);
		ts = new FileInputStream(targetFile);
		targetGraph = new Parser().read(ts);

		source_pg = new GraphPromiseGenerator(sourceGraph.copy());
		target_pg = new GraphPromiseGenerator(targetGraph.copy());
		
		ss.close();
		ts.close();
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
		return source_pg.getGraphPromise();
	}
	
	public HashMap<String, String> getSourcePromiseMap() {
		return source_pg.getPromiseMap();
	}
	
	public String getTargetGraphPromise() {
		return target_pg.getGraphPromise();
	}
	
	public HashMap<String, String> getTargetPromiseMap() {
		return target_pg.getPromiseMap();
	}
	
}
