package org.dominys.ittests;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit Platform suite that discovers every {@code .feature} under
 * {@code src/test/resources/features} and runs it with the glue code in
 * {@code org.dominys.ittests}.
 *
 * <p>This is the modern replacement for the source project's JUnit-4
 * {@code @RunWith(Cucumber.class)} runner. Run it with {@code mvn test}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "org.dominys.ittests")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber.html")
public class RunCucumberTest {
}
