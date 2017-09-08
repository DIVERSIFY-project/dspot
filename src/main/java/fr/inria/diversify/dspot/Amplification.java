package fr.inria.diversify.dspot;

import fr.inria.diversify.dspot.amplifier.Amplifier;
import fr.inria.diversify.dspot.assertGenerator.AssertGenerator;
import fr.inria.diversify.dspot.selector.TestSelector;
import fr.inria.diversify.dspot.support.DSpotCompiler;
import fr.inria.diversify.dspot.support.TestCompiler;
import fr.inria.diversify.runner.InputConfiguration;
import fr.inria.diversify.util.Log;
import fr.inria.diversify.utils.AmplificationChecker;
import fr.inria.diversify.utils.AmplificationHelper;
import fr.inria.diversify.utils.DSpotUtils;
import fr.inria.stamp.test.listener.TestListener;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * User: Simon
 * Date: 03/12/15
 * Time: 13:52
 */
public class Amplification {

	private InputConfiguration configuration;
	private List<Amplifier> amplifiers;
	private TestSelector testSelector;
	private AssertGenerator assertGenerator;
	private DSpotCompiler compiler;

	private static int ampTestCount;

	public Amplification(InputConfiguration configuration, List<Amplifier> amplifiers, TestSelector testSelector, DSpotCompiler compiler) {
		this.configuration = configuration;
		this.amplifiers = amplifiers;
		this.testSelector = testSelector;
		this.compiler = compiler;
		this.assertGenerator = new AssertGenerator(this.configuration, this.compiler);
	}

	public void amplification(CtType<?> classTest, int maxIteration) throws IOException, InterruptedException, ClassNotFoundException {
		amplification(classTest, AmplificationHelper.getAllTest(classTest), maxIteration);
	}

	public void amplification(CtType<?> classTest, List<CtMethod<?>> methods, int maxIteration) throws IOException, InterruptedException, ClassNotFoundException {
		List<CtMethod<?>> tests = methods.stream()
				.filter(mth -> AmplificationChecker.isTest(mth, this.configuration.getInputProgram().getRelativeTestSourceCodeDir()))
				.collect(Collectors.toList());
		if (tests.isEmpty()) {
			return;
		}

		Log.info("amplification of {} ({} test)", classTest.getQualifiedName(), tests.size());
		testSelector.reset();
		List<CtMethod<?>> ampTest = new ArrayList<>();

		updateAmplifiedTestList(ampTest, preAmplification(classTest, tests));
		resetAmplifiers(classTest);

		for (int i = 0; i < tests.size(); i++) {
			CtMethod test = tests.get(i);
			Log.debug("amp {} ({}/{})", test.getSimpleName(), i + 1, tests.size());
			testSelector.reset();
			TestListener result = compileAndRunTests(classTest, Collections.singletonList(tests.get(i)));
			if (result != null) {
				if (result.getFailingTests().isEmpty()
						&& !result.getRunningTests().isEmpty()) {
					updateAmplifiedTestList(ampTest,
							amplification(classTest, test, maxIteration));
				} else {
					Log.debug("{} / {} test cases failed!", result.getFailingTests().size(), result.getRunningTests().size());
				}
			}
		}
	}

	private List<CtMethod<?>> amplification(CtType<?> classTest, CtMethod test, int maxIteration) throws IOException, InterruptedException, ClassNotFoundException {
		List<CtMethod<?>> currentTestList = new ArrayList<>();
		currentTestList.add(test);

		List<CtMethod<?>> amplifiedTests = new ArrayList<>();

		for (int i = 0; i < maxIteration; i++) {
			Log.debug("iteration {}:", i);
			List<CtMethod<?>> testToBeAmplified = testSelector.selectToAmplify(currentTestList);
			if (testToBeAmplified.isEmpty()) {
				Log.debug("No test could be generated from selected test");
				continue;
			}
			Log.debug("{} tests selected to be amplified over {} available tests", testToBeAmplified.size(), currentTestList.size());

			currentTestList = AmplificationHelper.reduce(amplifyTests(testToBeAmplified));
			List<CtMethod<?>> testWithAssertions = assertGenerator.generateAsserts(classTest, currentTestList);
			if (testWithAssertions.isEmpty()) {
				continue;
			} else {
				currentTestList = testWithAssertions;
			}
			TestListener result = compileAndRunTests(classTest, currentTestList);
			if (result == null) {
				continue;
			} else if (!result.getFailingTests().isEmpty()) {
				Log.warn("Discarding failing test cases");
				final Set<String> failingTestCase = result.getFailingTests().stream()
						.map(Failure::getDescription)
						.map(Description::getMethodName)
						.collect(Collectors.toSet());
				currentTestList = currentTestList.stream()
						.filter(ctMethod -> !failingTestCase.contains(ctMethod.getSimpleName()))
						.collect(Collectors.toList());
			}
			currentTestList = AmplificationHelper.filterTest(currentTestList, result);
			Log.debug("{} test method(s) has been successfully generated", currentTestList.size());
			amplifiedTests.addAll(testSelector.selectToKeep(currentTestList));
		}
		return amplifiedTests;
	}

