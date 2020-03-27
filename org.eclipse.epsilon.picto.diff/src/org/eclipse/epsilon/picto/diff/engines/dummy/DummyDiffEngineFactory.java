package org.eclipse.epsilon.picto.diff.engines.dummy;

import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;

public class DummyDiffEngineFactory implements DiffEngineFactory {

	@Override
	public DiffEngine createDiffEngine() {
		return new DummyDiffEngine();
	}

}
