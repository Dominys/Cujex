package io.github.dominys.cujex.context;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final ConcurrentHashMap<String, Object> records = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        records.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) records.get(key);
    }

    public <T> Map<String, T> get(Collection<String> keys) {
        return keys.stream()
                .collect(Collectors.toMap(Function.identity(), this::get));
    }

    /** Immutable snapshot of everything stored - used as the JEXL evaluation context. */
    public Map<String, Object> getAll() {
        return Map.copyOf(records);
    }
}
