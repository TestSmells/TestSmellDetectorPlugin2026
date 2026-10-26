# Architecture Analysis — Eclipse Plugin

**Issue:** #1 — Analyze the existing Eclipse plugin architecture and identify
modernization requirements
**Baseline analyzed:** commit `c1c3725`, 2018-05-06 — the last commit before this
project began
**Analysis date:** September 2026
**Author:** Kamrul Haque

This document records the state of the Eclipse plugin as inherited, the defects and
obsolescence found in it, and the modernization requirements derived from that
analysis. The requirements became issues #3, #4, #5 and #62.

---

## 1. Scope and constraint

tsDetect consists of a **detection engine** and a set of **IDE integrations**. This
project modernizes the Eclipse integration only.

The governing technical constraint, from the SRS:

> The existing tsDetect engine must remain unchanged; the plugins must integrate
> with the current implementation rather than replacing its detection algorithms.

Every finding below is therefore classified as either in scope (plugin layer) or
out of scope (engine). This distinction shapes the whole project: several real
defects were found in the engine and deliberately left unfixed.

**In scope:** `src/main/java/testsmell/plugin/**`, `META-INF/MANIFEST.MF`,
`plugin.xml`, `build.properties`

**Out of scope:** `src/main/java/testsmell/*.java` (`TestSmellDetector`,
`AbstractSmell`, `TestFile`, `TestClass`, `TestMethod`, `SmellyElement`, `Util`,
`ResultsWriter`) and all 21 detectors in `src/main/java/testsmell/smell/`

---

## 2. As-found architecture

### 2.1 Repository layout

The repository holds both layers in one source tree, which is why the constraint
has to be stated as a file-level boundary rather than a module boundary:

```
src/main/java/testsmell/
├── AbstractSmell.java          ─┐
├── TestSmellDetector.java       │
├── TestFile.java                │  engine — out of scope
├── TestClass.java               │  (8 classes)
├── TestMethod.java              │
├── SmellyElement.java           │
├── Util.java                    │
├── ResultsWriter.java          ─┘
├── smell/                       ── 21 detectors — out of scope
└── plugin/                      ── Eclipse integration — in scope
    ├── Activator.java
    ├── handlers/
    │   ├── TSmellHandler.java
    │   ├── TSmellDetection.java
    │   └── TSmellDetectionCollection.java
    └── views/
        ├── TSmellView.java
        ├── TSmellTableViewer.java
        └── TSmellDialog.java
```

Seven classes make up the entire Eclipse integration.

### 2.2 Components

| Class | Responsibility |
|---|---|
| `Activator` | OSGi bundle lifecycle; holds the plugin singleton |
| `TSmellHandler` | Command handler. Resolves the selected project, walks it for compilation units, pairs test files with production files, builds `TestFile` objects, invokes detection, opens the results view |
| `TSmellDetection` | Wraps one `TestFile` and the engine's results for it |
| `TSmellDetectionCollection` | Singleton holding the results of the current run, read by the view |
| `TSmellView` | `ViewPart` hosting the results table |
| `TSmellTableViewer` | Builds the SWT `Table` and populates its rows |
| `TSmellDialog` | Error dialog, used when file pairing fails |

### 2.3 Control flow

1. The user activates the toolbar button or presses `Ctrl+6`.
2. Eclipse dispatches to `TSmellHandler.execute()`.
3. The handler reads the current selection, resolves the enclosing `IProject`, and
   confirms the Java nature is enabled.
4. It walks each `IPackageFragment` for `ICompilationUnit`s, parses each with JDT's
   `ASTParser` to find test methods, and pairs each test file with its production
   file by filename convention.
5. Each pair becomes an engine `TestFile`; detection runs over all 21 detectors.
6. Results are placed in `TSmellDetectionCollection`.
7. The handler shows `TSmellView`, which reads the collection and renders rows.

**All of this happens on the UI thread**, inside the handler's `execute()` call.

### 2.4 Extension points used

Declared in `plugin.xml`:

