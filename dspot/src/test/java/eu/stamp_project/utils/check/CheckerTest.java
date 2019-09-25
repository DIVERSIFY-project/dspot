package eu.stamp_project.utils.check;

import eu.stamp_project.Main;
import eu.stamp_project.utils.options.check.Checker;
import eu.stamp_project.utils.options.check.InputErrorException;
import org.junit.Test;
import spoon.testing.utils.Check;

import static org.junit.Assert.*;

/**
 * created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 22/11/18
 */
public class CheckerTest {

    @Test
    public void testJVMArgs() {
        assertTrue(Checker.checkJVMArgs("-Xms1024M"));
        assertTrue(Checker.checkJVMArgs("-Xms1024m"));
        assertTrue(Checker.checkJVMArgs("-Xms1G"));
        assertTrue(Checker.checkJVMArgs("-Xms1g"));

        assertTrue(Checker.checkJVMArgs("-Xmx1024M"));
        assertTrue(Checker.checkJVMArgs("-Xmx1024m"));
        assertTrue(Checker.checkJVMArgs("-Xmx1G"));
        assertTrue(Checker.checkJVMArgs("-Xmx1g"));

        assertTrue(Checker.checkJVMArgs("-Daproperty=3"));

        assertFalse(Checker.checkJVMArgs("-Daproperty3"));
        assertFalse(Checker.checkJVMArgs("-aproperty=3"));
        assertFalse(Checker.checkJVMArgs("-Xmx1x"));
        assertFalse(Checker.checkJVMArgs("-Xms1x"));
    }

    @Test
    public void testCheckVersion() {
        Checker.checkIsACorrectVersion("1");
        Checker.checkIsACorrectVersion("10");
        Checker.checkIsACorrectVersion("10.1");
        Checker.checkIsACorrectVersion("10.10");

        Checker.checkIsACorrectVersion("1.1.1");
        Checker.checkIsACorrectVersion("1.1.10");
        Checker.checkIsACorrectVersion("1.10.10");
        Checker.checkIsACorrectVersion("10.1.10");
        Checker.checkIsACorrectVersion("10.10.10");

        //Version with snapshot
        Checker.checkIsACorrectVersion("1.2.5-SNAPSHOT");

        try {
            Checker.checkIsACorrectVersion("1.");
            fail("should have thrown InputErrorException");
        } catch (InputErrorException e) {
            // expected
        }

        try {
            Checker.checkIsACorrectVersion("a.");
            fail("should have thrown InputErrorException");
        } catch (InputErrorException e) {
            // expected
        }

        try {
            Checker.checkIsACorrectVersion("1.a");
            fail("should have thrown InputErrorException");
        } catch (InputErrorException e) {
            // expected
        }

        try {
            Checker.checkIsACorrectVersion("b");
            fail("should have thrown InputErrorException");
        } catch (InputErrorException e) {
            // expected
        }
    }

    @Test
    public void testWrongPathToProperties() {
        try {
            Main.main(new String[0]);
            fail();
        } catch (InputErrorException e) {
            assertEquals("Error in the provided input. Please check your properties file and your command-line options.", e.getMessage());
        }
    }

    @Test
    public void testNoCorrectValueForAmplifiers() {
        try {
            Main.main(new String[] {
                    "--path-to-properties", "src/test/resources/test-projects/test-projects.properties",
                    "--amplifiers", "NotAnAmplifier:NotAnotherAmplifier"
            });
            fail();
        } catch (InputErrorException e) {
            assertEquals("Error in the provided input. Please check your properties file and your command-line options.", e.getMessage());
        }
    }

    @Test
    public void testNoCorrectValueForBudgetizer() {
        try {
            Main.main(new String[] {
                    "--path-to-properties", "src/test/resources/test-projects/test-projects.properties",
                    "--input-ampl-distributor", "NotABudgetizer"
            });
            fail();
        } catch (InputErrorException e) {
            assertEquals("Error in the provided input. Please check your properties file and your command-line options.", e.getMessage());
        }
    }

    @Test
    public void testNoCorrectValueForTestCriterion() {
        try {
            Main.main(new String[] {
                    "--path-to-properties", "src/test/resources/test-projects/test-projects.properties",
                    "--test-criterion", "NotASelector"
            });
            fail();
        } catch (InputErrorException e) {
            assertEquals("Error in the provided input. Please check your properties file and your command-line options.", e.getMessage());
        }
    }
}
