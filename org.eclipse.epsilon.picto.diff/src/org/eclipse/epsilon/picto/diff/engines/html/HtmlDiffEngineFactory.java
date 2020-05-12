package org.eclipse.epsilon.picto.diff.engines.html;

import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;

public class HtmlDiffEngineFactory implements DiffEngineFactory {

	@Override
	public DiffEngine createDiffEngine() {
		return new HtmlDiffEngine();
	}

}
