package main.java.testsmell.plugin.markers;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import main.java.testsmell.plugin.handlers.TSmellDetection;

/**
 * Creates and clears Eclipse problem markers for detected test smells.
 *
 * Markers place each smell on the line of the method that carries it, which
 * makes the smell visible in the editor ruler and in the Problems view. The
 * marker's message doubles as the hover text Eclipse shows over the marker.
 */
public final class TSmellMarkerManager {

	/** Declared in plugin.xml; prefixed with the bundle symbolic name. */
	public static final String MARKER_TYPE = "main.java.testsmell.plugin.testSmellMarker";

	private TSmellMarkerManager() {
	}

	/**
	 * Removes every test smell marker in the project. Called before each run so
	 * results from a previous run do not linger.
	 *
	 * @param project the project being analyzed
	 */
	public static void clearMarkers(IProject project) {
		if (project == null) {
			return;
		}
		try {
			project.deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
		} catch (CoreException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Creates a marker for every smell in the given detection.
	 *
	 * @param detection a detection whose detectSmells() has already run
	 */
	public static void createMarkers(TSmellDetection detection) {
		if (detection == null) {
			return;
		}
		String[] smellNames = detection.getSmellNames();
		String[] testFilePaths = detection.getTestFilePath();
		String[] elementNames = detection.getMethodNames();
		if (smellNames == null || testFilePaths == null || elementNames == null) {
			return;
		}

		for (int i = 0; i < smellNames.length; i++) {
			IFile file = fileFor(testFilePaths[i]);
			if (file == null) {
				continue;
			}
			String elements = elementNames[i];
			if (elements == null || elements.trim().isEmpty()) {
				createMarker(file, smellNames[i], "", 1, -1, -1);
				continue;
			}
			for (String element : elements.split(",")) {
				createMarkerForElement(file, smellNames[i], element.trim());
			}
		}
	}

	private static IFile fileFor(String absolutePath) {
		if (absolutePath == null || absolutePath.trim().isEmpty()) {
			return null;
		}
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(new Path(absolutePath));
		if (file == null || !file.exists()) {
			return null;
		}
		return file;
	}

	/**
	 * Locates the named method or type and places a marker on it. Falls back to
	 * line 1 when the element cannot be resolved.
	 */
	private static void createMarkerForElement(IFile file, String smellName, String elementName) {
		int line = 1;
		int start = -1;
		int end = -1;
		try {
			ICompilationUnit unit = JavaCore.createCompilationUnitFrom(file);
			if (unit != null) {
				ISourceRange range = rangeOf(unit, elementName);
				if (range != null) {
					start = range.getOffset();
					end = range.getOffset() + range.getLength();
					line = lineOf(unit, start);
				}
			}
		} catch (JavaModelException ex) {
			ex.printStackTrace();
		}
		createMarker(file, smellName, elementName, line, start, end);
	}

	/** Source range of the named method, or of a type with that name. */
	private static ISourceRange rangeOf(ICompilationUnit unit, String elementName) throws JavaModelException {
		for (IType type : unit.getAllTypes()) {
			for (IMethod method : type.getMethods()) {
				if (method.getElementName().equals(elementName)) {
					return method.getNameRange();
				}
			}
		}
		for (IType type : unit.getAllTypes()) {
			if (type.getElementName().equals(elementName)) {
				return type.getNameRange();
			}
		}
		return null;
	}

	/** 1-based line number containing the given character offset. */
	private static int lineOf(ICompilationUnit unit, int offset) throws JavaModelException {
		String source = unit.getSource();
		if (source == null || offset < 0) {
			return 1;
		}
		int limit = Math.min(offset, source.length());
		int line = 1;
		for (int i = 0; i < limit; i++) {
			if (source.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	private static void createMarker(IFile file, String smellName, String elementName, int line, int start, int end) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, SmellDescriptions.message(smellName, elementName));
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
			marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_NORMAL);
			marker.setAttribute(IMarker.LINE_NUMBER, line);
			if (start >= 0 && end > start) {
				marker.setAttribute(IMarker.CHAR_START, start);
				marker.setAttribute(IMarker.CHAR_END, end);
			}
		} catch (CoreException ex) {
			ex.printStackTrace();
		}
	}
}
