package org.eclipse.epsilon.picto.diff.engines.csv;

import org.eclipse.epsilon.picto.diff.engines.DiffEngine;
import org.eclipse.epsilon.picto.diff.engines.DiffEngineFactory;

public class CsvDiffEngineFactory implements DiffEngineFactory {

	@Override
	public DiffEngine createDiffEngine() {
		return new CsvDiffEngine();
	}

}
