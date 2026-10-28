package io.github.dominys.cujex.cucumber;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CucIdsToListDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private List<String> deserialize(String cell) throws Exception {
        try (var parser = mapper.getFactory().createParser('"' + cell + '"')) {
            parser.nextToken();
            return new CucIdsToListDeserializer().deserialize(parser, null);
        }
    }

    @Test
    void splitsACommaSeparatedCellIntoAList() throws Exception {
        assertThat(deserialize("a,b,c")).containsExactly("a", "b", "c");
    }

    @Test
    void wrapsASingleValueInAOneElementList() throws Exception {
        assertThat(deserialize("single")).containsExactly("single");
    }

    @Test
    void keepsEmptySegmentsBetweenSeparators() throws Exception {
        assertThat(deserialize("a,,c")).containsExactly("a", "", "c");
    }
}
