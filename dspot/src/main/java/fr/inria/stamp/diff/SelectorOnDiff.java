package fr.inria.stamp.diff;

import fr.inria.diversify.utils.AmplificationChecker;
import fr.inria.diversify.utils.AmplificationHelper;
import fr.inria.diversify.utils.sosiefier.InputConfiguration;
import fr.inria.stamp.Main;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 08/03/18
 */
public class SelectorOnDiff {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectorOnDiff.class);

    public static Map<CtType<?>, List<CtMethod<?>>> findTestMethodsAccordingToADiff(InputConfiguration configuration) {
        final Factory factory = configuration.getInputProgram().getFactory();
        final String baseSha = configuration.getProperties().getProperty("baseSha");
        final String pathToFirstVersion = configuration.getProperties().getProperty("project") +
                (configuration.getProperties().getProperty("targetModule") != null ?
                        configuration.getProperties().getProperty("targetModule") : "");
        final String pathToSecondVersion = configuration.getProperties().getProperty("folderPath") +
                (configuration.getProperties().getProperty("targetModule") != null ?
                        configuration.getProperties().getProperty("targetModule") : "");
        if (Main.verbose) {
            LOGGER.info("Selecting according to a diff between {} and {} ({})",
                    pathToFirstVersion,
                    pathToSecondVersion,
                    baseSha
            );
        }

        return new SelectorOnDiff(configuration,
                        factory,
                        baseSha,
                        pathToFirstVersion,
                        pathToSecondVersion
                ).findTestMethods();
    }

    private InputConfiguration configuration;
    private Factory factory;
    private String baseSha;
    private String pathToFirstVersion;
    private String pathToSecondVersion;

    public SelectorOnDiff(InputConfiguration configuration,
                          Factory factory,
                          String baseSha,
                          String pathToFirstVersion,
                          String pathToSecondVersion) {
        this.configuration = configuration;
        this.factory = factory;
        this.baseSha = baseSha;
        this.pathToFirstVersion = pathToFirstVersion;
        this.pathToSecondVersion = pathToSecondVersion;
    }

    @SuppressWarnings("unchecked")
    public Map<CtType<?>, List<CtMethod<?>>> findTestMethods() {
        Map<CtType<?>, List<CtMethod<?>>> selection = new HashMap<>();
        final Set<CtMethod> selectedTestMethods = new HashSet<>();
        // get the modified files
        final Set<String> modifiedJavaFiles = getModifiedJavaFiles();
        // get modified methods
        final Set<CtMethod> modifiedMethods = getModifiedMethods(modifiedJavaFiles);
        // get modified test cases
        final List<CtMethod> modifiedTestMethods = modifiedMethods.stream()
                .filter(AmplificationChecker::isTest)
                .collect(Collectors.toList());
        modifiedMethods.removeAll(modifiedTestMethods);
        if (!modifiedTestMethods.isEmpty()) { // if any, we add them to the selection
            LOGGER.info("Select {} modified test methods", modifiedTestMethods.size());
            selectedTestMethods.addAll(modifiedTestMethods);
        }
        // get all invocations to modified methods in test methods
        final List<CtMethod<?>> testMethodsThatExecuteDirectlyModifiedMethods =
                getTestMethodsThatExecuteDirectlyModifiedMethods(modifiedMethods, modifiedTestMethods);
        if (!modifiedTestMethods.isEmpty()) { // if any, we add them to the selection
            LOGGER.info("Select {} test methods that execute directly modified methods", modifiedTestMethods.size());
            selectedTestMethods.addAll(testMethodsThatExecuteDirectlyModifiedMethods);
        }
        // if we could not find any test methods above, we use naming convention
        if (selectedTestMethods.isEmpty()) {
            final List<CtMethod<?>> testMethodsAccordingToNameOfModifiedMethod =
                    getTestMethodsAccordingToNameOfModifiedMethod(modifiedMethods);
            // if any test methods has the name of a modified in its own name
            if (!testMethodsAccordingToNameOfModifiedMethod.isEmpty()) {
                selectedTestMethods.addAll(testMethodsAccordingToNameOfModifiedMethod);
            } else {
                selectedTestMethods.addAll(getTestClassesAccordingToModifiedJavaFiles(modifiedJavaFiles));
            }
        }

        for (CtMethod selectedTestMethod : selectedTestMethods) {
            final CtType parent = selectedTestMethod.getParent(CtType.class);
            if (!selection.containsKey(parent)) {
                selection.put(parent, new ArrayList<>());
            }
            selection.get(parent).add(selectedTestMethod);
        }
        return selection;
    }

    private Set<CtMethod<?>> getTestClassesAccordingToModifiedJavaFiles(Set<String> modifiedJavaFiles) {
        final List<String> candidateTestClassName = modifiedJavaFiles.stream()
                .filter(pathToClass ->
                        new File(configuration.getProperties().getProperty("project") + pathToClass.substring(1)).exists() &&
                                new File(configuration.getProperties().getProperty("folderPath") + pathToClass.substring(1)).exists() // it is present in both versions
                )
                .flatMap(pathToClass -> {
                    final String[] split = pathToClass.substring(this.configuration.getRelativeSourceCodeDir().length() + 2).split("/");
                    final String nameOfTestClass = split[split.length - 1].split("\\.")[0];
                    final String qualifiedName = IntStream.range(0, split.length - 1).mapToObj(value -> split[value]).collect(Collectors.joining("."));
                    return Stream.of(
                            qualifiedName + "." + nameOfTestClass + "Test",
                            qualifiedName + "." + "Test" + nameOfTestClass
                    );
                }).collect(Collectors.toList());
        // test classes directly dedicated to modified java files.
        final Set<CtMethod<?>> directTestClasses = candidateTestClassName.stream()
                .map(testClassName -> this.factory.Type().get(testClassName))
                .filter(testClass ->
                        testClass != null &&
                                (testClass.getMethods().stream().anyMatch(AmplificationChecker::isTest) ||
                                        testClass.getSuperclass()
                                                .getTypeDeclaration()
                                                .getMethods()
                                                .stream()
                                                .anyMatch(AmplificationChecker::isTest)
                                )
                ).flatMap(testClass -> {
                    if (testClass.getMethods().stream().noneMatch(AmplificationChecker::isTest)) {
                        return testClass.getSuperclass().getTypeDeclaration().getMethods().stream();
                    } else {
                        return testClass.getMethods().stream();
                    }
                })
                .filter(AmplificationChecker::isTest)
                .collect(Collectors.toSet());
        return directTestClasses;
    }

    private List<CtMethod<?>> getTestMethodsAccordingToNameOfModifiedMethod(Set<CtMethod> modifiedMethods) {
        final Set<String> modifiedMethodsNames =
                modifiedMethods.stream().map(CtMethod::getSimpleName).collect(Collectors.toSet());
        return this.factory.Package().getRootPackage()
                .getElements(new TypeFilter<CtMethod<?>>(CtMethod.class) {
                    @Override
                    public boolean matches(CtMethod<?> element) {
                        return AmplificationChecker.isTest(element) &&
                                modifiedMethodsNames.stream()
                                        .anyMatch(element.getSimpleName()::contains);
                    }
                });
    }


    private List getTestMethodsThatExecuteDirectlyModifiedMethods(Set<CtMethod> modifiedMethods,
                                                                  List<CtMethod> modifiedTestMethods) {
        return this.factory.Package().getRootPackage()
                .getElements(new TypeFilter<CtExecutableReference>(CtExecutableReference.class) {
                    @Override
                    public boolean matches(CtExecutableReference element) {
                        return modifiedMethods.contains(element.getDeclaration());
                    }
                }).stream()
                .map(ctExecutableReference -> ctExecutableReference.getParent(CtMethod.class))
                .filter(AmplificationChecker::isTest)
                .filter(ctMethod -> !(modifiedTestMethods.contains(ctMethod)))
                .collect(Collectors.toList());
    }

    public Set<CtMethod> getModifiedMethods(Set<String> modifiedJavaFiles) {
        return modifiedJavaFiles.stream()
                .flatMap(s ->
                        getModifiedMethods(pathToFirstVersion + s.substring(1), pathToSecondVersion + s.substring(1)).stream()
                ).collect(Collectors.toSet());
    }

    public Set<CtMethod> getModifiedMethods(String pathFile1, String pathFile2) {
        try {
            final File file1 = new File(pathFile1);
            final File file2 = new File(pathFile2);
            if (!file1.exists() || !file2.exists()) {
                return Collections.emptySet();
            }
            Diff result = (new AstComparator()).compare(file1, file2);
            return result.getRootOperations()
                    .stream()
                    .map(operation -> operation.getSrcNode().getParent(CtMethod.class))
                    .filter(Objects::nonNull) // it seems that gumtree can return null value
                    .collect(Collectors.toSet());
        } catch (Exception ignored) {
            // if something bad happen, we do not care, we go for next file
            return Collections.emptySet();
        }
    }

    private Set<String> getModifiedJavaFiles() {
        Process p;
        try {
            p = Runtime.getRuntime().exec(
                    "git diff " + this.baseSha,
                    new String[]{},
                    new File(this.pathToSecondVersion));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final Set<String> modifiedJavaFiles = new BufferedReader(new InputStreamReader(p.getInputStream()))
                .lines()
                .filter(line -> line.startsWith("diff") && line.endsWith(".java"))
                .map(line -> line.split(" ")[2])
                .collect(Collectors.toSet());
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (Main.verbose) {
            LOGGER.info("Modified files:{}{}", AmplificationHelper.LINE_SEPARATOR,
                    modifiedJavaFiles.stream().collect(Collectors.joining(AmplificationHelper.LINE_SEPARATOR))
            );
        }

        return modifiedJavaFiles;
    }

}
