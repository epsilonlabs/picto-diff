package org.eclipse.epsilon.picto.diff;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
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
		
		public static String getFileContents(String pluginFile) throws IOException {
			String fileContents;
			if (plugin == null) {
				fileContents = new String(Files.readAllBytes(Paths.get(pluginFile)));
			}
			else {
				InputStream stream = FileLocator.openStream(
						plugin.getBundle(),	new Path(pluginFile), false);
				fileContents = IOUtils.toString(stream, StandardCharsets.UTF_8);
				stream.close();
			}
			return fileContents;
		}
}
