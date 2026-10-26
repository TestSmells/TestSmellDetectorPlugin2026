# Use Cases — Eclipse Plugin

**Issue:** #34 — Eclipse: Use Case Document
**Scope:** Eclipse plugin only. The VS Code extension is a separate workstream.
**Last updated:** October 2026

> **Numbering note.** UC1 and UC5 keep the numbers already referenced in the SDS and
> the issue tracker. Reconcile the remaining numbers against the team's original
> list before this document is circulated.

---

## Actors

| Actor | Description |
|---|---|
| **Developer** | Writes and maintains Java unit tests. The primary user. Runs detection, reads results, and navigates to the code responsible. |
| **Team lead** | Decides which smells the team treats as actionable and configures the plugin accordingly. |
| **tsDetect engine** | The existing detection library, embedded in the plugin. Supporting actor — it performs the analysis; the plugin supplies input and presents output. |
| **Eclipse workbench** | Supporting actor. Provides the editor, Problems view, progress reporting and preference infrastructure the plugin extends. |

---

## Assumptions and constraints

- The project under analysis has the Java nature enabled.
- Test files follow the `testExample.java` ↔ `Example.java` naming convention. The
  `ExampleTest.java` convention is not supported — see §Limitations.
- The tsDetect engine is used unmodified, per the SRS technical constraint. The
  plugin supplies input and presents results; it does not alter detection logic.

---

## UC1 — Detect test smells when a file is saved

**Status:** Planned
**Actor:** Developer
**Goal:** Receive feedback on test quality while writing tests, without having to
remember to run anything.

**Preconditions**
- The plugin is installed and on-save detection is enabled in preferences.
- The developer is editing a Java test file in a project with the Java nature.

**Main flow**
1. The developer edits a test file and saves it.
2. The plugin detects the save and schedules a detection run for the affected file.
3. Detection runs in the background; the editor remains usable throughout.
4. Problem markers for that file are replaced with the new results.
5. The developer sees markers appear in the editor margin and Problems view.

**Alternate flows**
- *A1 — A run is already in progress for that file.* The in-flight run is
  superseded by the new one rather than queued behind it.
- *A2 — The saved file is not a test file.* No detection is triggered.

**Postconditions**
Markers for the saved file reflect its current content. Markers for other files are
unchanged.

