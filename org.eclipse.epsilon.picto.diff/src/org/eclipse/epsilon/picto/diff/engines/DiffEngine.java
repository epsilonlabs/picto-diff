package org.eclipse.epsilon.picto.diff.engines;

import org.eclipse.epsilon.picto.ViewTree;

public interface DiffEngine {

	public boolean supports(String format);

	public void diff(ViewTree diffView, ViewTree oldView, ViewTree newView) throws Exception;
}
