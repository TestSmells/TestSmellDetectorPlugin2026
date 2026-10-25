package main.java.testsmell.plugin.markers;

import java.util.HashMap;
import java.util.Map;

/**
 * Human-readable explanation and fix suggestion for each test smell.
 *
 * These strings form the marker message, which Eclipse also displays as
 * hover text over the marker in the editor.
 */
public final class SmellDescriptions {

	private static final Map<String, String> EXPLANATIONS = new HashMap<String, String>();
	private static final Map<String, String> SUGGESTIONS = new HashMap<String, String>();

	static {
		EXPLANATIONS.put("Assertion Roulette",
				"Multiple assertions in one test with no explanation messages, so a failure does not say which check broke.");
		SUGGESTIONS.put("Assertion Roulette",
				"Give each assertion a descriptive message, or split the test into separate cases.");
		EXPLANATIONS.put("Conditional Test Logic",
				"The test contains control flow, so not every statement runs on every execution and a defect can slip past.");
		SUGGESTIONS.put("Conditional Test Logic",
				"Remove the branching, or split each path into its own test.");
		EXPLANATIONS.put("Constructor Initialization",
				"The test class declares a constructor. Test fixtures should be prepared by the framework, not a constructor.");
		SUGGESTIONS.put("Constructor Initialization",
				"Move the initialization into a @Before (JUnit 4) or @BeforeEach (JUnit 5) method.");
		EXPLANATIONS.put("Default Test",
				"This is a generated example test class that was never renamed or removed.");
		SUGGESTIONS.put("Default Test",
				"Delete it, or rename it to describe what it actually tests.");
		EXPLANATIONS.put("EmptyTest",
				"The test method has no executable statements, so it always passes and verifies nothing.");
		SUGGESTIONS.put("EmptyTest",
				"Implement the test, or remove it rather than leaving a false pass.");
		EXPLANATIONS.put("Exception Catching Throwing",
				"The test handles exceptions itself instead of letting the framework decide pass or fail.");
		SUGGESTIONS.put("Exception Catching Throwing",
				"Use assertThrows, or declare the exception on the method signature.");
		EXPLANATIONS.put("General Fixture",
				"The setup method initializes fields that this test never uses, so it does work that is thrown away.");
		SUGGESTIONS.put("General Fixture",
				"Move the unused initialization into only the tests that need it.");
		EXPLANATIONS.put("Mystery Guest",
				"The test depends on an external resource such as a file, database, or network call, making it slow and unreliable.");
		SUGGESTIONS.put("Mystery Guest",
				"Replace the external resource with a mock or a stub.");
		EXPLANATIONS.put("Print Statement",
				"The test writes to the console. Tests normally run unattended, so the output is never read.");
		SUGGESTIONS.put("Print Statement",
				"Remove the print, or replace it with an assertion that actually checks the value.");
		EXPLANATIONS.put("Redundant Assertion",
				"The assertion compares a value to itself, so it can never fail and tests nothing.");
		SUGGESTIONS.put("Redundant Assertion",
				"Assert against the expected value, or delete the assertion.");
		EXPLANATIONS.put("Sensitive Equality",
				"The test asserts against toString() output, so an unrelated formatting change will break it.");
		SUGGESTIONS.put("Sensitive Equality",
				"Compare the specific fields you care about instead.");
		EXPLANATIONS.put("Verbose Test",
				"The test method is extremely long, which makes it hard to tell what is being verified.");
		SUGGESTIONS.put("Verbose Test",
				"Split it into several focused tests.");
		EXPLANATIONS.put("Sleepy Test",
				"The test pauses the thread to wait for something, which is slow and fails unpredictably on different machines.");
		SUGGESTIONS.put("Sleepy Test",
				"Wait on the actual condition, or use the framework's async support.");
		EXPLANATIONS.put("Eager Test",
				"The test exercises several production methods at once, so a failure does not identify which one is broken.");
		SUGGESTIONS.put("Eager Test",
				"Test one method per test case.");
		EXPLANATIONS.put("Lazy Test",
				"Several tests exercise the same production method, which duplicates effort and obscures intent.");
		SUGGESTIONS.put("Lazy Test",
				"Merge them, or make each one clearly cover a distinct case.");
		EXPLANATIONS.put("Duplicate Assert",
				"The same assertion appears more than once in this test, so the repeat adds nothing.");
		SUGGESTIONS.put("Duplicate Assert",
				"Remove the duplicate, or use different inputs and a descriptive test name.");
		EXPLANATIONS.put("Unknown Test",
				"The test has no assertion, so it only fails if something throws. Its purpose is unclear.");
		SUGGESTIONS.put("Unknown Test",
				"Add an assertion stating what the test is meant to verify.");
		EXPLANATIONS.put("IgnoredTest",
				"The test is annotated to be skipped, so it still costs compilation time while verifying nothing.");
		SUGGESTIONS.put("IgnoredTest",
				"Fix and re-enable it, or delete it.");
		EXPLANATIONS.put("Resource Optimism",
				"The test assumes an external file exists without checking first, so it fails confusingly when the file is absent.");
		SUGGESTIONS.put("Resource Optimism",
				"Check the resource exists, or supply it as part of the fixture.");
		EXPLANATIONS.put("Magic Number Test",
				"The assertion uses a bare numeric literal, which does not explain what the number represents.");
		SUGGESTIONS.put("Magic Number Test",
				"Replace it with a named constant or variable.");
		EXPLANATIONS.put("Dependent Test",
				"The test depends on another test having run first, so it fails when tests run in a different order.");
		SUGGESTIONS.put("Dependent Test",
				"Make the test independent of execution order.");
	}

	private SmellDescriptions() {
	}

	/**
	 * Builds the marker message for a smell.
	 *
	 * @param smellName the smell's display name
	 * @param elementName the method or class carrying the smell
	 * @return a message combining the smell, its explanation, and a suggestion
	 */
	public static String message(String smellName, String elementName) {
		String explanation = EXPLANATIONS.get(smellName);
		String suggestion = SUGGESTIONS.get(smellName);
		StringBuilder sb = new StringBuilder();
		sb.append(smellName);
		if (elementName != null && !elementName.trim().isEmpty()) {
			sb.append(" in ").append(elementName.trim());
		}
		if (explanation != null) {
			sb.append(" \u2014 ").append(explanation);
		}
		if (suggestion != null) {
			sb.append(" ").append(suggestion);
		}
		return sb.toString();
	}
}
