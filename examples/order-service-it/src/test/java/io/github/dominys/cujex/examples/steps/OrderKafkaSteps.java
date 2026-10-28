package io.github.dominys.cujex.examples.steps;

import io.github.dominys.cujex.cucumber.ParameterTypes;
import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.ScenarioContextStore;
import io.github.dominys.cujex.kafka.KafkaMessage;
import io.github.dominys.cujex.kafka.KafkaProducerSupport;
import io.github.dominys.cujex.kafka.PayloadDecoder;
import io.github.dominys.cujex.kafka.ScenarioKafkaInbox;
import io.github.dominys.cujex.steps.BaseSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.dominys.cujex.examples.api.model.OrderEvent;
import io.github.dominys.cujex.examples.api.model.OrderTopics;
import io.github.dominys.cujex.examples.api.model.StatusUpdateCommand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static io.github.dominys.cujex.cucumber.ParameterTypes.OBJECT_MAPPER;

/**
 * Steps that exercise the Order service over Kafka - the read/write counterpart
 * to {@link OrderSteps}' REST steps, and the worked example of using the
 * {@code cujex-kafka} extension.
 *
 * <p>Two collaborators are autowired:
 * <ul>
 *     <li>{@link KafkaProducerSupport} - a suite-lifetime producer used to push a
 *         command the service consumes;</li>
 *     <li>{@link ScenarioKafkaInbox} - this scenario's private mailbox over the
 *         shared topic consumers. Autowiring it here is what makes a scenario
 *         "interested" in Kafka traffic; non-Kafka scenarios never instantiate
 *         this class, so the bus stays a no-op for them.</li>
 * </ul>
 *
 * <p>The isolation trick that makes parallel scenarios safe lives in
 * {@link #eventMatcher}: every expectation is matched by the fields the table
 * lists, and scenarios always include the {@code orderId} they created, so a
 * scenario only ever sees <em>its own</em> event even though one consumer feeds
 * them all.
 */
public class OrderKafkaSteps extends BaseSteps {

    private final KafkaProducerSupport producer;

    private final ScenarioKafkaInbox inbox;

    public OrderKafkaSteps(ScenarioContext scenarioContext, ScenarioContextStore scenarioStore,
                           KafkaProducerSupport producer, ScenarioKafkaInbox inbox) {
        super(scenarioContext, scenarioStore);
        this.producer = producer;
        this.inbox = inbox;
    }

    /**
     * How this suite turns a raw Kafka value into a domain object. The service emits
     * JSON, so this decoder reads an {@link OrderEvent} straight from the record's
     * bytes; a Protobuf suite would instead write {@code OrderEvent::parseFrom}. This
     * is the {@code cujex-kafka} extension point that makes the harness format-agnostic.
     */
    private static final PayloadDecoder<OrderEvent> ORDER_EVENT =
            bytes -> OBJECT_MAPPER.readValue(bytes, OrderEvent.class);

    // --- act ---------------------------------------------------------------

    /**
     * Publishes a {@link StatusUpdateCommand} the service consumes. The single
     * data row is JEXL-resolved cell-by-cell (so {@code ${created.id()}} works),
     * mapped to the command record, serialized to JSON and sent keyed by order id.
     */
    @When("I send a status update command:")
    public void sendStatusUpdateCommand(DataTable table) {
        var row = firstDataRow(table);
        var command = OBJECT_MAPPER.convertValue(
                ParameterTypes.resolveRow(row, scenarioContext()), StatusUpdateCommand.class);
        producer.send(OrderTopics.ORDER_STATUS_UPDATES, command.orderId(), toJson(command));
    }

    // --- assert -------------------------------------------------------------

    /**
     * Waits (via the inbox's Awaitility poll) for an order event whose JSON fields
     * match every column in the table, then deserializes it to an {@link OrderEvent}
     * and stores it under {@code resultKey} for later assertions.
     */
    @Then("I await an order event matching and store result in {word}:")
    public void awaitOrderEvent(String resultKey, DataTable table) {
        KafkaMessage message = inbox.awaitMessage(eventMatcher(firstDataRow(table)));
        scenarioStore().put(resultKey, message.value(ORDER_EVENT));
    }

    /** Builds a predicate that accepts a message whose JSON fields equal the resolved criteria. */
    private Predicate<KafkaMessage> eventMatcher(Map<String, String> criteria) {
        var expected = new LinkedHashMap<String, String>();
        criteria.forEach((field, cell) ->
                expected.put(field, String.valueOf(resolveCell(cell))));
        return message -> {
            var fields = parse(message.valueAsString());
            return expected.entrySet().stream().allMatch(entry ->
                    Objects.equals(entry.getValue(), String.valueOf(fields.get(entry.getKey()))));
        };
    }

    private static Map<String, String> firstDataRow(DataTable table) {
        var rows = table.asMaps(String.class, String.class);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Expected a header row and at least one data row");
        }
        return rows.getFirst();
    }

    private static Map<String, Object> parse(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Message value is not JSON: " + json, e);
        }
    }

    private static String toJson(Object value) {

        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + value, e);
        }
    }
}
