package eu.stamp_project.diff_test_selection.configuration;

import eu.stamp_project.diff_test_selection.diff.DiffComputer;
import eu.stamp_project.diff_test_selection.report.CSVReport;
import eu.stamp_project.diff_test_selection.report.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 01/02/19
 */
public class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private static final String SRC_FOLDER = "src";

    public static final String DEFAULT_OUTPUT_PATH_NAME = "testsThatExecuteTheChange.csv";

    public final String pathToFirstVersion;

    public final String pathToSecondVersion;

    public final String outputPath;

    public final ReportEnum reportFormat;

    public final String diff;

    public final boolean enhanced;

    public Configuration(String pathToFirstVersion, String pathToSecondVersion, String outputPath, String reportFormat, String pathToDiff, boolean enhanced) {
        this.pathToFirstVersion = pathToFirstVersion;
        this.pathToSecondVersion = pathToSecondVersion;
        this.reportFormat = ReportEnum.valueOf(reportFormat);
        if (pathToDiff == null || pathToDiff.isEmpty()) {
            LOGGER.warn("No path to diff file has been specified.");
            LOGGER.warn("I'll compute a diff file using the UNIX diff command");
            LOGGER.warn("You may encounter troubles.");
            LOGGER.warn("If so, please specify a path to a correct diff file");
            LOGGER.warn("or implement a new way to compute a diff file.");
            this.diff = new DiffComputer()
                    .computeDiffWithDiffCommand(new File(pathToFirstVersion + "/" + SRC_FOLDER), new File(pathToSecondVersion + "/" + SRC_FOLDER));
        } else {
            this.diff = this.readFile(pathToDiff);
        }
        if (outputPath == null || outputPath.isEmpty()) {
            this.outputPath = this.pathToFirstVersion +
                    (this.pathToFirstVersion.endsWith("/") ? "" : "/") +
                    DEFAULT_OUTPUT_PATH_NAME;
        } else {
            this.outputPath = outputPath;
        }
        this.enhanced = enhanced;
    }

    private String readFile(String pathToFileToRead) {
        final String nl = System.getProperty("line.separator");
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(pathToFileToRead)))) {
            reader.lines().forEach(
                    line -> builder.append(line).append(nl)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return builder.toString();
    }

    public enum ReportEnum {
        CSV(new CSVReport());
        public final Report instance;

        ReportEnum(Report instance) {
            this.instance = instance;
        }
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "pathToFirstVersion='" + pathToFirstVersion + '\'' +
                ", pathToSecondVersion='" + pathToSecondVersion + '\'' +
                ", outputPath='" + outputPath + '\'' +
                ", reportFormat=" + reportFormat +
                ", diff='" + diff + '\'' +
                ", enhanced=" + enhanced +
                '}';
    }
}
