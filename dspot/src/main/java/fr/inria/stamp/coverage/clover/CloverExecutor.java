package fr.inria.stamp.coverage.clover;

import com.atlassian.clover.CloverInstr;
import com.atlassian.clover.reporters.html.HtmlReporter;
import com.atlassian.clover.reporters.json.JSONException;
import com.atlassian.clover.reporters.json.JSONObject;
import fr.inria.diversify.automaticbuilder.AutomaticBuilderFactory;
import fr.inria.diversify.dspot.support.DSpotCompiler;
import fr.inria.diversify.utils.AmplificationHelper;
import fr.inria.diversify.utils.DSpotUtils;
import fr.inria.diversify.utils.sosiefier.InputConfiguration;
import fr.inria.stamp.EntryPoint;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 22/12/17
 */
public class CloverExecutor {

    private static final String ROOT_DIRECTORY = "target/dspot/clover/";

    private static final String DATABASE_FILE = "/clover.db";

    private static final String INSTR_SOURCE_DIRECTORY = "/instr/";

    private static final String INSTR_BIN_DIRECTORY = "/instr-classes/";

    private static final String REPORT_DIRECTORY = "/report/";

    public static Map<String, Map<String, List<Integer>>> executeAll(InputConfiguration configuration,
                                                                     String pathToSources) {
        return CloverExecutor.execute(configuration, pathToSources,
                DSpotUtils.getAllTestClasses(configuration)
        );
    }

    public static Map<String, Map<String, List<Integer>>> execute(InputConfiguration configuration,
                                                                  String pathToSources,
                                                                  String... testClassesNames) {

        final File rootDirectoryOfCloverFiles = new File(ROOT_DIRECTORY);
        try {
            FileUtils.deleteDirectory(rootDirectoryOfCloverFiles);
        } catch (IOException ignored) {
            //ignored
        }

        CloverInstr.mainImpl(new String[]{
                "-i", rootDirectoryOfCloverFiles.getAbsolutePath() + DATABASE_FILE,
                "-s", pathToSources,
                "-d", rootDirectoryOfCloverFiles.getAbsolutePath() + INSTR_SOURCE_DIRECTORY
        });

        final String classpath = AutomaticBuilderFactory.getAutomaticBuilder(configuration)
                .buildClasspath(configuration.getInputProgram().getProgramDir());
        final String finalClasspath = classpath +
                AmplificationHelper.PATH_SEPARATOR + rootDirectoryOfCloverFiles.getAbsolutePath() + INSTR_BIN_DIRECTORY +
                AmplificationHelper.PATH_SEPARATOR + ABSOLUTE_PATH_TO_CLOVER_DEPENDENCIES;



        final File binaryOutputDirectory = new File(rootDirectoryOfCloverFiles.getAbsolutePath() + INSTR_BIN_DIRECTORY);
        if (!binaryOutputDirectory.mkdir()) {
            throw new RuntimeException("Could not create the directory" + rootDirectoryOfCloverFiles.getAbsolutePath() + INSTR_BIN_DIRECTORY);
        }
        DSpotCompiler.compile(rootDirectoryOfCloverFiles.getAbsolutePath() + INSTR_SOURCE_DIRECTORY,
                finalClasspath,
                binaryOutputDirectory
        );

        EntryPoint.runTestClasses(finalClasspath, testClassesNames);

        HtmlReporter.runReport(new String[]{
                "-i", rootDirectoryOfCloverFiles.getAbsolutePath() + DATABASE_FILE,
                "-o", rootDirectoryOfCloverFiles.getAbsolutePath() + REPORT_DIRECTORY,
                "--lineinfo",
                "--showinner",
                "--showlambda",
        });

        // removing the test classes ran
        Arrays.stream(testClassesNames).forEach(jsonTestTargets::remove);
        return convert();
    }

    private static Map<String, Map<String, List<Integer>>> convert() {
        final Map<String, Map<String, List<Integer>>> coverage = new HashMap<>();
        jsonTestTargets.keySet().forEach(sourceClass -> {
            final JSONObject jsonObject = jsonTestTargets.get(sourceClass);
            jsonObject.keys().forEachRemaining(record -> {
                try {
                    JSONObject currentValues = jsonObject.getJSONObject((String) record);
                    final String testMethodName = currentValues.getString("name");
                    if (!coverage.containsKey(testMethodName)) {
                        coverage.put(testMethodName, new HashMap<>());
                    }
                    coverage.get(testMethodName).put(sourceClass, new ArrayList<>());
                    ((List) currentValues.get("statements")).stream()
                            .map(list -> ((Map) list).get("sl"))
                            .forEach(line ->
                                    coverage.get(testMethodName).get(sourceClass).add((Integer) line)
                            );
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        jsonTestTargets.clear();
        return coverage;
    }

    private static final Function<List<String>, String> LIST_OF_DEPENDENCIES_TO_ABS_PATH = list ->
            Arrays.stream(((URLClassLoader) ClassLoader.getSystemClassLoader())
                    .getURLs())
                    .filter(url -> list.stream().anyMatch(s -> url.getPath().contains(s)))
                    .map(URL::getPath)
                    .collect(Collectors.joining(AmplificationHelper.PATH_SEPARATOR));

    private static final List<String> CLOVER_DEPENDENCIES = Arrays.asList(
            "commons-io/commons-io/2.5/commons-io-2.5.jar",
            "org/openclover/clover/4.2.1/clover-4.2.1.jar"
    );

    private static final String ABSOLUTE_PATH_TO_CLOVER_DEPENDENCIES = LIST_OF_DEPENDENCIES_TO_ABS_PATH.apply(CLOVER_DEPENDENCIES);

    public volatile static Map<String, JSONObject> jsonTestTargets = new HashMap<>();

}
