package io.github.dominys.cujex.jexl;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CucJexlFunctionsTest {

    @Test
    void nowReturnsTheCurrentInstant() {
        var before = Instant.now();

        var now = CucJexlFunctions.now();

        assertThat(now).isBetween(before, Instant.now());
    }

    @Test
    void nowShiftsBackwardsByAnIsoDuration() {
        var reference = Instant.now();

        var shifted = CucJexlFunctions.now("-PT1H");

        assertThat(Duration.between(shifted, reference)).isCloseTo(Duration.ofHours(1), Duration.ofMinutes(1));
    }

    @Test
    void nowShiftsForwardsByAnIsoDuration() {
        var reference = Instant.now();

        var shifted = CucJexlFunctions.now("+PT30M");

        assertThat(Duration.between(reference, shifted)).isCloseTo(Duration.ofMinutes(30), Duration.ofMinutes(1));
    }

    @Test
    void nowRejectsANonIsoDuration() {
        assertThatThrownBy(() -> CucJexlFunctions.now("one hour"))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }

    @Test
    void uuidReturnsAParsableRandomValue() {
        var first = CucJexlFunctions.uuid();
        var second = CucJexlFunctions.uuid();

        assertThat(UUID.fromString(first)).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }
}
