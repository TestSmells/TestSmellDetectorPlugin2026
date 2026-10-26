package main.java.testsmell.plugin.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import main.java.testsmell.TestFile;
import main.java.testsmell.plugin.markers.TSmellMarkerManager;
import main.java.testsmell.plugin.views.TSmellDialog;

/**
 * A handler class for the the test smell plugin.
 * 
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class TSmellHandler extends AbstractHandler {
	private ArrayList<IResource> testPaths = new ArrayList<IResource>();
	private HashMap<IResource, IResource> paths = new HashMap<>();
	private IProject project;
	private List<TestFile> testFiles = new ArrayList<TestFile>();

	/** Identifies this plugin's detection jobs so a new run can cancel an older one. */
	private static final Object JOB_FAMILY = new Object();

	/**
	 * The project analyzed by the most recent successful run. Opening the results
	 * view moves focus away from the Package Explorer, so a second run started
	 * from the toolbar has no project in its selection. Remembering the last
	 * project lets repeated runs work without reselecting the project first.
	 */
	private static IProject lastAnalyzedProject;

	/**
	 * Launches the Test Smell Detector view when a project is selected and the
	 * plugin icon is activated.
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		long startTime = System.currentTimeMillis();
		testPaths.clear();
		paths.clear();
		testFiles.clear();
		Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

		try {
			project = getCurrentProject(event);
			if (project == null) {
				MessageDialog.openInformation(activeShell, "Test Smell Detector",
						"Select a project in the Package Explorer, then run the detector again.");
				return null;
			}
			createTestFiles(project);
		} catch (CoreException | RuntimeException ex) {
			ex.printStackTrace();
			MessageDialog.openError(activeShell, "Test Smell Detector",
					"Could not read the selected project.\n\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage());
			return null;
		}

		lastAnalyzedProject = project;

		// Everything below runs off the UI thread. The work is prepared here,
		// on the UI thread, because the selection and the Java model must be
		// read from it.
		final IProject analyzedProject = project;
		final List<TSmellDetection> detections = new ArrayList<TSmellDetection>();
		for (TestFile testFile : testFiles) {
			detections.add(new TSmellDetection(testFile));
		}
		final int analyzedCount = detections.size();
		final long started = startTime;

		// A new run supersedes one still in progress rather than queueing behind it.
		Job.getJobManager().cancel(JOB_FAMILY);

		WorkspaceJob job = new WorkspaceJob("Detecting test smells") {

			@Override
			public boolean belongsTo(Object family) {
				return JOB_FAMILY.equals(family);
			}

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				SubMonitor progress = SubMonitor.convert(monitor, "Detecting test smells", detections.size() + 1);

				TSmellMarkerManager.clearMarkers(analyzedProject);
				progress.worked(1);

				for (TSmellDetection detection : detections) {
					if (progress.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
					detection.detectSmells();
					TSmellMarkerManager.createMarkers(detection);
					progress.worked(1);
				}

				long elapsed = System.currentTimeMillis() - started;
				System.out.println("[tsDetect] Analyzed " + analyzedCount + " test file(s) in " + elapsed + " ms");

				publishAndShow(detections);
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.setRule(analyzedProject);
		job.schedule();

		return null;
	}

	/**
	 * Publishes a completed run's results and opens the results view.
	 *
	 * Both happen together on the UI thread so the view never reads results
	 * that have not been published yet.
	 *
	 * @param detections the completed run's detections
	 */
	private static void publishAndShow(final List<TSmellDetection> detections) {
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				TSmellResults.publish(detections);

				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window == null) {
					return;
				}
				IWorkbenchPage page = window.getActivePage();
				if (page == null) {
					return;
				}
				try {
					IViewPart viewPart = page.findView("TestSmellDetector.view");
					if (viewPart != null) {
						page.hideView(viewPart);
					}
					page.showView("TestSmellDetector.view");
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Gets the current project that is selected.
	 * 
	 * @param e
	 * @return
	 */
	private IProject getCurrentProject(ExecutionEvent e) {
		IProject fromSelection = projectFromSelection(HandlerUtil.getCurrentSelection(e));
		if (fromSelection != null) {
			return fromSelection;
		}

		IProject fromEditor = projectFromActiveEditor();
		if (fromEditor != null) {
			return fromEditor;
		}

		// Nothing is selected and no file is open. Fall back to the project
		// analyzed last, provided it still exists and is open.
		if (lastAnalyzedProject != null && lastAnalyzedProject.exists() && lastAnalyzedProject.isAccessible()) {
			return lastAnalyzedProject;
		}

		return null;
	}

	/**
	 * Resolves the enclosing project of the current workbench selection, or null
	 * if the selection is empty or is not backed by a workspace resource.
	 */
	private IProject projectFromSelection(ISelection sel) {
		if (!(sel instanceof IStructuredSelection)) {
			return null;
		}

		Object selected = ((IStructuredSelection) sel).getFirstElement();
		if (selected == null) {
			// An empty selection yields a null element. Passing it to the
			// adapter manager raises AssertionFailedException, so stop here.
			return null;
		}

		if (selected instanceof IResource) {
			return ((IResource) selected).getProject();
		}

		IResource resource = Platform.getAdapterManager().getAdapter(selected, IResource.class);
		return resource == null ? null : resource.getProject();
	}

	/**
	 * Resolves the enclosing project of the file open in the active editor, or
	 * null if no editor is open on a workspace file.
	 */
	private IProject projectFromActiveEditor() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null) {
			return null;
		}

		IWorkbenchPage page = window.getActivePage();
		if (page == null) {
			return null;
		}

		IEditorPart editor = page.getActiveEditor();
		if (editor == null) {
			return null;
		}

		IEditorInput input = editor.getEditorInput();
		if (input == null) {
			return null;
		}

		IFile file = input.getAdapter(IFile.class);
		return file == null ? null : file.getProject();
	}

	/**
	 * Creates test files that can be analyzed for detection smells using both
	 * production and test file paths.
	 * 
	 * @param project
	 * @throws CoreException
	 * @throws JavaModelException
	 */
	private void createTestFiles(IProject project) throws CoreException, JavaModelException {
		if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
			IJavaProject javaProject = JavaCore.create(project);
			IPackageFragment[] packages = javaProject.getPackageFragments();
			for (IPackageFragment pkg : packages) {
				if (pkg.getKind() == IPackageFragmentRoot.K_SOURCE) {
					for (ICompilationUnit unit : pkg.getCompilationUnits()) {
						testPaths = getTestFilePaths(unit);
					}
				}
			}
			if (testPaths.size() > 0) {
				try {
					getProdFilePaths();

					TestFile testFile;

					for (Entry<IResource, IResource> fullPaths : paths.entrySet()) {
						String prodFilePath = fullPaths.getKey().getRawLocation().toOSString();
						String testFilePath = fullPaths.getValue().getRawLocation().toOSString();
						testFile = new TestFile(project.getName(), testFilePath, prodFilePath);
						testFiles.add(testFile);
					}
				} catch (NullPointerException e) {
					Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
					String errorStr = "Recheck production and test file names. "
							+ "\nPlease restart your workspace after the file names have been corrected. "
							+ "\nBelow are the test file/production file mappings that triggered the exception: \n\n";
					for (Entry<IResource, IResource> fullPaths : paths.entrySet()) {
						if (fullPaths.getKey() == null) {
							errorStr += "Production File: null\n";
						} else {
							errorStr += "Production File: " + fullPaths.getKey().getRawLocation().toString() + "\n";
						}

						if (fullPaths.getValue() == null) {
							errorStr += "Test File: null\n";
						} else {
							errorStr += "Test File: " + fullPaths.getValue().getRawLocation().toString() + " \n\n";
						}
					}
					TSmellDialog dialog = new TSmellDialog(activeShell, "Failed to load files", errorStr);
					dialog.open();
				}
			}
		}
	}

	/**
	 * Gets the production file paths.
	 * 
	 * @param unit
	 * @return
	 * @throws JavaModelException
	 */
	private void getProdFilePaths() throws JavaModelException {
		HashMap<String, IResource> possibleProdFileNames = new HashMap<String, IResource>();
		HashMap<String, IResource> potentialProdFiles = new HashMap<>();
		for (IResource test : testPaths) {

			String fileName = test.getName();
			int prefixLength = "test".length();
			String prodFileName = fileName.substring(prefixLength, fileName.length());
			possibleProdFileNames.put(prodFileName, test);
		}

		IContainer container = containerForPath(project.getFullPath());
		try {
			potentialProdFiles = findJavaFiles(container);
		} catch (CoreException e) {
			e.printStackTrace();
		}

		for (Entry<String, IResource> possibleNames : possibleProdFileNames.entrySet()) {
			String key = possibleNames.getKey();
			IResource prodIRes = potentialProdFiles.get(key);
			IResource testIRes = possibleNames.getValue();
			paths.put(prodIRes, testIRes);
		}
	}

	/**
	 * Gets the container for a given path.
	 * 
	 * @param path
	 * @return
	 */
	private IContainer containerForPath(IPath path) {
		if (path.segmentCount() == 1) {
			return ResourcesPlugin.getWorkspace().getRoot().getProject(path.segment(0));
		} else {
			return ResourcesPlugin.getWorkspace().getRoot().getFolder(path);
		}
	}

	/**
	 * Finds all .java file paths in a given container.
	 * 
	 * @param container
	 * @return
	 * @throws CoreException
	 */
	private HashMap<String, IResource> findJavaFiles(IContainer container) throws CoreException {
		IResource[] members = container.members();
		String path;
		HashMap<String, IResource> paths = new HashMap<String, IResource>();
		for (IResource member : members) {
			HashMap<String, IResource> findProdFiles = new HashMap<String, IResource>();

			if (member instanceof IContainer) {
				findProdFiles = findJavaFiles((IContainer) member);
				if (!findProdFiles.isEmpty()) {
					paths.putAll(findProdFiles);
				}
			} else if (member instanceof IFile) {
				path = member.getRawLocation().makeAbsolute().toString();
				if (path.substring(path.lastIndexOf("."), path.length()).toLowerCase().equals(".java")) {
					paths.put(path.substring(path.lastIndexOf("/") + 1, path.length()), member);
				} else {
					continue;
				}
			}
		}
		return paths;
	}

	/**
	 * Gets the test file paths by checking if test methods exist in a file.
	 * 
	 * @param unit
	 * @return
	 * @throws JavaModelException
	 */
	private ArrayList<IResource> getTestFilePaths(ICompilationUnit unit) throws JavaModelException {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor() {
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().startsWith("test")
						|| node.getName().getIdentifier().startsWith("Test")
								&& !testPaths.contains(unit.getResource())) {
					testPaths.add(unit.getResource());
					return true;
				}
				return false;
			}
		});
		return testPaths;
	}
}