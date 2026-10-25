package main.java.testsmell.plugin.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import main.java.testsmell.TestSmellDetector;
import main.java.testsmell.plugin.Activator;

/**
 * Preference page listing every test smell the detector knows about, with a
 * checkbox controlling whether it is reported.
 *
 * Detection itself always runs all analyzers; disabling a smell filters it out
 * of the reported results. The engine provides no way to skip an analyzer
 * without modifying it, and engine code is out of scope for this plugin.
 */
public class TSmellPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public TSmellPreferencePage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Select which test smells the detector should report.");
	}

	@Override
	protected void createFieldEditors() {
		for (String smellName : TestSmellDetector.createTestSmellDetector().getTestSmellNames()) {
			addField(new BooleanFieldEditor(Activator.smellKey(smellName), smellName, getFieldEditorParent()));
		}
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing to initialize
	}
}
