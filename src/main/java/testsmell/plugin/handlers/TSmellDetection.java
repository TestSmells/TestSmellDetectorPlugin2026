package main.java.testsmell.plugin.handlers;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;

import main.java.testsmell.AbstractSmell;
import main.java.testsmell.TestFile;
import main.java.testsmell.TestSmellDetector;
import main.java.testsmell.plugin.Activator;
import main.java.testsmell.SmellyElement;

/**
 * Detection class to encapsulate test smell detection objects.
 */
public class TSmellDetection {
	private SimpleEntry<String, String> paths;
	private List<AbstractSmell> smells = new ArrayList<AbstractSmell>();
	private TestFile testFile;
	
	/**
	 * Constructor that takes in a test file to create the test smell detection object.
	 * @param tf
	 */
	protected TSmellDetection(TestFile tf) {
		testFile = tf;
		new ArrayList<String>();
	}
	
	/**
	 * Creates an instance of the test smell detector and runs the detect smells on a list of test files.
	 */
	public void detectSmells() {
		TestSmellDetector testSmellDetector = TestSmellDetector.createTestSmellDetector();
		TestFile tempFile;
		try {
			tempFile = testSmellDetector.detectSmells(testFile);
			paths = new SimpleEntry<String, String>(testFile.getProductionFilePath(),testFile.getTestFilePath());
			for (AbstractSmell smell : tempFile.getTestSmells()) {
				try {
					if (smell.getHasSmell() && isSmellEnabled(smell.getSmellName())) {
						smells.add(smell);
					}
				} catch (NullPointerException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Gets production file paths.
	 * Each element is padded to match size of the smells array to keep table columns the same size.
	 * 
	 * @return
	 */
	public String[] getProdFilePath() {
		String[] tempProdFilePaths = new String[smells.size()];
		String prodFile = null;
		if (paths != null)
			if (paths.getKey() != null)
				prodFile = paths.getKey();
			else return null;
		else return null;
		for (int i = 0; i < tempProdFilePaths.length; i++) {
			tempProdFilePaths[i] =  prodFile;
		}
		return tempProdFilePaths;
	}
	
	/**
	 * Gets test file paths.
	 * Each element is padded to match size of the smells array to keep table columns the same size.
	 * 
	 * @return
	 */
	public String[] getTestFilePath() {
		String[] tempTestFilePaths = new String[smells.size()];
		String tempTestFile = null;
		if (paths != null )
			if (paths.getKey() != null)
				tempTestFile = paths.getValue();
			else return null;
		else return null;
		for (int i = 0; i < tempTestFilePaths.length; i++) {
			tempTestFilePaths[i] = tempTestFile;
		}
		return tempTestFilePaths;
	}
	
	/**
	 * Gets test smell names. 
	 * Each element is padded to match size of the smells array to keep table columns the same size.
	 * 
	 * @return
	 */
	public String[] getSmellNames() {
		String[] tempSmellNames = new String[smells.size()];
		for (int i = 0; i < smells.size(); i++) {
			tempSmellNames[i] = smells.get(i).getSmellName();
		}
		return tempSmellNames;
	}

	/**
	 * Gets the names of the methods (or classes) carrying each smell.
	 * Padded to match the size of the smells array so table columns stay aligned.
	 *
	 * @return comma-separated element names, one entry per detected smell
	 */
	public String[] getMethodNames() {
		String[] tempMethodNames = new String[smells.size()];
		for (int i = 0; i < smells.size(); i++) {
			List<String> names = new ArrayList<String>();
			for (SmellyElement element : smells.get(i).getSmellyElements()) {
				if (element.getHasSmell()) {
					names.add(element.getElementName());
				}
			}
			tempMethodNames[i] = String.join(", ", names);
		}
		return tempMethodNames;
	}

	/**
	 * Whether the given smell is enabled in the plugin preferences.
	 * Defaults to true if preferences are unavailable.
	 *
	 * @param smellName the smell's display name
	 * @return true if the smell should be reported
	 */
	private boolean isSmellEnabled(String smellName) {
		Activator activator = Activator.getDefault();
		if (activator == null) {
			return true;
		}
		return activator.getPreferenceStore().getBoolean(Activator.smellKey(smellName));
	}
}
