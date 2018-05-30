package eu.stamp_project.utils.sosiefier;

import eu.stamp_project.utils.AmplificationHelper;
import eu.stamp_project.utils.DSpotUtils;
import spoon.reflect.factory.Factory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

/**
 * The input configuration class encapsulates all the data and associated behavior we obtain from the input properties
 * given by the user.
 * Created by marcel on 8/06/14.
 */
//TODO @Deprecated
public class InputConfiguration {

    /**
     * Internal properties
     */
    protected Properties prop;

    /**
     * resulting input program from the input configuration
     */
    @Deprecated
    private InputProgram inputProgram;

    public InputConfiguration() {
        prop = new Properties();
        setDefaultProperties();
    }

    public InputConfiguration(InputStream stream) throws IOException {
        prop = new Properties();
        setDefaultProperties();
        prop.load(stream);

        this.setPathToClasses(DSpotUtils.shouldAddSeparator.apply(prop.getProperty("classes", "target/classes")));
        this.setPathToTestClasses(DSpotUtils.shouldAddSeparator.apply(prop.getProperty("testclasses", "target/test-classes")));

        if (prop.getProperty("systemProperties") != null) {
            Arrays.stream(prop.getProperty("systemProperties").split(","))
                    .forEach( systemProperty -> {
                                String[] keyValueInArray = systemProperty.split("=");
                                System.getProperties().put(keyValueInArray[0], keyValueInArray[1]);
                            });
        }
        this.absolutePathToProjectRoot = DSpotUtils.computeProgramDirectory.apply(this);
    }

    public InputConfiguration(File project, File srcDir, File testDir, File classesDir, File testClassesDir,
			File tempDir, String filter, File mavenHome) throws IOException {
		this();
		getProperties().setProperty("project", project.getAbsolutePath());
		getProperties().setProperty("src", getRelativePath(srcDir));
		getProperties().setProperty("testSrc", getRelativePath(testDir));
//		getProperties().setProperty("testResources", getRelativePath(testResourcesDir));
//		getProperties().setProperty("srcResources", getRelativePath(srcResourcesDir));
		getProperties().setProperty("maven.home", mavenHome.getAbsolutePath());
		getProperties().setProperty("classes", getRelativePath(classesDir));
		getProperties().setProperty("tmpDir", getRelativePath(tempDir));
		if (filter != null) {
			getProperties().setProperty("filter", filter);
		}
		getProperties().setProperty("javaVersion", "8");
	}

    private String getRelativePath(File path) {
    	String base = getProperties().getProperty("project");
    	String relative = new File(base).toURI().relativize(path.toURI()).getPath();
		return relative;
	}

    public InputConfiguration(String file) throws IOException {
        this(new FileInputStream(file));
    }

    /**
     * Return the internal properties
     *
     * @return Proprties instance
     */
    public Properties getProperties() {
        return prop;
    }

    /**
     * Gets the specific value of a property
     *
     * @param key Key to the value
     * @return A string with the value                                                      g
     */
    public String getProperty(String key) {
        return getProperties().getProperty(key);
    }

    /**
     * Gets the specific value of a property
     *
     * @param key          Key to the value
     * @param defaultValue Default value to set
     * @return A string with the value
     */
    public String getProperty(String key, String defaultValue) {
        return getProperties().getProperty(key, defaultValue);
    }

    /**
     * The input program we are sosieficating.
     */
    @Deprecated
    public InputProgram getInputProgram() {
        return inputProgram;
    }

    /**
     * The input program we are sosieficating.
     */
    @Deprecated
    public void setInputProgram(InputProgram inputProgram) {
        this.inputProgram = inputProgram;
    }

    /**
     * Gets the project path. The project path is the parent directory where all files concerning a project are.
     *
     * @return String with the path
     */
    public String getProjectPath() {
        return getAbsolutePath(getProperty("project"));
    }

    /**
     * Returns the full path of the production (source) code of a project.
     *
     * @return String with the path
     */
    public String getRelativeSourceCodeDir() {
        return prop.getProperty("src");
    }

    /**
     * Returns the full path of the test (source) code of a project.
     *
     * @return String with the path
     */
    public String getRelativeTestSourceCodeDir() {
        return prop.getProperty("testSrc", "src/test/java");
    }


    /**
     * Returns the path of the built classes
     *
     * @return String with the path
     */
    public String getClassesDir() {
        return  prop.getProperty("classes");
    }

    /**
     * Returns the output path
     * @return
     */
    public String getOutputDirectory() {
        return prop.getProperty("outputDirectory", "output");
    }

