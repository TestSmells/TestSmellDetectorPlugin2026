#!/usr/bin/env python3
"""Run the shared cross-IDE regression corpus against this repository's engine.

The corpus lives in the VS Code repository. This script does not copy it; it is
given a path to a checkout and compares results against the same
``expected-results.json`` the VS Code runner uses.

Where this engine is known to disagree with the newer engine the manifest was
generated from, the difference must be declared in ``known-divergences.json``.
The run fails if a case diverges without a declaration, and also fails if a
declared divergence has disappeared, so the declarations cannot go stale.

Usage:
    python3 tools/corpus/run-corpus.py \\
        --corpus      ../VSCode-Plugin/regression-corpus \\
        --classes     build/corpus-classes \\
        --lib         lib

Exit status is 0 only when every case either matches or is a declared
divergence.
"""

import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DIVERGENCES = os.path.join(HERE, "known-divergences.json")


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", required=True,
                        help="Path to the regression-corpus directory in the VS Code repository")
    parser.add_argument("--classes", required=True,
                        help="Directory holding the compiled engine and CorpusRunner classes")
    parser.add_argument("--lib", required=True,
                        help="Directory holding javaparser and commons-lang3 jars")
    parser.add_argument("--divergences", default=DEFAULT_DIVERGENCES,
                        help="Path to known-divergences.json")
    parser.add_argument("--java", default=os.environ.get("JAVA_PATH", "java"),
                        help="Java executable to use")
    return parser.parse_args()


def load_json(path, label):
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except OSError as error:
        fail_setup(f"Cannot read {label}: {error}")
    except json.JSONDecodeError as error:
        fail_setup(f"{label} is not valid JSON: {error}")


def fail_setup(message):
    print(f"Corpus setup error: {message}", file=sys.stderr)
    sys.exit(2)


def classpath(classes, lib):
    jars = [os.path.join(lib, name)
            for name in sorted(os.listdir(lib))
            if name.endswith(".jar")]
    if not jars:
        fail_setup(f"No jars found in {lib}")
    return os.pathsep.join([classes] + jars)


def run_engine(args, manifest):
    invocation = [args.java, "-cp", classpath(args.classes, args.lib), "CorpusRunner"]
    for case in manifest["cases"]:
        invocation += [
            case["id"],
            os.path.join(args.corpus, case["testFile"]),
            os.path.join(args.corpus, case["productionFile"]),
        ]

    completed = subprocess.run(invocation, capture_output=True, text=True)
    if completed.returncode != 0:
        fail_setup(f"CorpusRunner exited {completed.returncode}:\n{completed.stderr}")

    results = {}
    for line in completed.stdout.splitlines():
        if "\t" not in line:
            continue
        parts = line.split("\t")
        status = parts[1]
        detail = parts[2] if len(parts) > 2 else ""
        smells = [s for s in detail.split("|") if s] if status == "success" else []
        results[parts[0]] = {"status": status, "smells": smells, "detail": detail}
    return results


def compare(case, actual, declared):
    """Return (outcome, message). Outcome is PASS, KNOWN, STALE or FAIL."""
    expected = case["expected"]
    case_id = case["id"]

    if expected["status"] != actual["status"]:
        return "FAIL", (f"expected status {expected['status']}, "
                        f"received {actual['status']} ({actual['detail']})")

    if expected["status"] == "error":
        for fragment in expected.get("messageContains", []):
            if fragment not in actual["detail"]:
                return "FAIL", (f"expected the error to mention {fragment!r}, "
                                f"received {actual['detail']!r}")
        return "PASS", ""

    matches_manifest = expected["smells"] == actual["smells"]
    divergence = declared.get(case_id)

    if matches_manifest and divergence is None:
        return "PASS", ""

    if matches_manifest and divergence is not None:
        return "STALE", ("this case now matches the shared manifest; remove its entry "
                         "from known-divergences.json")

    if divergence is None:
        return "FAIL", (f"expected {expected['smells']}, received {actual['smells']} "
                        f"— declare it in known-divergences.json if intentional")

    if divergence["eclipseSmells"] != actual["smells"]:
        return "FAIL", (f"declared divergence expects {divergence['eclipseSmells']}, "
                        f"received {actual['smells']}")

    return "KNOWN", divergence["reason"]


def main():
    args = parse_args()

    manifest_path = os.path.join(args.corpus, "expected-results.json")
    manifest = load_json(manifest_path, "the corpus manifest")
    if manifest.get("schemaVersion") != 1:
        fail_setup("The corpus manifest does not use the supported schema version.")
    if len(manifest.get("cases", [])) < 10:
        fail_setup("The corpus manifest must contain at least 10 fixtures.")

    divergence_file = load_json(args.divergences, "the divergence declarations")
    declared = {entry["caseId"]: entry
                for entry in divergence_file.get("divergences", [])}

    unknown = set(declared) - {case["id"] for case in manifest["cases"]}
    if unknown:
        fail_setup("known-divergences.json names cases that are not in the corpus: "
                   + ", ".join(sorted(unknown)))

    actual = run_engine(args, manifest)

    tally = {"PASS": 0, "KNOWN": 0, "STALE": 0, "FAIL": 0}
    problems = []

    for case in manifest["cases"]:
        case_id = case["id"]
        if case_id not in actual:
            tally["FAIL"] += 1
            problems.append((case_id, "the engine produced no result for this case"))
            print(f"FAIL  {case_id}")
            print("        the engine produced no result for this case")
            continue

        outcome, message = compare(case, actual[case_id], declared)
        tally[outcome] += 1
        print(f"{outcome:<5} {case_id}")
        if message:
            print(f"        {message}")
        if outcome in ("FAIL", "STALE"):
            problems.append((case_id, message))

    total = len(manifest["cases"])
    print()
    print(f"{tally['PASS']} matched, {tally['KNOWN']} declared divergences, "
          f"{tally['FAIL']} unexpected, {tally['STALE']} stale — {total} cases total")

    if problems:
        print()
        print("The corpus comparison failed:", file=sys.stderr)
        for case_id, message in problems:
            print(f"  {case_id}: {message}", file=sys.stderr)
        return 1

    print("Engine behaviour matches the shared corpus, "
          "allowing for the declared divergences.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
