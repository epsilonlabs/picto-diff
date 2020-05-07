package org.eclipse.epsilon.picto.diff.engines.dot;

import org.eclipse.epsilon.picto.ContentPromise;

public class DotDiffContentPromise implements ContentPromise {

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
			content = engine.getDotString();
		}
		return content;
	}

}
