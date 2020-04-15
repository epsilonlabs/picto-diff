package org.eclipse.epsilon.picto.diff.engines.dummy;

import org.eclipse.epsilon.picto.StaticContentPromise;
import org.eclipse.epsilon.picto.ViewTree;
import org.eclipse.epsilon.picto.diff.engines.DiffEngine;

public class DummyDiffEngine implements DiffEngine {

	@Override
	public boolean supports(String format) {
		return true;
	}

	@Override
	public void diff(ViewTree diffView, ViewTree left, ViewTree right) throws Exception {
		diffView.setPromise(new StaticContentPromise(String.format("%s: Content differs", diffView.getName())));
		diffView.setFormat("text");
	}

}
