package org.eclipse.epsilon.picto.diff.engines.sidebyside;

import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;

public class SideBySideDiffEngineFactory implements DiffEngineFactory {

	@Override
	public DiffEngine createDiffEngine() {
		return new SideBySideDiffEngine();
	}

}
