# Cross-IDE Parity — Eclipse vs VS Code

**Issue:** #53 — Cross-IDE: Create regression corpus and expected results
**Corpus:** `regression-corpus/` in `TestSmells/VSCode-Plugin`, pinned at
`117c2f1f585aeb97c3940ede420ad64afe9c58ae`
**Date:** October 2026

---

## Summary

Both integrations were run over the same ten-case corpus and compared against the
same `expected-results.json`.

**The two integrations do not ship the same engine**, and they disagree on 5 of the
10 cases. Every disagreement is the same detector — Magic Number Test — and in
every instance the Eclipse engine's behaviour is the defensible one.

| | |
|---|---|
| Cases where both engines agree | 5 |
| Cases where they differ | 5 |
| Detectors involved in the differences | 1 (Magic Number Test) |
| Differences caused by the Eclipse integration | 0 |

The differences are declared in `tools/corpus/known-divergences.json` and enforced
by CI, so they cannot drift unnoticed.

---

## Why the engines differ

The two repositories embed different generations of tsDetect.

| | Eclipse plugin | VS Code extension |
|---|---|---|
| Engine | Source tree inherited from the 2018 RIT plugin | Prebuilt `detector/tsdetect.jar` |
| Entry point | `main.java.Main` | `detector-runner` CLI |
| CLI options | input file only | `--file`, `--thresholds`, `--granularity`, `--output` |
| Reports test-method count | no | yes (`NumberOfMethods` column) |
| Reports `TestClass` column | no | yes |

`expected-results.json` was generated from the newer engine
(`sourceCommit e89ce1a2…`). The Eclipse plugin cannot adopt that engine: the SRS
constrains this project to leave the engine unchanged and integrate with the
current implementation. Replacing it is engine work and out of scope.

---

## Results

| Case | Manifest / pinned jar | Eclipse engine | Agree |
|---|---|---|:---:|
| 01-junit4-magic-number | Magic Number Test | Magic Number Test | yes |
| 02-junit5-magic-number | Magic Number Test | Magic Number Test | yes |
| 03-junit5-no-smell | *(none)* | *(none)* | yes |
| 04-junit4-assertion-roulette | Assertion Roulette, Eager Test, Magic Number Test | Assertion Roulette, Eager Test, Magic Number Test | yes |
| 05-junit5-unknown-test | Eager Test, Magic Number Test, Unknown Test | Eager Test, Unknown Test | no |
| 06-junit5-lazy-test | Lazy Test, Magic Number Test | Lazy Test | no |
| 07-conventional-test-name | Magic Number Test | *(none)* | no |
| 08-alternative-it-name | Magic Number Test | *(none)* | no |
| 09-malformed-input | error: malformed-input | error: `ParseProblemException` | yes |
| 10-special-path | Magic Number Test | *(none)* | no |

The manifest column and the pinned jar were confirmed to be identical by running
`detector/tsdetect.jar` directly over the same inputs, so the comparison is
between two engines rather than between an engine and a stale expectation file.

---

## Analysis of the divergence

Magic Number Test should report a test that asserts against an unexplained numeric
literal — `assertEquals(42, discount.applyTo(100))` rather than
`assertEquals(expectedDiscount, discount.applyTo(basePrice))`.

The Eclipse engine implements exactly that. `MagicNumberTest.ClassVisitor` walks
assertion calls and increments its counter only when `Util.isNumber(argument)`
holds, or when an `ObjectCreationExpr` argument wraps a number.

The newer engine reports the smell more broadly. Examining the five divergent
cases:

- **Cases 06, 07, 10** assert on string literals only — `assertEquals("ready",
  order.status())`. There is no number anywhere in the assertion.
- **Case 08** calls `assertTrue(inventory.available("book"))`. The argument is a
  boolean expression with a string literal inside it.
- **Case 05** contains **no assertion at all**. The test body calls two production
  methods and asserts nothing. The newer engine still reports Magic Number Test.

