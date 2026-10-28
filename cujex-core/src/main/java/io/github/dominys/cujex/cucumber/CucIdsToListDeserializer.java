package io.github.dominys.cujex.cucumber;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static io.github.dominys.cujex.cucumber.CucConstants.ID_NAMES_SEPARATOR;

/**
 * Jackson deserializer that turns a comma-separated cell (e.g. {@code "a,b,c"})
 * into a {@code List<String>}.
 *
 * <p>Kept from the original source as an example of a custom deserializer you can
 * register via {@link ParameterTypes#configure(java.util.function.Consumer)} when a
 * single data-table cell should map to a list without JEXL array syntax.
 */
public class CucIdsToListDeserializer extends StdDeserializer<List<String>> {

    protected CucIdsToListDeserializer() {
        super(List.class);
    }

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        var split = p.getValueAsString().split(ID_NAMES_SEPARATOR);
        return Arrays.asList(split);
    }
}
