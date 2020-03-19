package org.eclipse.epsilon.picto.diff;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class PictoDiffPlugin extends AbstractUIPlugin {

	// The plug-in ID
		public static final String PLUGIN_ID = "org.eclipse.epsilon.picto.diff";

		// The shared instance
		private static PictoDiffPlugin plugin;
		
		/**
		 * The constructor
		 */
		public PictoDiffPlugin() {
		}

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
		 */
		@Override
		public void start(BundleContext context) throws Exception {
			super.start(context);
			plugin = this;
		}

		/*
		 * (non-Javadoc)
		 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
		 */
		@Override
		public void stop(BundleContext context) throws Exception {
			plugin = null;
			super.stop(context);
		}

		/**
		 * Returns the shared instance
		 *
		 * @return the shared instance
		 */
		public static PictoDiffPlugin getDefault() {
			return plugin;
		}
		
		public ImageDescriptor getImageDescriptor(String path) {
			return imageDescriptorFromPlugin(PLUGIN_ID, path);
		}
}
