package eu.stamp_project.dspot.common.automaticbuilder.gradle;

import eu.stamp_project.dspot.common.automaticbuilder.AutomaticBuilder;
import eu.stamp_project.dspot.common.configuration.UserInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

import static eu.stamp_project.dspot.common.automaticbuilder.gradle.GradlePitTaskAndOptions.CMD_PIT_MUTATION_COVERAGE;

/**
 * Created by Daniele Gagliardi
 * daniele.gagliardi@eng.it
 * on 18/07/17.
 */
public class GradleAutomaticBuilder implements AutomaticBuilder {

    @Override
    public void setAbsolutePathToProjectRoot(String absolutePathToProjectRoot) {
        this.absolutePathToProjectRoot = absolutePathToProjectRoot;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GradleAutomaticBuilder.class);

    private static final String JAVA_PROJECT_CLASSPATH = "gjp_cp"; // Gradle Java Project classpath file

    private GradleInjector gradleInjector;

    private String absolutePathToProjectRoot;

    public GradleAutomaticBuilder(UserInput configuration) {
        this.absolutePathToProjectRoot = configuration.getAbsolutePathToProjectRoot();
        this.gradleInjector = new GradleInjector(
                this.absolutePathToProjectRoot + File.separator + GradleInjector.GRADLE_BUILD_FILE,
                !configuration.isGregorMode(),
                configuration.getFilter(),
                configuration.getPitVersion(),
                configuration.getTimeOutInMs(),
                configuration.getJVMArgs(),
                configuration.getExcludedClasses(),
                configuration.getAdditionalClasspathElements()
        );
    }

    @Override
    public String compileAndBuildClasspath() {
        this.compile();
        return this.buildClasspath();
    }

    @Override
    public void compile() {
        runTasks(false, "clean", "compileTest");
    }

    @Override
    public String buildClasspath() {
        try {
            final File classpathFile = new File(this.absolutePathToProjectRoot + File.separator + "build/classpath.txt");
            if (!classpathFile.exists()) {
                LOGGER.info("Classpath file for Gradle project doesn't exist, starting to build it...");
                LOGGER.info("Injecting  Gradle task to print project classpath on stdout...");
                this.gradleInjector.injectPrintClasspathTask(this.absolutePathToProjectRoot);
                LOGGER.info("Retrieving project classpath...");
                this.runTasks(false, GradleInjector.WRITE_CLASSPATH_TASK);
                LOGGER.info("Writing project classpath on file " + JAVA_PROJECT_CLASSPATH + "...");
                this.gradleInjector.resetOriginalGradleBuildFile(this.absolutePathToProjectRoot);
            }
            try (BufferedReader buffer = new BufferedReader(new FileReader(classpathFile))) {
                final String collect = buffer
                        .lines()
                        .collect(Collectors.joining());
                return Arrays.stream(collect.split(":"))
                        .filter(path -> new File(path).exists() && new File(path).isAbsolute())
                        .collect(Collectors.joining(":"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reset() {
        //TODO Maybe we should change one time the the gradle and reset it at the end of the process
    }

    @Override
    public void runPit() {
        runPit(null);
    }

    @Override
    public void runPit(CtType<?>... testClasses) {
        try {
            LOGGER.info("Injecting  Gradle task to run Pit...");
            this.gradleInjector.injectPitTask(this.absolutePathToProjectRoot, testClasses);
            LOGGER.info("Running Pit...");
            runTasks(true, "clean", CMD_PIT_MUTATION_COVERAGE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.gradleInjector.resetOriginalGradleBuildFile(this.absolutePathToProjectRoot);
        }
    }

    protected void runTasks(boolean skipTest, String... tasks) {
        Process p;
        final String[] finalCommand = new String[tasks.length + 1];
        finalCommand[0] = "gradle";
        System.arraycopy(tasks, 0, finalCommand, 1, tasks.length);
        LOGGER.info("Run gradle tasks: {}", String.join(" ", tasks));
        try {
            p = Runtime.getRuntime().exec(String.join(" ", finalCommand), null, new File(this.absolutePathToProjectRoot));
            new Thread(() -> {
                Scanner sc = new Scanner(p.getInputStream());
                while (sc.hasNextLine()) {
                    System.out.println(sc.nextLine());
                }
                sc.close();
            }).start();
            new Thread(() -> {
                Scanner sc = new Scanner(p.getErrorStream());
                while (sc.hasNextLine()) {
                    System.err.println(sc.nextLine());
                }
                sc.close();
            }).start();
            p.waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getOutputDirectoryPit() {
        return GradlePitTaskAndOptions.OUTPUT_DIRECTORY_PIT;
    }
}
