package main.java.testsmell.plugin;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import main.java.testsmell.TestSmellDetector;

/**
 * The activator class that controls the plug-in life cycle.
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "TestSmellsDetector";

	// The shared instance
	private static Activator plugin;
	
	/**
	 * Empty constructor.
	 */
	public Activator() {
	}

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		initializeSmellDefaults();
	}

	/**
	 * Every smell is reported by default. Without this, an unset preference
	 * reads as false and the detector would report nothing on a fresh install.
	 */
	private void initializeSmellDefaults() {
		IPreferenceStore store = getPreferenceStore();
		for (String smellName : TestSmellDetector.createTestSmellDetector().getTestSmellNames()) {
			store.setDefault(smellKey(smellName), true);
		}
	}

	/**
	 * Preference key for a given smell name.
	 *
	 * @param smellName the smell's display name
	 * @return the key used in the preference store
	 */
	public static String smellKey(String smellName) {
		return "smell." + smellName;
	}

	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance.
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path.
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
}
