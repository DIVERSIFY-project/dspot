package eu.stamp_project.dspot;

import eu.stamp_project.automaticbuilder.AutomaticBuilder;
import eu.stamp_project.dspot.input_ampl_distributor.InputAmplDistributor;
import eu.stamp_project.dspot.selector.TestSelector;
import eu.stamp_project.utils.compilation.DSpotCompiler;
import eu.stamp_project.utils.compilation.TestCompiler;
import eu.stamp_project.utils.configuration.AmplificationSetup;
import eu.stamp_project.utils.configuration.DSpotConfiguration;
import eu.stamp_project.utils.configuration.TestTuple;
import eu.stamp_project.utils.program.InputConfiguration;
import eu.stamp_project.utils.report.GlobalReport;
import eu.stamp_project.utils.report.error.Error;
import eu.stamp_project.utils.report.output.Output;
import eu.stamp_project.utils.test_finder.TestFinder;
import org.slf4j.Logger;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static eu.stamp_project.utils.report.error.ErrorEnum.ERROR_ASSERT_AMPLIFICATION;
import static eu.stamp_project.utils.report.error.ErrorEnum.ERROR_INPUT_AMPLIFICATION;
import static eu.stamp_project.utils.report.error.ErrorEnum.ERROR_SELECTION;

/**
 * User: Simon
 * Date: 08/06/15
 * Time: 17:36
 */
public class DSpot {

    private DSpotConfiguration configuration;
    private AmplificationSetup setup;
    private int globalNumberOfSelectedAmplification;
    private final Logger LOGGER;
    private final GlobalReport GLOBAL_REPORT;

    public DSpot(InputConfiguration inputConfiguration){
        configuration = new DSpotConfiguration(inputConfiguration);
        setup = new AmplificationSetup(configuration);
        LOGGER = configuration.getLogger();
        globalNumberOfSelectedAmplification = 0;
        GLOBAL_REPORT = configuration.getGlobalReport();
    }

    public DSpot(double delta,
                 TestFinder testFinder,
                 DSpotCompiler compiler,
                 TestSelector testSelector,
                 InputAmplDistributor inputAmplDistributor,
                 Output output,
                 int numberOfIterations,
                 boolean shouldGenerateAmplifiedTestClass,
                 AutomaticBuilder automaticBuilder,
                 TestCompiler testCompiler) {
        configuration = new DSpotConfiguration();
        configuration.getInputConfiguration().setDelta(delta);
        configuration.setTestSelector(testSelector);
        configuration.setInputAmplDistributor(inputAmplDistributor);
        configuration.getInputConfiguration().setNbIteration(numberOfIterations);
        configuration.setTestFinder(testFinder);
        configuration.setCompiler(compiler);
        configuration.setOutput(output);
        configuration.getInputConfiguration().setGenerateAmplifiedTestClass(shouldGenerateAmplifiedTestClass);
        configuration.setAutomaticBuilder(automaticBuilder);
        configuration.setTestCompiler(testCompiler);
        setup = new AmplificationSetup(configuration);
        LOGGER = configuration.getLogger();
        globalNumberOfSelectedAmplification = 0;
        GLOBAL_REPORT = configuration.getGlobalReport();
    }

    public DSpot(DSpotConfiguration configuration) {
        this.configuration = configuration;
        setup = new AmplificationSetup(configuration);
        LOGGER = configuration.getLogger();
        globalNumberOfSelectedAmplification = 0;
        GLOBAL_REPORT = configuration.getGlobalReport();
    }

    // todo merge run, amplify and amplify
    public void run() {
        final List<CtType<?>> amplifiedTestClasses = amplify(configuration.getTestClassesToBeAmplified(),
                configuration.getTestMethodsToBeAmplifiedNames());
        configuration.report(amplifiedTestClasses);
    }

    public CtType<?> amplify(CtType<?> testClassToBeAmplified, List<String> testMethodsToBeAmplifiedAsString) {
        return this.amplify(Collections.singletonList(testClassToBeAmplified), testMethodsToBeAmplifiedAsString).get(0);
    }

