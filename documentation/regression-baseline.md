# Regression Baseline — Eclipse Plugin vs. Embedded Engine

**Date:** 2026-10-25
**Plugin build:** master, post Constructor Initialization fix
**Corpus:** `fixtures/` — two test/production pairs
  - `testCalculator.java` / `Calculator.java`
  - `testVerbose.java` / `Verbose.java`

## Purpose

Verify that the Eclipse plugin reports the same test smells as the detection
engine it wraps. Any difference between the two is, by definition, a defect in
the plugin layer rather than in detection, because both run identical engine
code.

## Method

The baseline is produced by `main.java.Main`, the command-line entry point that
ships inside this repository. It compiles against the same embedded engine
classes the plugin uses, so the comparison isolates plugin-layer behaviour
exactly.

```
java -cp "bin;lib\javaparser-core-3.2.4.jar;lib\commons-lang3-3.18.0.jar" \
     main.java.Main baseline-input.csv
```

`baseline-input.csv` lists one line per test file:

```
SmellFixture,<abs path>\testCalculator.java,<abs path>\Calculator.java
SmellFixture,<abs path>\testVerbose.java,<abs path>\Verbose.java
```

The plugin was then run against the same files through the Eclipse UI and its
results view compared against the generated output CSV.

### A note on the upstream CLI

The standalone `TestSmells/TestSmellDetector` repository was considered as the
baseline and rejected. Its engine has diverged from the copy embedded here: it
injects a configurable `Thresholds` object, reports smell counts rather than
booleans, and is partly implemented in Kotlin. Differences against it would
reflect engine versions rather than plugin defects.

## Results

| # | Smell | Baseline | Plugin | Match |
|---|-------|----------|--------|-------|
| 1 | Assertion Roulette | detected | detected | yes |
| 2 | Conditional Test Logic | detected | detected | yes |
| 3 | Constructor Initialization | detected | detected | yes |
| 4 | Default Test | detected | detected | yes |
| 5 | EmptyTest | detected | detected | yes |
| 6 | Exception Catching Throwing | detected | detected | yes |
| 7 | General Fixture | detected | detected | yes |
| 8 | Mystery Guest | detected | detected | yes |
| 9 | Print Statement | detected | detected | yes |
| 10 | Redundant Assertion | detected | detected | yes |
| 11 | Sensitive Equality | detected | detected | yes |
| 12 | Sleepy Test | detected | detected | yes |
| 13 | Eager Test | detected | detected | yes |
| 14 | Lazy Test | detected | detected | yes |
| 15 | Duplicate Assert | detected | detected | yes |
| 16 | Unknown Test | detected | detected | yes |
| 17 | IgnoredTest | detected | detected | yes |
| 18 | Resource Optimism | detected | detected | yes |
| 19 | Magic Number Test | detected | detected | yes |
| 20 | Verbose Test | detected | detected | yes |
| 21 | Dependent Test | not reported | not reported | yes (see below) |

**Coverage: 20 of 21 detectors exercised. Plugin and baseline agree on all 21.**

Smells 1–19 are triggered by `testCalculator.java`; Verbose Test is isolated in
`testVerbose.java` because its threshold requires a deliberately oversized
method that would otherwise dominate the readable fixture.

Dependent Test is absent from both. This is not a corpus gap — the analyzer
cannot fire under any input (see Finding 3).

## Discrepancy found and resolved: Constructor Initialization

### Symptom

An earlier run of this baseline reported 16 smells against the plugin's 15.
Constructor Initialization was flagged by the engine but silently omitted from
the plugin's results view, with no error raised.

### Root cause

`TestFile.getTestFileName()` derives the class name by splitting the file path
on backslashes only:

```java
int lastIndex = testFilePath.lastIndexOf("\\");
return testFilePath.substring(lastIndex + 1, testFilePath.length());
```

The plugin obtained paths via `IPath.toString()`, which returns forward-slash
separated paths. The backslash search returned `-1`, so the method returned the
entire path rather than the file name. `ConstructorInitialization` only reports
when the constructor's name matches that derived name, so it never matched.

The CLI baseline was unaffected because its paths came from the input CSV
already in Windows form.

### Fix

`TSmellHandler.java` — replaced `toString()` with `toOSString()`, which returns
the platform-native path representation. The defect originates in an engine
file that project constraints prohibit modifying, so the fix is applied in the
plugin layer. Engine files remain unchanged.

## Engine defects identified

The following were found by inspecting the analyzers while building the corpus.
All are in engine code and therefore out of scope to repair under the project's
"engine must remain unchanged" constraint. They are recorded because they affect
how the tool's output should be interpreted.

### Finding 1 — Windows path coupling

The engine assumes Windows path separators throughout. Beyond
`getTestFileName()`, `getTagName()` performs `testFilePath.split("\\\\")[4]`,
and both relative-path methods assume at least five backslash-separated
segments. On macOS or Linux these fail the same way, and `getTagName()` would
raise `ArrayIndexOutOfBoundsException` on shorter paths. Relevant to any
cross-platform testing of the plugin.

### Finding 2 — Resource Optimism partially inoperative

`ResourceOptimism` has two detection paths. The one handling declared variables
compares a JavaParser `Type` object to a `String`:

```java
if (variableDeclarator.getType().equals("File")) {
```

This can never be true, so the canonical case — `File f = new File("x");`
without an `exists()` check — is never flagged. Only the inline form, which
correctly uses `.asString().equals("File")`, fires. The corpus triggers the
inline form deliberately.

### Finding 3 — Dependent Test cannot fire

`DependentTest` compares a `String` to a `Stream`:

```java
x.getName().equals(testMethods.stream().map(z -> z.getMethodDeclaration().getNameAsString()))
```

This is always false. A corrected version exists in the same file, commented
out. Independently, the `TestMethod` element the analyzer would create never has
`setHasSmell(true)` called on it, so even a matching condition would report
nothing. The detector is inoperative under all inputs, which is why 20 of 21 is
the maximum attainable corpus coverage.

### Finding 4 — Debug output on stdout

`ResourceOptimism` contains a leftover `System.out.println(n.getType())` that
fires for object creations outside test methods, writing stray lines to the
console during analysis.

## Conclusion

The plugin's output matches the embedded engine exactly across all 21
detectors. One genuine plugin-layer defect was found and corrected as a direct
result of establishing this baseline, and four engine-side defects were
identified and documented.
