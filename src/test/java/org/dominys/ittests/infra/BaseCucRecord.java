package org.dominys.ittests.infra;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Generic wrapper that pairs a reference id ({@code cucId}) with a typed payload.
 *
 * <p>This is the key enabler for "build an entity in a table, store it, reference
 * it later". A data table like:
 *
 * <pre>
 * | cucId     | name  | country |
 * | customer1 | Alice | PL      |
 * </pre>
 *
 * is mapped (per row) into a {@code BaseCucRecord&lt;Customer&gt;} where
 * {@code cucId = "customer1"} and {@code data} is the {@link org.dominys.ittests.sut.Customer}
 * built from the remaining columns. {@code @JsonUnwrapped} is what flattens the
 * payload fields next to {@code cucId} in the same row.
 *
 * <p>A step then stores {@code data} under {@code cucId} in the
 * {@link ScenarioContextStore}, making it reachable as {@code ${customer1}}.
 *
 * @param <T> the payload type for this row
 */
public class BaseCucRecord<T> {

    private String cucId;

    @JsonUnwrapped
    private T data;

    public String getCucId() {
        return cucId;
    }

    public void setCucId(String cucId) {
        this.cucId = cucId;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