    public List<CtType<?>> amplify(List<CtType<?>> testClassesToBeAmplified, List<String> testMethodsToBeAmplifiedAsString) {
        for (CtType<?> testClassToBeAmplified : testClassesToBeAmplified) {
            TestTuple tuple = setup.preAmplification(testClassToBeAmplified,testMethodsToBeAmplifiedAsString);
            final List<CtMethod<?>> amplifiedTestMethods = amplification(tuple.testClassToBeAmplified,tuple.testMethodsToBeAmplified);
            setup.postAmplification(testClassToBeAmplified,amplifiedTestMethods);
            globalNumberOfSelectedAmplification = 0;
        }
        return setup.getAmplifiedTestClasses();
    }

    public List<CtMethod<?>>  amplification(CtType<?> testClassToBeAmplified, List<CtMethod<?>> testMethodsToBeAmplified) {
        final List<CtMethod<?>> amplifiedTestMethodsToKeep = onlyAssertionGeneration(testClassToBeAmplified,testMethodsToBeAmplified);
        if (configuration.getInputAmplDistributor().shouldBeRun()) {
            fullyAmplifyAllMethods(testClassToBeAmplified,testMethodsToBeAmplified,amplifiedTestMethodsToKeep);
        }
        return amplifiedTestMethodsToKeep;
    }

