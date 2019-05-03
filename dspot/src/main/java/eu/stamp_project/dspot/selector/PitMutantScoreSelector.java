package eu.stamp_project.dspot.selector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.stamp_project.testrunner.listener.pit.AbstractParser;
import eu.stamp_project.testrunner.listener.pit.AbstractPitResult;
import eu.stamp_project.testrunner.listener.pit.PitCSVResultParser;
import eu.stamp_project.testrunner.listener.pit.PitXMLResultParser;
import eu.stamp_project.utils.execution.PitRunner;
import eu.stamp_project.test_framework.TestFramework;
import eu.stamp_project.utils.compilation.DSpotCompiler;
import eu.stamp_project.utils.AmplificationHelper;
import eu.stamp_project.utils.report.output.selector.TestSelectorElementReport;
import eu.stamp_project.utils.report.output.selector.TestSelectorElementReportImpl;
import eu.stamp_project.utils.report.output.selector.mutant.json.MutantJSON;
import eu.stamp_project.utils.report.output.selector.mutant.json.TestCaseJSON;
import eu.stamp_project.utils.report.output.selector.mutant.json.TestClassJSON;
import eu.stamp_project.utils.Counter;
import eu.stamp_project.utils.DSpotUtils;
import eu.stamp_project.utils.program.InputConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 1/5/17
 */
