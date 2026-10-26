package main.java.testsmell.plugin.views;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import main.java.testsmell.plugin.handlers.TSmellDetection;
import main.java.testsmell.plugin.handlers.TSmellResults;

/**
 * Table viewer class that launches a table viewer with a grid that displays the
 * results for the test smells. Displays all the production and test file paths
 * and smells detected for each.
 */
public class TSmellTableViewer {

	/**
	 * Constructor for the table viewer.
	 * 
	 * @param parent
	 * @throws FileNotFoundException
	 */
	public TSmellTableViewer(Composite parent) throws FileNotFoundException {

		this.addChildControls(parent);
	}

	private static Table table = null;
	private TableViewer tableViewer;
	private Button closeButton;

	// Set the table column property names
	private final String FILES_COLUMN = "files";
	private final String TEST_SMELLS_COLUMN = "test smells";
	private final String DETECTED_COLUMN = "detected";
	private final String DESCRIPTION_COLUMN = "description";

	// Set column names
	private String[] columnNames = new String[] { FILES_COLUMN, TEST_SMELLS_COLUMN, DETECTED_COLUMN,
			DESCRIPTION_COLUMN };

	/**
	 * Main method to launch the window.
	 * 
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws FileNotFoundException {

		Shell shell = new Shell();
		shell.setText("Test Smell Detector");

		// Set layout for shell
		GridLayout layout = new GridLayout();
		shell.setLayout(layout);

		// Create a composite to hold the children
		Composite composite = new Composite(shell, SWT.NONE);

		final TSmellTableViewer tableViewer = new TSmellTableViewer(composite);

		tableViewer.getControl().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				tableViewer.dispose();
			}
		});

		shell.open();
		tableViewer.run(shell);
	}

	/**
	 * Runs and waits for a close event.
	 * 
	 * @param shell
	 */
	private void run(Shell shell) {

		// Add a listener for the close button
		closeButton.addSelectionListener(new SelectionAdapter() {

			// Close the view i.e. dispose of the composite's parent
			public void widgetSelected(SelectionEvent e) {
				table.getParent().getParent().dispose();
			}
		});

		Display display = shell.getDisplay();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
	}

	/**
	 * Creates the layout, table, and table viewer.
	 * 
	 * @return
	 * @throws FileNotFoundException
	 */
	private void addChildControls(Composite composite) throws FileNotFoundException {

		// Create a composite to hold the children
		GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_BOTH);
		composite.setLayoutData(gridData);

		// Create the table
		createTable(composite);