Case 05 is the clearest of the five: a smell about numeric literals in assertions
cannot meaningfully be present in a test with no assertions and no numbers. The
Eclipse engine reports nothing there, which is correct.

**This is recorded as an observation, not a defect report against the VS Code
workstream.** The newer engine is maintained upstream, the behaviour may be
deliberate under a broader definition of the smell, and neither team controls it.
What matters for this project is that the difference is understood, declared, and
will be noticed if it changes.

---

## How parity is enforced

`tools/corpus/` holds the Eclipse-side runner:

| File | Purpose |
|---|---|
| `CorpusRunner.java` | Runs the engine over a list of cases and prints one normalized line per case |
| `run-corpus.py` | Reads the shared manifest, invokes the runner, compares, and sets the exit status |
| `known-divergences.json` | Declares each accepted difference, with the Eclipse engine's actual output and the reason |
| `engine-checksums.txt` | SHA-256 of every engine source file |

`.github/workflows/eclipse-ci.yml` runs this on every pull request and push to
`master`.

The runner fails the build when:

- a case differs from the manifest and is **not** declared in
  `known-divergences.json`;
- a declared divergence produces output other than what the declaration states;
- a declared divergence **stops** diverging, which means the declaration is stale
  and should be removed;
- the engine produces no result for a case.

The third condition is what keeps this document honest over time. A declaration
cannot quietly outlive the behaviour it describes.

The corpus repository is checked out at a pinned commit. A change to the shared
corpus cannot alter this repository's CI results until the pin is deliberately
moved.

### Engine integrity

The workflow verifies every engine source file against `engine-checksums.txt`
before compiling. The SRS constraint that the engine remain unchanged is therefore
enforced mechanically rather than by review alone: any edit to
`src/main/java/testsmell/*.java` or `src/main/java/testsmell/smell/**` fails the
build.

Regenerate the file only when the engine is intentionally replaced:

```
find src/main/java -name "*.java" -not -path "*/plugin/*" | sort \
  | xargs sha256sum > tools/corpus/engine-checksums.txt
```

---

## Fields not compared

**`testMethods`.** The manifest records a test-method count for every successful
case, taken from the newer engine's `NumberOfMethods` column. The engine in this
repository has no equivalent output, so the Eclipse runner does not compare it.
Deriving the count independently would compare this project's own parsing against
the newer engine's rather than comparing the two integrations, which is not what
the criterion asks for.

This is recorded in the `notComparedFields` section of
`tools/corpus/known-divergences.json`.

---

## Running it locally

From the repository root, with the VS Code repository checked out alongside it:

```
mkdir -p build/corpus-classes
javac -nowarn -d build/corpus-classes \
  -cp "lib/javaparser-core-3.2.4.jar:lib/commons-lang3-3.18.0.jar" \
  $(find src/main/java -name '*.java' -not -path '*/plugin/*')
javac -nowarn -d build/corpus-classes \
  -cp "lib/javaparser-core-3.2.4.jar:lib/commons-lang3-3.18.0.jar:build/corpus-classes" \
  tools/corpus/CorpusRunner.java
python3 tools/corpus/run-corpus.py \
  --corpus ../VSCode-Plugin/regression-corpus \
  --classes build/corpus-classes \
  --lib lib
```

On Windows, use `;` as the classpath separator.

Note that the engine compiles with plain `javac` and needs no Eclipse
installation. Only `src/main/java/testsmell/plugin/**` depends on the Eclipse
platform, which is why corpus verification can run in CI while packaging the
bundle still cannot.

---

## What this does not cover

**The Eclipse UI layer is not exercised.** The corpus verifies that the engine the
plugin embeds produces the expected results. It does not drive the handler, the
results view, markers or preferences, because those require a running Eclipse
workbench. End-to-end behaviour in the IDE is covered by the manual verification
recorded in #44 and by `documentation/regression-baseline.md`.

**The bundle is not built in CI.** `pom.xml` is the standalone CLI's POM, inherited
at import, with no Tycho configuration. Packaging the plugin and publishing the p2
update site remain manual Export steps, documented in `README.md`. See #16.
