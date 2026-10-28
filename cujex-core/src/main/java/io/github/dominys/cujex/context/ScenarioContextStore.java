package io.github.dominys.cujex.context;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-scenario key/value store - the "memory" shared between steps of a single
 * scenario.
 *
 * <p><b>Why {@code @Scope("cucumber-glue")}:</b> cucumber-spring creates a fresh
 * instance of every glue-scoped bean for each scenario and discards it afterwards.
 * That means data stored here in one scenario can never leak into the next, giving
 * you automatic isolation without any manual cleanup.
 *
 * <p>Steps write results here (e.g. the id/object returned by a service call) and
 * read them back. The same backing map is also exposed to JEXL (see
 * {@link ScenarioContext#resolveField}), so anything put here under key
 * {@code "created"} becomes referenceable from a feature file as {@code ${created}}.
 */
@Component
@Scope("cucumber-glue")
public class ScenarioContextStore {

    // A scenario's steps run sequentially on one thread, but the map is wrapped for
    // safe publication. A plain HashMap (not ConcurrentHashMap) is used deliberately:
    // a cell can JEXL-resolve to null (e.g. a stored entity's null field), and
    // ConcurrentHashMap forbids null values - which would turn an ordinary
    // "define scenario variables" row into a NullPointerException.
    private final Map<String, Object> records = Collections.synchronizedMap(new HashMap<>());

    public void put(String key, Object value) {
        records.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) records.get(key);
    }

    public <T> Map<String, T> get(Collection<String> keys) {
        Map<String, T> selected = new HashMap<>();
        for (var key : keys) {
            selected.put(key, get(key));
        }
        return selected;
    }

    /** Immutable, null-tolerant snapshot of everything stored - used as the JEXL evaluation context. */
    public Map<String, Object> getAll() {
        synchronized (records) {
            return Collections.unmodifiableMap(new HashMap<>(records));
        }
    }
}
