import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import main.java.testsmell.AbstractSmell;
import main.java.testsmell.TestFile;
import main.java.testsmell.TestSmellDetector;

/**
 * Runs the shared cross-IDE regression corpus against the engine embedded in the
 * Eclipse plugin repository, and prints one normalized result per case.
 *
 * <p>Output format, one line per case:
 *
 * <pre>
 * &lt;caseId&gt;\tsuccess\t&lt;smell&gt;|&lt;smell&gt;|...
 * &lt;caseId&gt;\terror\t&lt;exception class name&gt;
 * </pre>
 *
 * <p>Smell names are de-duplicated and sorted, matching the normalization the
 * VS Code corpus runner applies. The comparison against
 * {@code expected-results.json} is performed by the calling script.
 *
 * <p>This class is a test harness. It reads the engine's public API and does not
 * modify it, as required by the SRS constraint that the engine remain unchanged.
 */
public final class CorpusRunner {

    public static void main(String[] args) {
        if (args.length == 0 || args.length % 3 != 0) {
            System.err.println("Usage: CorpusRunner <caseId> <testFile> <productionFile> [...]");
            System.exit(2);
        }

        for (int i = 0; i < args.length; i += 3) {
            String caseId = args[i];
            String testFilePath = args[i + 1];
            String productionFilePath = args[i + 2];
            System.out.println(analyze(caseId, testFilePath, productionFilePath));
        }
    }

    private static String analyze(String caseId, String testFilePath, String productionFilePath) {
        try {
            TestSmellDetector detector = TestSmellDetector.createTestSmellDetector();
            TestFile testFile = new TestFile(caseId, testFilePath, productionFilePath);
            TestFile analyzed = detector.detectSmells(testFile);

            Set<String> smells = new TreeSet<String>();
            for (AbstractSmell smell : analyzed.getTestSmells()) {
                try {
                    if (smell.getHasSmell()) {
                        smells.add(smell.getSmellName());
                    }
                } catch (NullPointerException ignored) {
                    // A detector that failed to populate its state reports nothing,
                    // matching how Main treats the same condition.
                }
            }

            List<String> ordered = new ArrayList<String>(smells);
            return caseId + "\tsuccess\t" + String.join("|", ordered);
        } catch (Throwable error) {
            Throwable root = error;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return caseId + "\terror\t" + root.getClass().getName();
        }
    }

    private CorpusRunner() {
    }
}
