package io.github.dominys.cujex;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring entry point for Cujex core.
 *
 * <p>A consuming suite imports this one class to pull in every Cujex-managed
 * bean - {@code ScenarioContext}, {@code ScenarioContextStore} and the glue-scoped
 * machinery - without having to know the internal package layout:
 *
 * <pre>{@code
 * @Configuration
 * @Import(CujexCoreConfig.class)
 * public class TestConfig {
 *     // ... add your own service clients / fixtures here
 * }
 * }</pre>
 *
 * <p>The scan is confined to {@code io.github.dominys.cujex} so it only ever picks
 * up framework beans; your domain beans stay under your own packages and are wired
 * however your suite prefers.
 */
@Configuration
@ComponentScan("io.github.dominys.cujex")
public class CujexCoreConfig {
}
