package io.github.dominys.cujex.jexl;

import java.time.Duration;
import java.time.Instant;

/**
 * Custom functions exposed to JEXL expressions used inside feature files.
 *
 * <p>Every {@code public static} method here becomes callable from a
 * {@code ${...}} expression because {@link ScenarioContext} registers this class
 * under the default (anonymous) JEXL namespace. For example, a data-table cell
 * containing:
 *
 * <pre>{@code ${now('-PT1H')}}</pre>
 *
 * resolves by calling {@link #now(String)} with {@code "-PT1H"} and returns an
 * {@link Instant} one hour in the past.
 *
 * <p>To teach "how to add a function": just add another {@code public static}
 * method - no other wiring is required.
 *
 * <p>This class is intentionally <b>subclassable</b>: to add functions to the
 * default namespace from a consuming project, extend it and implement
 * {@link CujexJexlFunctions} (returning a blank namespace). The subclass keeps all
 * built-ins here (they are inherited) while adding its own - see
 * {@link CujexJexlFunctions} for a worked example.
 */
public class CucJexlFunctions {

    protected CucJexlFunctions() {
    }

    /** {@code ${now()}} - the current instant. */
    public static Instant now() {
        return Instant.now();
    }

    /**
     * {@code ${now('-PT1H')}} / {@code ${now('+PT30M')}} - current instant shifted
     * by an ISO-8601 {@link Duration}.
     */
    public static Instant now(String isoDuration) {
        return now().plus(Duration.parse(isoDuration));
    }

    /** {@code ${uuid()}} - a random UUID string, handy for unique test data. */
    public static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }
}