| Extension point | Purpose |
|---|---|
| `org.eclipse.ui.commands` | Declares the detection command and its category |
| `org.eclipse.ui.handlers` | Binds the command to `TSmellHandler` |
| `org.eclipse.ui.bindings` | Keyboard shortcut `Ctrl+6` |
| `org.eclipse.ui.menus` | Toolbar button |
| `org.eclipse.ui.views` | Registers the results view |
| `org.eclipse.ui.perspectiveExtensions` | Places the view in the Java perspective |

Not present: no problem markers, no preference page, no context menu contribution.
Detection could only be triggered from the toolbar or the keyboard, and results
existed only inside the plugin's own table.

---

## 3. As-found dependencies

### 3.1 Bundle metadata

`META-INF/MANIFEST.MF` as inherited:

```
Bundle-SymbolicName: main.java.testsmell.plugin;singleton:=true
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.resources;bundle-version="3.12.0",
 org.eclipse.jdt.core;bundle-version="3.13.100"
Import-Package: com.github.javaparser,
 org.apache.commons.lang3;version="3.1.0"
Bundle-ClassPath: lib/javaparser-core-3.2.4.jar,
 bin/,
 .
```

Four problems are visible in this file alone:

1. **`Bundle-RequiredExecutionEnvironment: JavaSE-1.8`.** The BREE constrains the
   build path to the Java 8 API surface. Compiling against a modern bundled JRE
   fails on classes the Java 8 profile does not expose — in practice,
   `StringConcatFactory`, which `javac` emits for string concatenation from Java 9
   onward.

2. **`Import-Package: org.apache.commons.lang3;version="3.1.0"`** with no provider.
   Nothing in `Bundle-ClassPath` supplies commons-lang3 and no required bundle
   exports it, so the constraint is unsatisfiable and the bundle does not resolve.

3. **Pinned 2017-era bundle versions.** `org.eclipse.core.resources 3.12.0` and
   `org.eclipse.jdt.core 3.13.100` correspond to Eclipse Oxygen. These are lower
   bounds, so they resolve, but they document an eight-year-old target platform.

4. **`bin/` on `Bundle-ClassPath`.** Build output is listed as a runtime classpath
   entry, which couples the bundle to one particular build layout.

The manifest also re-exports the whole of `com.github.javaparser` via
`Export-Package`. A plugin exporting a third-party library into the OSGi namespace
invites version conflicts with any other bundle doing the same.

### 3.2 The vestigial Maven POM

`pom.xml` is present at the repository root and has not been modified since the
2018 import. It declares:

```
groupId    edu.rit.se.testsmells
artifactId TestSmellDetector
version    0.1
```

with `maven-compiler-plugin` at source/target 1.8 and dependencies on javaparser
3.2.4, opencsv 3.9 and commons-lang 2.3.

**This POM does not build the plugin.** It is the POM of the standalone tsDetect
CLI, inherited with the source tree. It contains no Tycho configuration, no
packaging type of `eclipse-plugin`, and no reference to the bundle manifest or
`plugin.xml`. The plugin is built entirely by Eclipse PDE from the workspace.

Two consequences follow:

- Its dependency list is not the plugin's dependency list. It names commons-lang
  2.3 where the manifest imports commons-lang3; neither matches what the bundle
  actually needs.