    protected void setDefaultProperties() {
        prop.setProperty("src", "src/main/java");
        prop.setProperty("testSrc", "src/test/java");
        prop.setProperty("classes", "target/classes");
        prop.setProperty("javaVersion", "5");
        prop.setProperty("tmpDir", "tmpDir"); // TODO Checks usage
        prop.setProperty("outputDirectory", "output");
        prop.setProperty("timeOut", "-1"); // TODO Checks usage
        prop.setProperty("logLevel", "2"); // TODO Checks usage
        prop.setProperty("builder", "maven");
        prop.setProperty("pom", "/pom.xml");
    }

    protected String getAbsolutePath(String path) {
        Path p = Paths.get(path);
        if ( new File(path).exists() || p.isAbsolute() ) {
            return path;
        }
        return p.normalize().toString().replace(File.separator, "/");
    }

    /**
     * Gets the temporary directory for all operations
     * @return
     */
    public String getTempDir() {
        return getAbsolutePath(getProperty("tmpDir"));
    }

    @Deprecated
    public static InputProgram initInputProgram(InputConfiguration inputConfiguration) throws IOException {
        InputProgram inputProgram = new InputProgram();
        inputConfiguration.setInputProgram(inputProgram);
        inputProgram.setProgramDir(inputConfiguration.getProperty("project"));
        inputProgram.setRelativeSourceCodeDir(inputConfiguration.getRelativeSourceCodeDir());
        inputProgram.setRelativeTestSourceCodeDir(inputConfiguration.getRelativeTestSourceCodeDir());

        inputProgram.setTransformationPerRun(
                Integer.parseInt(inputConfiguration.getProperty("transformation.size", "1")));

        //Path to pervious transformations made to this input program
        inputProgram.setPreviousTransformationsPath(
                inputConfiguration.getProperty("transformation.directory"));

        inputProgram.setClassesDir(inputConfiguration.getProperty("classes"));

        inputProgram.setCoverageDir(inputConfiguration.getProperty("jacoco"));

        inputProgram.setJavaVersion(Integer.parseInt(inputConfiguration.getProperty("javaVersion", "6")));

        return inputProgram;
    }

    @Override
    public String toString() {
    	String toReturn = "";
    	Properties prop = this.getProperties();
		Set keys = prop.keySet();
		for (Object key : keys) {
			toReturn += key + ": " + prop.getProperty((String) key)+ "\n";
		}
		toReturn += "ClassesDir: " + this.getClassesDir()+ "\n";
		toReturn += "outputDirectory: " + this.getOutputDirectory()+ "\n";
		toReturn += "projectPath: " + this.getProjectPath()+ "\n";
		toReturn += "relativeSourceCodeDir: " + this.getRelativeSourceCodeDir()+ "\n";
		toReturn += "relativeTestSourceCodeDir: " + this.getRelativeTestSourceCodeDir()+ "\n";
		toReturn += "TempDir: " + this.getTempDir()+ "\n";
		return toReturn;
    }

    private Factory factory;

    /**
     * Spoon factory to process all AST elements
     */
    public Factory getFactory() {
        return factory;
    }

    public void setFactory(Factory factory) {
        this.factory = factory;
    }

    private String absolutePathToProjectRoot;

    /**
     * This method return the absolute path to the project.
     * If the project is multi-modules, the returned path is the path to the specified targetModule properties
     * @return absolute path to the project root
     */
    public String getAbsolutePathToProjectRoot() {
        return absolutePathToProjectRoot;
    }

    /**
     * set the absolute path to the root of the project, and add a / at the end if needed
     * @param absolutePathToProjectRoot
     */
    public void setAbsolutePathToProjectRoot(String absolutePathToProjectRoot) {
        this.absolutePathToProjectRoot = DSpotUtils.shouldAddSeparator.apply(absolutePathToProjectRoot);
    }

    private String pathToClasses;

    public String getPathToClasses() {
        return pathToClasses;
    }

    public void setPathToClasses(String pathToClasses) {
        this.pathToClasses = pathToClasses;
    }

    private String pathToTestClasses;

    public String getPathToTestClasses() {
        return pathToTestClasses;
    }

    public void setPathToTestClasses(String pathToTestClasses) {
        this.pathToTestClasses = pathToTestClasses;
    }

    public String getClasspathClassesProject() {
        return this.getAbsolutePathToProjectRoot() + this.getPathToClasses()
                + AmplificationHelper.PATH_SEPARATOR +
                this.getAbsolutePathToProjectRoot() + this.getPathToTestClasses();
    }
}