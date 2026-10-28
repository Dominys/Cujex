package io.github.dominys.cujex.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioContextStoreTest {

    private final ScenarioContextStore store = new ScenarioContextStore();

    @Test
    void getReturnsWhatWasPut() {
        store.put("created", "value");

        assertThat(store.<String>get("created")).isEqualTo("value");
    }

    @Test
    void getReturnsNullForUnknownKey() {
        assertThat(store.<Object>get("missing")).isNull();
    }

    @Test
    void storesAndReturnsNullValues() {
        store.put("nullable", null);

        assertThat(store.<Object>get("nullable")).isNull();
    }

    @Test
    void getByKeysSelectsRequestedEntriesIncludingMissingAsNull() {
        store.put("a", 1);
        store.put("b", 2);

        var selected = store.<Integer>get(List.of("a", "missing"));

        assertThat(selected)
                .hasSize(2)
                .containsEntry("a", 1)
                .containsEntry("missing", null);
    }

    @Test
    void getAllReturnsSnapshotOfEverythingStored() {
        store.put("a", 1);
        store.put("b", 2);

        assertThat(store.getAll())
                .containsOnlyKeys("a", "b")
                .containsEntry("a", 1)
                .containsEntry("b", 2);
    }

    @Test
    void getAllSnapshotIsImmutable() {
        store.put("a", 1);
        var snapshot = store.getAll();

        assertThatThrownBy(() -> snapshot.put("b", 2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAllSnapshotIsDecoupledFromLaterWrites() {
        store.put("a", 1);
        var snapshot = store.getAll();

        store.put("b", 2);

        assertThat(snapshot).containsOnlyKeys("a");
    }
}
