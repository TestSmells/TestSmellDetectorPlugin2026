import static org.junit.Assert.*;
import java.io.File;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Fixture for validating tsDetect Eclipse plugin output.
 * Each method is annotated with the smell(s) it is expected to trigger.
 *
 * NOTE: filename must be testCalculator.java (not CalculatorTest.java).
 * The plugin strips the first 4 chars of the test filename to find the
 * production file, so testCalculator.java -> Calculator.java.
 */
public class testCalculator {

    private Calculator calc;
    private String neverUsed;

    // EXPECTED: General Fixture (neverUsed initialized here, never read in a test)
    @Before
    public void setUp() {
        calc = new Calculator();
        neverUsed = "not accessed by any test";
    }

    // EXPECTED: Constructor Initialization
    public testCalculator() {
        calc = new Calculator();
    }

    // EXPECTED: Assertion Roulette (multiple undocumented asserts), Magic Number Test
    @Test
    public void testAddition() {
        assertEquals(4, calc.add(2, 2));
        assertEquals(6, calc.add(3, 3));
        assertEquals(10, calc.add(5, 5));
    }

    // EXPECTED: Duplicate Assert
    @Test
    public void testDuplicateAssertion() {
        assertEquals(4, calc.add(2, 2));
        assertEquals(4, calc.add(2, 2));
    }

    // EXPECTED: Redundant Print
    @Test
    public void testWithPrintStatement() {
        System.out.println("running the addition test");
        assertTrue(calc.add(1, 1) == 2);
    }

    // EXPECTED: Sleepy Test
    @Test
    public void testSleepy() throws InterruptedException {
        Thread.sleep(1000);
        assertNotNull(calc);
    }

    // EXPECTED: Empty Test
    @Test
    public void testEmptyBody() {
    }

    // EXPECTED: Conditional Test Logic
    @Test
    public void testConditionalLogic() {
        if (calc.add(1, 1) == 2) {
            assertTrue(true);
        } else {
            fail("addition broken");
        }
    }

    // EXPECTED: Exception Catching & Throwing
    @Test
    public void testExceptionHandling() {
        try {
            calc.add(1, 1);
        } catch (Exception e) {
            fail("should not throw");
        }
    }

    // EXPECTED: Ignored Test
    @Ignore
    @Test
    public void testIgnoredCase() {
        assertEquals(2, calc.add(1, 1));
    }

    // EXPECTED: Unknown Test (no assertion at all)
    @Test
    public void testNoAssertion() {
        calc.add(1, 1);
    }

    // EXPECTED: Sensitive Equality (toString in assertion)
    @Test
    public void testSensitiveEquality() {
        assertEquals("Calculator", calc.describe().toString());
    }

    // EXPECTED: Eager Test (calls several production methods)
    @Test
    public void testEagerMultipleMethods() {
        assertEquals(4, calc.add(2, 2));
        assertEquals(0, calc.subtract(2, 2));
        assertEquals(4, calc.multiply(2, 2));
    }

    // EXPECTED: Mystery Guest
    // A variable of an external-resource type (File) declared inside a test.
    @Test
    public void testMysteryGuest() {
        File inputData = new File("test-data.txt");
        assertNotNull(inputData);
    }

    // EXPECTED: Resource Optimism
    // An inline "new File(...)" that is NOT assigned to a variable.
    // The assigned-variable path in the engine's analyzer is broken
    // (it compares a Type object to a String), so only this form fires.
    @Test
    public void testResourceOptimism() {
        assertTrue(new File("config.properties").canRead());
    }
}

/**
 * EXPECTED: Default Test
 *
 * The analyzer fires on any class literally named ExampleUnitTest or
 * ExampleInstrumentedTest (Android Studio's generated names). A secondary,
 * non-public class in the same file is enough to trigger it, which avoids
 * adding a file that would break the plugin's testX/X pairing convention.
 */
class ExampleUnitTest {

    @Test
    public void testGeneratedExample() {
        assertTrue(true);
    }
}
