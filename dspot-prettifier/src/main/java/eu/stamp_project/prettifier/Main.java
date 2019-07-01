package eu.stamp_project.prettifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.stamp_project.prettifier.code2vec.Code2VecExecutor;
import eu.stamp_project.prettifier.code2vec.Code2VecParser;
import eu.stamp_project.prettifier.code2vec.Code2VecWriter;
import eu.stamp_project.prettifier.context2code.Context2CodeExecutor;
import eu.stamp_project.prettifier.context2code.Context2CodeParser;
import eu.stamp_project.prettifier.context2code.Context2CodeWriter;
import eu.stamp_project.prettifier.minimization.GeneralMinimizer;
import eu.stamp_project.prettifier.minimization.Minimizer;
import eu.stamp_project.prettifier.minimization.PitMutantMinimizer;
import eu.stamp_project.prettifier.options.InputConfiguration;
import eu.stamp_project.prettifier.options.JSAPOptions;
import eu.stamp_project.prettifier.output.PrettifiedTestMethods;
import eu.stamp_project.prettifier.output.report.ReportJSON;
import eu.stamp_project.test_framework.TestFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 11/02/19
 */
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static ReportJSON report = new ReportJSON();

    /*
        Apply the following algorithm:
            1) Minimize the amplified test methods.
            2) Rename local variables
            3) rename the test methods
     */

    public static void main(String[] args) {
        JSAPOptions.parse(args);
        final CtType<?> amplifiedTestClass = loadAmplifiedTestClass();
        final List<CtMethod<?>> prettifiedAmplifiedTestMethods = run(amplifiedTestClass);
        // output now
        output(amplifiedTestClass, prettifiedAmplifiedTestMethods);
    }

    public static CtType<?> loadAmplifiedTestClass() {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(InputConfiguration.get().getPathToAmplifiedTestClass());
        launcher.buildModel();
        return launcher.getFactory().Class().getAll().get(0);
    }

    public static List<CtMethod<?>> run(CtType<?> amplifiedTestClass) {
        final List<CtMethod<?>> testMethods = TestFramework.getAllTest(amplifiedTestClass);
        Main.report.nbTestMethods = testMethods.size();
        // 1
        final List<CtMethod<?>> minimizedAmplifiedTestMethods = applyMinimization(
                testMethods,
                amplifiedTestClass
        );
        // 2
//        applyContext2Code(minimizedAmplifiedTestMethods);
        // 3
        applyCode2Vec(minimizedAmplifiedTestMethods);
        return minimizedAmplifiedTestMethods;
    }

    public static List<CtMethod<?>> applyMinimization(List<CtMethod<?>> amplifiedTestMethodsToBeMinimized, CtType<?> amplifiedTestClass) {

        Main.report.medianNbStatementBefore = Main.getMedian(amplifiedTestMethodsToBeMinimized.stream()
                .map(ctMethod -> ctMethod.getElements(new TypeFilter<>(CtStatement.class)))
                .map(List::size)
                .collect(Collectors.toList()));

        // 1rst apply a general minimization
        amplifiedTestMethodsToBeMinimized = Main.applyGivenMinimizer(new GeneralMinimizer(), amplifiedTestMethodsToBeMinimized);
        // update the test class with minimized test methods
        final ArrayList<CtMethod<?>> allMethods = new ArrayList<>(amplifiedTestClass.getMethods());
        allMethods.stream()
                .filter(TestFramework.get()::isTest)
                .forEach(amplifiedTestClass::removeMethod);
        amplifiedTestMethodsToBeMinimized.forEach(amplifiedTestClass::addMethod);

        // 2nd apply a specific minimization
        amplifiedTestMethodsToBeMinimized = Main.applyGivenMinimizer(new PitMutantMinimizer(amplifiedTestClass), amplifiedTestMethodsToBeMinimized);

        Main.report.medianNbStatementAfter = Main.getMedian(amplifiedTestMethodsToBeMinimized.stream()
                .map(ctMethod -> ctMethod.getElements(new TypeFilter<>(CtStatement.class)))
                .map(List::size)
                .collect(Collectors.toList()));

        return amplifiedTestMethodsToBeMinimized;
    }

    private static List<CtMethod<?>> applyGivenMinimizer(Minimizer minimizer, List<CtMethod<?>> amplifiedTestMethodsToBeMinimized) {
        final List<CtMethod<?>> minimizedAmplifiedTestMethods = amplifiedTestMethodsToBeMinimized.stream()
                .map(minimizer::minimize)
                .collect(Collectors.toList());
        minimizer.updateReport(Main.report);
        return minimizedAmplifiedTestMethods;
    }

    public static void applyContext2Code(List<CtMethod<?>> amplifiedTestMethodsToBeRenamed) {
        Context2CodeWriter writer = new Context2CodeWriter();
        Context2CodeParser parser = new Context2CodeParser();
        Context2CodeExecutor context2codeExecutor = null;
        try {
            context2codeExecutor = new Context2CodeExecutor();
            for (CtMethod<?> amplifiedTestMethodToBeRenamed : amplifiedTestMethodsToBeRenamed) {
                writer.writeCtMethodToInputFile(amplifiedTestMethodToBeRenamed);
                context2codeExecutor.run();
                // TODO (one method-name) -> (numerous variable-names)
                final String context2codeOutput = context2codeExecutor.getOutput();
                final String predictedSimpleName = parser.parse(context2codeOutput);
                LOGGER.info("Context2Code predicted {} for {} as new name", predictedSimpleName, amplifiedTestMethodToBeRenamed.getSimpleName());
                amplifiedTestMethodToBeRenamed.setSimpleName(predictedSimpleName);
                // TODO add scripts, models, test files
                // TODO uncomment Line78
            }
        } finally {
            if (context2codeExecutor != null) {
                context2codeExecutor.stop();
            }
        }
    }

    public static void applyCode2Vec(List<CtMethod<?>> amplifiedTestMethodsToBeRenamed) {
        Code2VecWriter writer = new Code2VecWriter();
        Code2VecParser parser = new Code2VecParser();
        Code2VecExecutor code2VecExecutor = null;
        try {
            code2VecExecutor = new Code2VecExecutor();
            for (CtMethod<?> amplifiedTestMethodToBeRenamed : amplifiedTestMethodsToBeRenamed) {
                writer.writeCtMethodToInputFile(amplifiedTestMethodToBeRenamed);
                code2VecExecutor.run();
                final String code2vecOutput = code2VecExecutor.getOutput();
                final String predictedSimpleName = parser.parse(code2vecOutput);
                LOGGER.info("Code2Vec predicted {} for {} as new name", predictedSimpleName, amplifiedTestMethodToBeRenamed.getSimpleName());
                amplifiedTestMethodToBeRenamed.setSimpleName(predictedSimpleName);
            }
        } finally {
            if (code2VecExecutor != null) {
                code2VecExecutor.stop();
            }
        }
    }

    public static <T extends Number & Comparable<T>> Double getMedian(List<T> list) {
        Collections.sort(list);
        return list.size() % 2 == 0 ?
                list.stream().skip(list.size() / 2 - 1).limit(2).mapToDouble(value -> new Double(value.toString())).average().getAsDouble() :
                new Double(list.stream().skip(list.size() / 2).findFirst().get().toString());
    }

    public static void output(CtType<?> amplifiedTestClass, List<CtMethod<?>> prettifiedAmplifiedTestMethods) {
        PrettifiedTestMethods.output(amplifiedTestClass, prettifiedAmplifiedTestMethods);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final String pathname = eu.stamp_project.utils.program.InputConfiguration.get().getOutputDirectory() +
                "/" + amplifiedTestClass.getSimpleName() + "report.json";
        LOGGER.info("Output a report in {}", pathname);
        final File file = new File(pathname);
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(gson.toJson(Main.report));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