    public List<CtMethod<?>> onlyAssertionGeneration(CtType<?> testClassToBeAmplified, List<CtMethod<?>> testMethodsToBeAmplified){
        final List<CtMethod<?>> selectedToBeAmplified;
        final List<CtMethod<?>> amplifiedTestMethodsToKeep;
        try {
            selectedToBeAmplified = setup.firstSelectorSetup(testClassToBeAmplified,testMethodsToBeAmplified);

            // generate tests with additional assertions
            final List<CtMethod<?>> assertionAmplifiedTestMethods = this.assertionAmplification(testClassToBeAmplified,
                    selectedToBeAmplified);

            // keep tests that improve the test suite
            amplifiedTestMethodsToKeep = selectOnlyAssertionGeneration(assertionAmplifiedTestMethods);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        return amplifiedTestMethodsToKeep;
    }

    // iteratively generate tests with input modification and associated new assertions for all methods
    private void fullyAmplifyAllMethods(CtType<?> testClassToBeAmplified,List<CtMethod<?>> testMethodsToBeAmplified,
                                        List<CtMethod<?>> amplifiedTestMethodsToKeep){
        LOGGER.info("Applying Input-amplification and Assertion-amplification test by test.");
        for (int i = 0; i < testMethodsToBeAmplified.size(); i++) {
            CtMethod test = testMethodsToBeAmplified.get(i);
            LOGGER.info("Amplification of {}, ({}/{})", test.getSimpleName(), i + 1, testMethodsToBeAmplified.size());

            // tmp list for current test methods to be amplified
            // this list must be a implementation that support remove / clear methods
            List<CtMethod<?>> currentTestList = new ArrayList<>();
            currentTestList.add(test);
            final List<CtMethod<?>> amplifiedTests = new ArrayList<>();
            for (int j = 0; j < configuration.getInputConfiguration().getNbIteration() ; j++) {
                LOGGER.info("iteration {} / {}", j, configuration.getInputConfiguration().getNbIteration());
                currentTestList = this.fullAmplification(testClassToBeAmplified, currentTestList, amplifiedTests, j);
            }
            amplifiedTestMethodsToKeep.addAll(amplifiedTests);
            this.globalNumberOfSelectedAmplification += amplifiedTestMethodsToKeep.size();
            LOGGER.info("{} amplified test methods has been selected to be kept. (global: {})", amplifiedTests.size(),
                    this.globalNumberOfSelectedAmplification);
        }
    }

    /**
     * Amplification of test methods
     *
     * DSpot combines the different kinds of I-Amplification iteratively: at each iteration all kinds of
     * I-Amplification are applied, resulting in new tests. From one iteration to another, DSpot reuses the
     * previously amplified tests, and further applies I-Amplification.
     *
     * @param testClassToBeAmplified        Test class
     * @param currentTestListToBeAmplified  Methods to amplify
     * @return Valid amplified tests
     */
    public List<CtMethod<?>> fullAmplification(CtType<?> testClassToBeAmplified,
                                               List<CtMethod<?>> currentTestListToBeAmplified,
                                               List<CtMethod<?>> amplifiedTests,
                                               int currentIteration) {
        final List<CtMethod<?>> selectedToBeAmplified;
        final List<CtMethod<?>> inputAmplifiedTests;
        final List<CtMethod<?>> currentTestList;
        try {
            selectedToBeAmplified = setup.fullSelectorSetup(testClassToBeAmplified,currentTestListToBeAmplified);

            // amplify tests and shrink amplified set with inputAmplDistributor
            inputAmplifiedTests = configuration.getInputAmplDistributor().inputAmplify(selectedToBeAmplified, currentIteration);

            // add assertions to input modified tests
            currentTestList = this.assertionAmplification(testClassToBeAmplified, inputAmplifiedTests);

            // keep tests that improve the test suite
            selectFullAmplification(currentTestList,amplifiedTests);
        } catch (AmplificationException e) {
            return Collections.emptyList();
        } catch (Exception | java.lang.Error e) {
            GLOBAL_REPORT.addError(new Error(ERROR_INPUT_AMPLIFICATION, e));
            return Collections.emptyList();
        }
        return currentTestList;
    }

    public List<CtMethod<?>> assertionAmplification(CtType<?> classTest, List<CtMethod<?>> testMethods) {
        final List<CtMethod<?>> testsWithAssertions;
        try {
            testsWithAssertions = configuration.getAssertionGenerator().assertionAmplification(classTest, testMethods);
        } catch (Exception | java.lang.Error e) {
            GLOBAL_REPORT.addError(new Error(ERROR_ASSERT_AMPLIFICATION, e));
            return Collections.emptyList();
        }
        if (testsWithAssertions.isEmpty()) {
            return testsWithAssertions;
        }

        // final check on A-amplified test, see if they all pass. if they don't, we just discard them.
        final List<CtMethod<?>> amplifiedPassingTests =
                configuration.getTestCompiler().compileRunAndDiscardUncompilableAndFailingTestMethods(
                        classTest,
                        testsWithAssertions,
                        configuration.getCompiler()
                );
        LOGGER.info("Assertion amplification: {} test method(s) has been successfully amplified.",
                amplifiedPassingTests.size());
        return amplifiedPassingTests;
    }

    private List<CtMethod<?>>  selectOnlyAssertionGeneration(List<CtMethod<?>> assertionAmplifiedTestMethods)
            throws Exception {
        final List<CtMethod<?>> amplifiedTestMethodsToKeep;
        try {
            amplifiedTestMethodsToKeep = configuration.getTestSelector().selectToKeep(assertionAmplifiedTestMethods);
        } catch (Exception | java.lang.Error e) {
            GLOBAL_REPORT.addError(new Error(ERROR_SELECTION, e));
            throw new Exception();
        }
        this.globalNumberOfSelectedAmplification += amplifiedTestMethodsToKeep.size();
        LOGGER.info("{} amplified test methods has been selected to be kept. (global: {})",
                amplifiedTestMethodsToKeep.size(), this.globalNumberOfSelectedAmplification);

        return amplifiedTestMethodsToKeep;
    }

    private void selectFullAmplification(List<CtMethod<?>> currentTestList,List<CtMethod<?>> amplifiedTests)
            throws AmplificationException {
        final List<CtMethod<?>> amplifiedTestMethodsToKeep;
        try {
            amplifiedTestMethodsToKeep = configuration.getTestSelector().selectToKeep(currentTestList);
        } catch (Exception | java.lang.Error e) {
            GLOBAL_REPORT.addError(new Error(ERROR_SELECTION, e));
            throw new AmplificationException("");
        }
        LOGGER.info("{} amplified test methods has been selected to be kept.", amplifiedTestMethodsToKeep.size());
        amplifiedTests.addAll(amplifiedTestMethodsToKeep);
    }
}
