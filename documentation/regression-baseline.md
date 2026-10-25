# Regression Baseline — Eclipse Plugin vs. Embedded Engine

**Date:** 2026-10-24
**Plugin build:** master @ `v1.0.0-installable` + Constructor Initialization fix
**Fixture:** `SmellFixture` — `testCalculator.java` paired with `Calculator.java`

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

with `baseline-input.csv` containing a single line:

```
SmellFixture,<abs path>\testCalculator.java,<abs path>\Calculator.java
```

The plugin was then run against the same two files through the Eclipse UI and
its results view compared against the generated output CSV.

### A note on the upstream CLI

The standalone `TestSmells/TestSmellDetector` repository was considered as the
baseline and rejected. Its engine has diverged from the copy embedded here: it
injects a configurable `Thresholds` object, reports smell counts rather than
booleans, and is partly implemented in Kotlin. Differences against it would
reflect engine versions rather than plugin defects, making it unusable for this
purpose without first aligning the two engines.

## Results

| # | Smell | Baseline | Plugin (before fix) | Plugin (after fix) |
|---|-------|----------|---------------------|--------------------|
| 1 | Assertion Roulette | true | detected | detected |
| 2 | Conditional Test Logic | true | detected | detected |
| 3 | Constructor Initialization | true | **not reported** | detected |
| 4 | Default Test | false | — | — |
| 5 | EmptyTest | true | detected | detected |
| 6 | Exception Catching Throwing | true | detected | detected |
| 7 | General Fixture | true | detected | detected |
| 8 | Mystery Guest | false | — | — |
| 9 | Print Statement | true | detected | detected |
| 10 | Redundant Assertion | true | detected | detected |
| 11 | Sensitive Equality | true | detected | detected |
| 12 | Verbose Test | false | — | — |
| 13 | Sleepy Test | true | detected | detected |
| 14 | Eager Test | true | detected | detected |
| 15 | Lazy Test | true | detected | detected |
| 16 | Duplicate Assert | true | detected | detected |
| 17 | Unknown Test | true | detected | detected |
| 18 | IgnoredTest | true | detected | detected |
| 19 | Resource Optimism | false | — | — |
| 20 | Magic Number Test | true | detected | detected |
| 21 | Dependent Test | false | — | — |

**Before fix:** baseline 16 / plugin 15 — one discrepancy.
**After fix:** baseline 16 / plugin 16 — full agreement across all 21 detectors.

## Discrepancy found: Constructor Initialization

### Symptom

The engine flagged Constructor Initialization for the fixture; the plugin did
not display it. No error was raised and nothing appeared in the Error Log — the
smell was silently absent from the results view.

### Root cause

`TestFile.getTestFileName()` derives the class name by splitting the file path
on backslashes only:

```java
int lastIndex = testFilePath.lastIndexOf("\\");
return testFilePath.substring(lastIndex + 1, testFilePath.length());
```

The plugin obtained its paths via `IPath.toString()`, which returns
forward-slash-separated paths (`C:/Users/.../testCalculator.java`). The
backslash search therefore returned `-1`, and the method returned the entire
path rather than the file name.

`ConstructorInitialization` only reports a smell when the constructor's name
matches the derived file name:

```java
if (n.getNameAsString().equals(testFileName)) { ... }
```

Comparing the constructor name `testCalculator` against a full path never
matched, so the smell was dropped. The CLI baseline was unaffected because its
paths came from the input CSV already in Windows form.

### Fix

`TSmellHandler.java`, lines 143–144 — replaced `toString()` with
`toOSString()`, which returns the platform-native path representation:

```java
String prodFilePath = fullPaths.getKey().getRawLocation().toOSString();
String testFilePath = fullPaths.getValue().getRawLocation().toOSString();
```

The defect originates in `TestFile.java`, an engine file that project
constraints prohibit modifying. The fix is therefore applied in the plugin
layer, supplying the engine with the path format it was written to expect.
Engine files remain unchanged.

## Related finding: platform coupling in the engine

The engine assumes Windows path separators throughout. In addition to
`getTestFileName()`, the method `getTagName()` performs
`testFilePath.split("\\\\")[4]`, and both relative-path methods assume at least
five backslash-separated segments. On macOS or Linux these would fail the same
way, and `getTagName()` would raise `ArrayIndexOutOfBoundsException` on shorter
paths.

This cannot be corrected without modifying engine files. It is recorded here as
a known portability limitation and is relevant to any cross-platform testing of
the plugin.

## Conclusion

The plugin's output now matches the embedded engine exactly across all 21
detectors for the fixture under test. One genuine plugin-layer defect was found
and corrected as a direct result of establishing this baseline.
