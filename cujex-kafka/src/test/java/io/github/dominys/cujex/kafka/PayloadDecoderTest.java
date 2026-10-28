package io.github.dominys.cujex.kafka;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadDecoderTest {

    @Test
    void utf8DecodesBytesToText() throws Exception {
        assertThat(PayloadDecoder.utf8().decode("hello".getBytes(StandardCharsets.UTF_8))).isEqualTo("hello");
    }

    @Test
    void utf8DecodesNullToNull() throws Exception {
        assertThat(PayloadDecoder.utf8().decode(null)).isNull();
    }
}
