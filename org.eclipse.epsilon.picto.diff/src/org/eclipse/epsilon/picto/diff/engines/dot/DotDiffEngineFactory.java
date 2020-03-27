package org.eclipse.epsilon.picto.diff.engines.dot;

import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;

public class DotDiffEngineFactory implements DiffEngineFactory {

	@Override
	public DiffEngine createDiffEngine() {
		return new DotDiffEngine();
	}

}
