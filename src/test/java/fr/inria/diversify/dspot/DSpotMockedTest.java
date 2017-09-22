package fr.inria.diversify.dspot;

import edu.emory.mathcs.backport.java.util.Collections;
import fr.inria.diversify.dspot.amplifier.value.ValueCreator;
import fr.inria.diversify.sosiefier.runner.InputConfiguration;
import fr.inria.diversify.sosiefier.runner.InputProgram;
import fr.inria.diversify.utils.AmplificationHelper;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import spoon.reflect.declaration.CtType;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 29/04/17
 */
public class DSpotMockedTest extends MavenAbstractTest {

	@Test
	public void test() throws Exception {

        /*
			Test the whole dspot procedure.
                It results with 24 methods: 18 amplified tests + 6 original tests.
         */
		ValueCreator.count = 0;
		AmplificationHelper.setSeedRandom(23L);
		InputConfiguration configuration = new InputConfiguration(pathToPropertiesFile);
		InputProgram program = new InputProgram();
		configuration.setInputProgram(program);
		DSpot dspot = new DSpot(configuration, 1);
		try {
			FileUtils.cleanDirectory(new File(configuration.getOutputDirectory()));
		} catch (Exception ignored) {

		}
		assertEquals(6, dspot.getInputProgram().getFactory().Class().get("info.sanaulla.dal.BookDALTest").getMethods().size());

		CtType<?> amplifiedTest = dspot.amplifyTest("info.sanaulla.dal.BookDALTest", Collections.singletonList("testAddBook"));

		assertEquals(7, amplifiedTest.getMethods().size());
	}

	@Override
	public String getPathToPropertiesFile() {
		return "src/test/resources/mockito/mockito.properties";
	}
}