		// Create and setup the TableViewer
		createTableViewer();

	}

	/**
	 * Creates the table with a grid of columns. Displays all the detection results
	 * into the columns.
	 * 
	 * @param parent
	 */
	private void createTable(Composite parent) throws FileNotFoundException {

		List<TSmellDetection> detections = TSmellResults.get();

		int style = SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.HIDE_SELECTION;

		table = new Table(parent, style);

		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalSpan = 3;
		table.setLayoutData(gridData);

		table.setLinesVisible(true);
		table.setHeaderVisible(true);

		TableColumn column = new TableColumn(table, SWT.CENTER, 0);
		column.setText("Test Smell");
		column.setWidth(200);

		column = new TableColumn(table, SWT.LEFT, 1);
		column.setText("Production File Path");
		column.setWidth(500);

		column = new TableColumn(table, SWT.LEFT, 2);
		column.setText("Test File Path");
		column.setWidth(500);

		column = new TableColumn(table, SWT.LEFT, 3);
		column.setText("Method Names");
		column.setWidth(500);

		// Suppress redraws while rows are added. Without this SWT lays out and
		// repaints the table after every single TableItem, which makes the UI
		// freeze for seconds on large workspaces.
		table.setRedraw(false);
		try {
			if (detections != null) {
				for (TSmellDetection detection : detections) {
					try {
						String[] tempSmellNames = detection.getSmellNames();
						String[] tempProdFilePaths = (String[]) detection.getProdFilePath();
						String[] tempTestFilePaths = (String[]) detection.getTestFilePath();
						String[] tempMethodNames = detection.getMethodNames();
						if (!tempProdFilePaths.equals(null))
							if (!tempTestFilePaths.equals(null))
								for (int i = 0; i < tempSmellNames.length; i++) {
									TableItem item = new TableItem(table, SWT.NONE);
									item.setText(new String[] { tempSmellNames[i], tempProdFilePaths[i],
											tempTestFilePaths[i], tempMethodNames[i] });
								}
					} catch (NullPointerException e) {
						Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
						String errorStr = "Recheck production and test file names. "
								+ "\nPlease restart your workspace after the file names have been corrected. "
								+ "\nBelow are the test file/production file mappings that triggered the exception: \n\n";
						if (detection.getProdFilePath() == null) {
							errorStr += "Production File: null\n";
						} else {
							errorStr += "Production File: " + detection.getProdFilePath() + "\n";
						}

						if (detection.getTestFilePath() == null) {
							errorStr += "Test File: null\n";
						} else {
							errorStr += "Test File: " + detection.getTestFilePath() + " \n\n";
						}

						TSmellDialog dialog = new TSmellDialog(activeShell, "Failed to load files", errorStr);
						dialog.open();
					}
				}
			}
		} finally {
			table.setRedraw(true);
		}

		table.addMouseListener(new MouseAdapter() {
			public void mouseDoubleClick(MouseEvent e) {
				TableItem[] selection = table.getSelection();
				if (selection.length == 0) {
					return;
				}
				openFileInEditor(selection[0].getText(2), selection[0].getText(3));
			}
		});
	}

	/**
	 * Opens the file at the given absolute path and reveals the named element.
	 */
	private void openFileInEditor(String absolutePath, String methodNames) {
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(new Path(absolutePath));
		if (file == null || !file.exists()) {
			return;
		}
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IEditorPart editor = IDE.openEditor(page, file);
			revealElement(editor, file, methodNames);
		} catch (PartInitException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Selects the named method in the editor. Some smells report a class name
	 * rather than a method name, so classes are matched as a fallback.
	 */
	private void revealElement(IEditorPart editor, IFile file, String methodNames) {
		if (methodNames == null || methodNames.trim().isEmpty()) {
			return;
		}
		if (!(editor instanceof ITextEditor)) {
			return;
		}
		String firstName = methodNames.split(",")[0].trim();
		try {
			ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);
			if (unit == null) {
				return;
			}
			for (IType type : unit.getAllTypes()) {
				for (IMethod method : type.getMethods()) {
					if (method.getElementName().equals(firstName)) {
						ISourceRange range = method.getNameRange();
						((ITextEditor) editor).selectAndReveal(range.getOffset(), range.getLength());
						return;
					}
				}
				if (type.getElementName().equals(firstName)) {
					ISourceRange range = type.getNameRange();
					((ITextEditor) editor).selectAndReveal(range.getOffset(), range.getLength());
					return;
				}
			}
		} catch (JavaModelException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Creates the TableViewer.
	 */
	private void createTableViewer() {

		tableViewer = new TableViewer(table);
		tableViewer.setUseHashlookup(true);
		tableViewer.setColumnProperties(columnNames);
	}

	/**
	 * Closes the window and disposes of resources.
	 */
	public void close() {
		Shell shell = table.getShell();

		if (shell != null && !shell.isDisposed())
			shell.dispose();
	}

	/**
	 * Releases resources.
	 */
	public void dispose() {
		tableViewer.getLabelProvider().dispose();
	}

	/**
	 * Returns the column names in a collection.
	 * 
	 * @return List containing column names
	 */
	public java.util.List<String> getColumnNames() {
		return Arrays.asList(columnNames);
	}

	/**
	 * Gets currently selected item in the table viewer.
	 * 
	 * @return currently selected item
	 */
	public ISelection getSelection() {
		return tableViewer.getSelection();
	}

	/**
	 * Returns the parent composite.
	 */
	public Control getControl() {
		return table.getParent();
	}

	/**
	 * Get an instance of the test smell table.
	 * 
	 * @return
	 */
	public static Table getTSmellTable() {
		return table;
	}
}