**Notes**
This use case was blocked until detection moved off the UI thread. Running
detection synchronously on every save would have frozen the editor several times a
minute — the Usability Risk recorded in the project plan. That prerequisite is now
complete (#73), so UC1 is unblocked.

**Related:** #73

---

## UC2 — Run detection on a project

**Status:** Implemented
**Actor:** Developer
**Goal:** Analyze an entire project's tests on demand.

**Preconditions**
- The plugin is installed.
- A project with the Java nature exists in the workspace.

**Main flow**
1. The developer selects a project in the Package Explorer.
2. The developer clicks the Test Smell Detector toolbar button, or presses
   `Ctrl+6`.
3. The plugin resolves the selected project and walks it for compilation units.
4. Each test file is paired with its production file and handed to the engine.
5. Detection runs in the background with progress shown in the status bar.
6. Results are published to the Test Smell Detector view, which opens
   automatically.

**Alternate flows**
- *A1 — Nothing is selected.* The plugin falls back to the file open in the active
  editor, then to the project analyzed in the previous run. If none of these
  resolve, it reports that a project must be selected.
- *A2 — A run is already in progress.* The in-flight run is cancelled and replaced.
- *A3 — The developer cancels.* The run stops and results from it are discarded.

**Exception flows**
- *E1 — A test file has no matching production file.* The plugin reports the
  unmatched pairing and names the files involved.
- *E2 — The project does not have the Java nature.* The run does not start.

**Postconditions**
The results view lists every detected smell. Problem markers exist for each.

**Related:** #44, #52, #73

---

## UC3 — Review detected smells

**Status:** Implemented
**Actor:** Developer
**Goal:** Understand what was detected and where.

**Preconditions**
A detection run has completed.

**Main flow**
1. The Test Smell Detector view opens with one row per detected smell.
2. Each row shows the smell name, the production file, the test file, and the
   method or class responsible.
3. The developer scans the table to see which smells occur and how often.

**Postconditions**
The developer knows which tests carry which smells.

**Notes**
The Method Names column existed in the original plugin but was always blank — the
table supplied three values for four columns. The engine already returns the
responsible elements; the plugin was discarding them.

**Related:** #69

---

## UC4 — Navigate from a result to the code responsible

**Status:** Implemented
**Actor:** Developer
**Goal:** Go straight from a reported smell to the method that causes it.

**Preconditions**
The results view lists at least one smell.

**Main flow**
1. The developer double-clicks a row in the results view.
2. The plugin opens the corresponding test file in an editor.
3. The plugin locates the named method through JDT and selects its source range.
4. The editor scrolls to the method and highlights it.

**Alternate flows**
- *A1 — The result names a class rather than a method.* The class declaration is
  revealed instead. Constructor Initialization reports at class level by design.
- *A2 — The file is no longer in the workspace.* No editor is opened.

**Postconditions**
The developer is positioned on the code responsible for the smell.

**Related:** #70

---

## UC5 — Run detection from the project context menu

**Status:** Implemented
**Actor:** Developer
**Goal:** Run detection without using the toolbar or remembering a shortcut.

**Preconditions**
A project with the Java nature exists in the workspace.

**Main flow**
1. The developer right-clicks a project in the Package Explorer.
2. The developer selects **Run Test Smell Analysis**.
3. Detection proceeds as in UC2 from step 3.

**Postconditions**
As UC2.

**Notes**
The original plugin exposed detection only through a toolbar button and `Ctrl+6`,
neither of which is discoverable. Right-clicking the thing you want to act on is
where Eclipse users look first.

---

## UC6 — See smells alongside compiler problems

**Status:** Implemented
**Actor:** Developer
**Goal:** Treat test smells as part of normal code review rather than something
in a separate tool.

**Preconditions**
A detection run has completed.

**Main flow**
1. Detected smells appear in the Problems view alongside compiler warnings.
2. Markers appear in the editor's vertical ruler next to the responsible method.
3. The developer hovers a marker.
4. A tooltip explains what the smell is and suggests how to fix it.

**Alternate flows**
- *A1 — A new run starts.* Markers for the analyzed project are cleared before new
  ones are created, so results never accumulate across runs.

**Postconditions**
Smells are visible in the workbench's own problem reporting, with an explanation
attached.

**Notes**
The original plugin reported smell names only, inside its own table. A developer
who had not read the tsDetect paper had no way to learn what "Eager Test" meant or
what to do about it.

**Related:** #9, #10

---

## UC7 — Configure which smells are detected

**Status:** Implemented
**Actor:** Team lead
**Goal:** Suppress smells the team has decided not to act on, so the remaining
results stay meaningful.

**Preconditions**
The plugin is installed.

**Main flow**
1. The team lead opens Window → Preferences → Test Smell Detector.
2. A checkbox is shown for each of the 21 detectors, all enabled by default.
3. The team lead clears the checkboxes for smells the team does not act on.
4. The team lead applies the change.
5. Subsequent runs report only enabled smells — in the results view and as markers.

**Postconditions**
Disabled smells produce neither table rows nor problem markers.

**Notes**
All 21 detectors run unconditionally in the original plugin. A team that considers
some smells acceptable had no way to quieten them, which makes the whole report
easier to ignore.

**Related:** #15

---

## UC8 — Cancel a long-running detection

**Status:** Implemented
**Actor:** Developer
**Goal:** Stop a run started by mistake, or on a project larger than intended,
without waiting for it or restarting Eclipse.

**Preconditions**
A detection run is in progress.

**Main flow**
1. Progress is reported in the status bar while the run proceeds.
2. The developer clicks the cancel control on the progress indicator.
3. The run stops at the next file boundary.
4. No results are published and no markers are created.

**Postconditions**
The workspace is left as it was before the run. The workbench stays responsive
throughout.

**Related:** #73

---

## UC9 — Install and update the plugin

**Status:** Implemented
**Actor:** Developer
**Goal:** Install the plugin without building it from source, and receive updates.

**Preconditions**
A current Eclipse installation.

**Main flow**
1. The developer opens Help → Install New Software.
2. The developer enters the update site URL:
   `https://testsmells.github.io/TestSmellDetectorPlugin2026/`
3. Eclipse lists the Test Smell Detector feature.
4. The developer selects it and completes the wizard.
5. Eclipse restarts with the plugin installed.

**Alternate flows**
- *A1 — A newer version is published.* Help → Check for Updates offers it, provided
  the bundle and feature versions were incremented.

**Postconditions**
The plugin is installed and its toolbar button, context menu entry and preference
page are available.

**Related:** #13

---

## Limitations

These constrain the use cases above and are recorded here so they are not
rediscovered as defects.

**File pairing.** A test file is matched to its production file by stripping the
first four characters of the filename, so only `testExample.java` → `Example.java`
is supported. `ExampleTest.java` produces no match and the run reports a pairing
failure. Changing this would alter what the engine expects, which the SRS
constraint places out of scope.

**Dependent Test is never reported.** The detector compares a `String` to a
`Stream`, which is always false, and never sets its smell flag. It cannot fire
under any input. This is an engine defect, documented in
`documentation/regression-baseline.md` rather than fixed.

**Detection is project-wide.** UC2 and UC5 analyze an entire project. There is no
use case for analyzing a single file on demand; UC1 will cover the per-file case
when implemented.

---

## Traceability

| Use case | Status | Issues |
|---|---|---|
| UC1 — Detect on save | Planned | #73 (prerequisite) |
| UC2 — Run detection on a project | Implemented | #44, #52, #73 |
| UC3 — Review detected smells | Implemented | #69 |
| UC4 — Navigate to the code | Implemented | #70 |
| UC5 — Run from context menu | Implemented | — |
| UC6 — See smells with compiler problems | Implemented | #9, #10 |
| UC7 — Configure detected smells | Implemented | #15 |
| UC8 — Cancel a run | Implemented | #73 |
| UC9 — Install and update | Implemented | #13 |