- **There is no headless build.** Nothing can compile or package this plugin
  outside the Eclipse IDE. This is the reason continuous integration (#16) is
  substantially harder here than for the VS Code extension, which has `npm` and a
  real command-line build.

### 3.3 Third-party libraries

| Library | Version | Location |
|---|---|---|
| javaparser-core | 3.2.4 | `lib/`, on `Bundle-ClassPath` |
| commons-lang3 | — | declared in `Import-Package`, **not present** |

javaparser 3.2.4 was released in 2017. Upgrading it was considered under #7 and
declined: the engine's detectors extend javaparser visitor classes and consume its
AST node types throughout, so a major-version upgrade requires editing
`testsmell/smell/**`, which the constraint forbids. Upgrading javaparser is engine
work, not plugin work.

---

## 4. Findings

### F1 — The bundle does not resolve on a current Eclipse
Unsatisfiable `Import-Package` constraint on commons-lang3 (§3.1.2). The plugin
cannot start. **In scope.** → #3

### F2 — The build path is pinned to Java 8
`Bundle-RequiredExecutionEnvironment: JavaSE-1.8` breaks compilation against a
modern JRE (§3.1.1). **In scope.** → #3, #4

### F3 — A removed JDT constant is referenced
`TSmellHandler` passes `AST.JLS9` to `ASTParser.newParser()`. That constant has
been deleted from JDT, so the plugin does not compile. **In scope.** → #5

### F4 — Detection runs on the UI thread
`TSmellHandler.execute()` performs parsing and detection synchronously (§2.3). The
workbench cannot repaint for the duration. At the project sizes available this was
tolerable, but it rules out any automatic trigger such as detection on save.
**In scope.** → later filed as #73

### F5 — Results are held in a mutable singleton
`TSmellDetectionCollection` is a singleton holding the current run's results, read
by the view with no synchronization. Safe only because everything ran on one
thread; a blocker for F4. **In scope.** → later filed as #73

### F6 — Handler state is never cleared
`TSmellHandler` keeps its path and file collections as instance fields and never
clears them. Eclipse reuses one handler instance for the workbench session, so each
run appends to the previous run's state. **In scope.** → later filed as #52

### F7 — Results do not reach Eclipse's own UI
No problem markers are created (§2.4). Detected smells appear only in the plugin's
table, not in the Problems view or the editor ruler, and carry no explanation of
what the smell means. **In scope.** → later filed as #9, #10

### F8 — The Method Names column is always empty
`TSmellTableViewer` declares four columns but supplies three values to `setText`.
The engine exposes the responsible elements through
`AbstractSmell.getSmellyElements()`; the plugin discards them. **In scope.** → #69

### F9 — Detection cannot be tuned
All 21 detectors always run and all results are always reported (§2.4). A team that
accepts some smells has no way to quieten them. **In scope.** → later filed as #15

### F10 — File pairing is convention-bound and undocumented
The handler pairs a test file with its production file by stripping the first four
characters of the filename, supporting only `testExample.java` → `Example.java`.
The widely used `ExampleTest.java` convention produces no match and the run fails.
**In scope** as a limitation to document; changing the convention would alter
engine expectations. → documented in `README.md` and
`documentation/regression-baseline.md`

### F11 — No headless build exists
`pom.xml` is the CLI's POM and does not build the bundle (§3.2). **In scope** but
substantial. → #16

### F12 — Engine defects, out of scope
Verification against the engine (#62, #72) surfaced four defects in engine code:
Windows-only path splitting in `TestFile.getTestFileName()` and `getTagName()`; a
`Type`-to-`String` comparison in `ResourceOptimism` that prevents its
assigned-variable case from firing; a `String`-to-`Stream` comparison in
`DependentTest` that makes the detector unreachable under any input; and a stray
`System.out.println` in `ResourceOptimism`.

**Out of scope.** Recorded in `documentation/regression-baseline.md` rather than
fixed. `DependentTest` being unreachable is why corpus coverage is capped at 20 of
21 detectors.

---

## 5. Modernization requirements

| # | Requirement | From | Issue |
|---|---|---|---|
| R1 | Resolve bundle dependencies so the plugin starts on current Eclipse | F1 | #3 |
| R2 | Raise the execution environment and verify the build on a current JDK | F2 | #3, #4 |
| R3 | Replace removed and deprecated Eclipse APIs | F3 | #5 |
| R4 | Confirm the plugin installs and runs on the latest Eclipse release | F1–F3 | #4 |
| R5 | Establish a regression baseline before changing behaviour | F12 | #62 |

R5 was treated as a precondition rather than a follow-on: without a recorded
baseline there is no way to show that modernization preserved detection behaviour.
It is the reason #62 and #72 exist, and the reason the engine defects in F12 are
documented rather than silently inherited.

Findings F4 through F11 were filed separately as the modernization work exposed
them, and are tracked as #9, #10, #15, #16, #52, #69 and #73.

---

## 6. What this analysis did not cover

- **The VS Code extension.** A separate workstream.
- **Engine internals beyond the integration boundary.** The detectors were read
  only as far as needed to understand what the plugin passes them and what it gets
  back. The four defects in F12 were found through behavioural comparison, not a
  code audit.
- **Performance.** Deferred to #11, which required a working build first.
- **Security review.** Not applicable; the plugin reads workspace files and writes
  markers, with no network access and no credential handling.
