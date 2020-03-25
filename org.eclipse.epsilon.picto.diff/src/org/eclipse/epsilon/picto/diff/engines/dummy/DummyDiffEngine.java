package org.eclipse.epsilon.picto.diff.engines.dummy;

import org.eclipse.epsilon.picto.StringContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;

public class DummyDiffEngine implements DiffEngine {

	@Override
	public boolean supports(String format) {
		return true;
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {
		diffView.setPromise(new StringContentPromise(String.format("%s: Content differs", diffView.getName())));
		diffView.setFormat("text");
	}

}