	private void updateAmplifiedTestList(List<CtMethod<?>> ampTest, List<CtMethod<?>> amplification) {
		ampTest.addAll(amplification);
		ampTestCount += amplification.size();
		Log.debug("total amp test: {}, global: {}", amplification.size(), ampTestCount);
	}

	private List<CtMethod<?>> preAmplification(CtType classTest, List<CtMethod<?>> tests) throws IOException, ClassNotFoundException {
		TestListener result = compileAndRunTests(classTest, tests);
		if (result == null) {
			Log.warn("Need a green test suite to run dspot");
			return Collections.emptyList();
		}
		if (!result.getFailingTests().isEmpty()) {
			Log.warn("{} tests failed before the amplifications", result.getFailingTests().size());

			Log.warn("{}", result.getFailingTests().stream()
					.map(Object::toString)
					.collect(Collectors.joining(System.getProperty("line.separator")))
			);
			Log.warn("Discarding following test cases for the amplification");

			result.getFailingTests().stream()
					.map(Failure::getDescription)
					.map(Description::getMethodName)
					.forEach(failure -> {
						try {
							CtMethod testToRemove = tests.stream()
									.filter(m -> failure.equals(m.getSimpleName()))
									.findFirst().get();
							tests.remove(tests.indexOf(testToRemove));
							Log.warn("{}", testToRemove.getSimpleName());
						} catch (Exception ignored) {
							//ignored
						}
					});
			return preAmplification(classTest, tests);
		} else {
			testSelector.update();
			Log.debug("Try to add assertions before amplification");
			testSelector.selectToKeep(
					assertGenerator.generateAsserts(
							classTest, testSelector.selectToAmplify(tests))
			);
			return testSelector.getAmplifiedTestCases();
		}
	}

	private List<CtMethod<?>> amplifyTests(List<CtMethod<?>> tests) {
		Log.info("Amplification of inputs...");
		List<CtMethod<?>> amplifiedTests = tests.stream()
				.flatMap(test -> {
					DSpotUtils.printProgress(tests.indexOf(test), tests.size());
					return amplifyTest(test).stream();
				})
				.filter(test -> test != null && !test.getBody().getStatements().isEmpty())
				.collect(Collectors.toList());
		Log.debug("{} new tests generated", amplifiedTests.size());
		return amplifiedTests;
	}

	private List<CtMethod<?>> amplifyTest(CtMethod test) {
		return amplifiers.stream()
				.flatMap(amplifier -> amplifier.apply(test).stream()).
						map(CtMethod::getBody)
				.distinct()
				.map(body -> body.getParent(CtMethod.class))
				.map(amplifiedTest ->
						AmplificationHelper.addOriginInComment(amplifiedTest, AmplificationHelper.getTopParent(test))
				).collect(Collectors.toList());
	}

	private void resetAmplifiers(CtType parentClass) {
		amplifiers.forEach(amp -> amp.reset(parentClass));
	}

	private TestListener compileAndRunTests(CtType classTest, List<CtMethod<?>> currentTestList) {
		CtType amplifiedTestClass = this.testSelector.buildClassForSelection(classTest, currentTestList);
		final TestListener result = TestCompiler.compileAndRun(
				amplifiedTestClass,
				this.compiler,
				currentTestList,
				this.configuration
		);
		final long numberOfSubClasses = classTest.getFactory().Class().getAll().stream()
				.filter(subClass -> classTest.getReference().equals(subClass.getSuperclass()))
				.count();
		if (result == null ||
				!result.getFailingTests().isEmpty() ||
				(!classTest.getModifiers().contains(ModifierKind.ABSTRACT) &&
						result.getRunningTests().size() != currentTestList.size()) ||
				(classTest.getModifiers().contains(ModifierKind.ABSTRACT) &&
						numberOfSubClasses != result.getRunningTests().size())) {
			return null;
		} else {
			Log.debug("update test testCriterion");
			testSelector.update();
			return result;
		}
	}
}