public class PitMutantScoreSelector extends TakeAllSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(PitMutantScoreSelector.class);

    private int numberOfMutant;

    private List<? extends AbstractPitResult> originalKilledMutants;

    private Map<CtMethod, Set<AbstractPitResult>> testThatKilledMutants;

    private List<AbstractPitResult> mutantNotTestedByOriginal;

    private PitRunner pitRunner;

    private HashMap<AbstractPitResult, CtMethod<?>> methodPerMutant;

    public PitMutantScoreSelector() {
        this(AbstractParser.OutputFormat.XML);
    }

    public PitMutantScoreSelector(AbstractParser.OutputFormat format) {
        this.testThatKilledMutants = new HashMap<>();
        this.pitRunner = new PitRunner(format);
        this.methodPerMutant = new HashMap<>();
    }

    public PitMutantScoreSelector(String pathToOriginalResultOfPit,
                                  AbstractParser.OutputFormat originalFormat,
                                  AbstractParser.OutputFormat consecutiveFormat) {
        this(consecutiveFormat);
        AbstractParser originalResultParser;
        switch (originalFormat) {
            case CSV:
                originalResultParser = new PitCSVResultParser();
                break;
            default:
                originalResultParser = new PitXMLResultParser();
                break;
        }
        initOriginalPitResult(originalResultParser.parse(new File(pathToOriginalResultOfPit)));
    }

    @Override
    public boolean init() {
        if (this.originalKilledMutants == null) {
            final List<? extends AbstractPitResult> results;
            if (InputConfiguration.get().shouldTargetOneTestClass()) {
                results = this.pitRunner.runPit(InputConfiguration.get().getFactory().Class().get(InputConfiguration.get().getTestClasses().get(0)));
            } else {
                results = this.pitRunner.runPit();
            }
            if (results == null) {
                return false;
            }
            initOriginalPitResult(results);
        }
        return true;
    }

    private void initOriginalPitResult(List<? extends AbstractPitResult> results) {
        this.numberOfMutant = results.size();
        this.mutantNotTestedByOriginal = results.stream()
                .filter(result -> result.getStateOfMutant() != AbstractPitResult.State.KILLED)
                .filter(result -> result.getStateOfMutant() != AbstractPitResult.State.SURVIVED)
                .filter(result -> result.getStateOfMutant() != AbstractPitResult.State.NO_COVERAGE)
                .collect(Collectors.toList());
        this.originalKilledMutants = results.stream()
                .filter(result -> result.getStateOfMutant() == AbstractPitResult.State.KILLED)
                .collect(Collectors.toList());
        LOGGER.info("The original test suite kill {} / {}", this.originalKilledMutants.size(), results.size());
    }

    @Override
    public List<CtMethod<?>> selectToAmplify(CtType<?> classTest, List<CtMethod<?>> testsToBeAmplified) {
        if (this.currentClassTestToBeAmplified == null && !testsToBeAmplified.isEmpty()) {
            this.currentClassTestToBeAmplified = testsToBeAmplified.get(0).getDeclaringType();

        }
        return testsToBeAmplified;
    }

    @Override
    public List<CtMethod<?>> selectToKeep(List<CtMethod<?>> amplifiedTestToBeKept) {
        if (amplifiedTestToBeKept.isEmpty()) {
            return amplifiedTestToBeKept;
        }
        CtType clone = this.currentClassTestToBeAmplified.clone();
        clone.setParent(this.currentClassTestToBeAmplified.getParent());
        this.currentClassTestToBeAmplified.getMethods().stream()
                .filter(TestFramework.get()::isTest)
                .forEach(clone::removeMethod);
        amplifiedTestToBeKept.forEach(clone::addMethod);

        DSpotUtils.printCtTypeToGivenDirectory(clone, new File(DSpotCompiler.getPathToAmplifiedTestSrc()));
        final String classpath = InputConfiguration.get().getBuilder()
                .buildClasspath()
                + AmplificationHelper.PATH_SEPARATOR +
                InputConfiguration.get().getClasspathClassesProject()
                + AmplificationHelper.PATH_SEPARATOR + DSpotUtils.getAbsolutePathToDSpotDependencies();

        DSpotCompiler.compile(InputConfiguration.get(), DSpotCompiler.getPathToAmplifiedTestSrc(), classpath,
                new File(InputConfiguration.get().getAbsolutePathToTestClasses()));

        final List<? extends AbstractPitResult> results = this.pitRunner.runPit(clone);

        Set<CtMethod<?>> selectedTests = new HashSet<>();
        if (results != null) {
            LOGGER.info("{} mutants has been generated ({})", results.size(), this.numberOfMutant);
            if (results.size() != this.numberOfMutant) {
                LOGGER.warn("Number of generated mutant is different than the original one.");
            }
            results.stream()
                    .filter(result -> result.getStateOfMutant() == AbstractPitResult.State.KILLED &&
                            !this.originalKilledMutants.contains(result) &&
                            !this.mutantNotTestedByOriginal.contains(result))
                    .forEach(result -> {
                        CtMethod method = this.getMethod(result, clone);
                        if (killsMoreMutantThanParents(method, result)) {
                            if (!testThatKilledMutants.containsKey(method)) {
                                testThatKilledMutants.put(method, new HashSet<>());
                            }
                            testThatKilledMutants.get(method).add(result);
                            if (method == null) {
                                selectedTests.addAll(amplifiedTestToBeKept);// output of pit test does not allow us to know which test case kill new mutants... we keep them all...
                            } else {
                                selectedTests.add(method);
                            }
                        }
                    });
        }

        this.selectedAmplifiedTest.addAll(selectedTests);

        selectedTests.forEach(selectedTest ->
                LOGGER.info("{} kills {} more mutants",
                        selectedTest == null ?
                                this.currentClassTestToBeAmplified.getSimpleName() : selectedTest.getSimpleName(),
                        this.testThatKilledMutants.containsKey(selectedTest) ?
                                this.testThatKilledMutants.get(selectedTest).size() : this.testThatKilledMutants.get(null))
        );
        return new ArrayList<>(selectedTests);
    }


    private CtMethod getMethod(AbstractPitResult result, CtType<?> ctClass) {
        if ("none".equals(result.getSimpleNameMethod())) {
            return null;
        } else {
            if (!this.methodPerMutant.containsKey(result)) {
                List<CtMethod<?>> methodsByName = ctClass.getMethodsByName(result.getSimpleNameMethod());
                if (methodsByName.isEmpty()) {
                    if (ctClass.getSuperclass() != null) {
                        return getMethod(result, ctClass.getSuperclass().getDeclaration());
                    } else {
                        return null;
                    }
                }
                this.methodPerMutant.put(result, methodsByName.get(0));
            }
            return this.methodPerMutant.get(result);
        }
    }

    private boolean killsMoreMutantThanParents(CtMethod test, AbstractPitResult result) {
        CtMethod parent = AmplificationHelper.getAmpTestParent(test);
        while (parent != null) {
            if (this.testThatKilledMutants.get(parent) != null &&
                    this.testThatKilledMutants.get(parent).contains(result)) {
                return false;
            }
            parent = AmplificationHelper.getAmpTestParent(parent);
        }
        return true;
    }

    @Override
    public TestSelectorElementReport report() {
        final String reportStdout = reportStdout();
        final TestClassJSON testClassJSON = reportJSONMutants();
        //clean up for the next class
        this.currentClassTestToBeAmplified = null;
        this.testThatKilledMutants.clear();
        this.selectedAmplifiedTest.clear();
        return new TestSelectorElementReportImpl(reportStdout, testClassJSON);
    }

    private String reportStdout() {
        return "Test class that has been amplified: " + this.currentClassTestToBeAmplified.getQualifiedName() +
                AmplificationHelper.LINE_SEPARATOR +
                "The original test suite kills " +
                this.originalKilledMutants.size() +
                " mutants" + AmplificationHelper.LINE_SEPARATOR +
                "The amplification results with " +
                this.testThatKilledMutants.size() +
                " new tests" + AmplificationHelper.LINE_SEPARATOR +
                "it kills " + getNbTotalNewMutantKilled() +
                " more mutants" + AmplificationHelper.LINE_SEPARATOR;
    }

    private long getNbTotalNewMutantKilled() {
        return this.testThatKilledMutants.keySet()
                .stream()
                .flatMap(method -> this.testThatKilledMutants.get(method).stream())
                .distinct()
                .count();
    }

    private TestClassJSON reportJSONMutants() {
        if (this.currentClassTestToBeAmplified == null) {
            LOGGER.warn("The current test class is null.");
            return new TestClassJSON(getNbMutantKilledOriginally(this.currentClassTestToBeAmplified.getQualifiedName()),
                    "unknown",
                    this.currentClassTestToBeAmplified.getMethods()
                            .stream()
                            .filter(TestFramework.get()::isTest)
                            .count());
        }
        TestClassJSON testClassJSON;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final File file = new File(InputConfiguration.get().getOutputDirectory() + "/" +
                this.currentClassTestToBeAmplified.getQualifiedName() + "report.json");
        if (file.exists()) {
            try {
                testClassJSON = gson.fromJson(new FileReader(file), TestClassJSON.class);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            testClassJSON = new TestClassJSON(getNbMutantKilledOriginally(this.currentClassTestToBeAmplified.getQualifiedName()),
                    this.currentClassTestToBeAmplified.getQualifiedName(),
                    this.currentClassTestToBeAmplified.getMethods()
                            .stream()
                            .filter(TestFramework.get()::isTest)
                            .count());
        }
        List<CtMethod> keys = new ArrayList<>(this.testThatKilledMutants.keySet());
        keys.forEach(amplifiedTest -> {
                    List<AbstractPitResult> pitResults = new ArrayList<>(this.testThatKilledMutants.get(amplifiedTest));
                    final List<MutantJSON> mutantsJson = new ArrayList<>();
                    pitResults.forEach(pitResult -> mutantsJson.add(new MutantJSON(
                            pitResult.getFullQualifiedNameMutantOperator(),
                            pitResult.getLineNumber(),
                            pitResult.getNameOfMutatedMethod()
                    )));
                    if (amplifiedTest == null) {
                        testClassJSON.addTestCase(new TestCaseJSON(
                                this.currentClassTestToBeAmplified.getSimpleName(),
                                Counter.getAllAssertions(),
                                Counter.getAllInput(),
                                mutantsJson
                        ));
                    } else {
                        testClassJSON.addTestCase(new TestCaseJSON(
                                amplifiedTest.getSimpleName(),
                                Counter.getAssertionOfSinceOrigin(amplifiedTest),
                                Counter.getInputOfSinceOrigin(amplifiedTest),
                                mutantsJson
                        ));
                    }
                }
        );
        return testClassJSON;
    }

    private int getNbMutantKilledOriginally(String qualifiedName) {
        return (int) this.originalKilledMutants.stream()
                .filter(pitResult ->
                        qualifiedName.equals(pitResult.getFullQualifiedNameOfKiller())
                )
                .count();
    }

}
