package org.dominys.ittests.cucumber;

/**
 * Constants shared across the Cucumber + JEXL infrastructure.
 *
 * <p>Kept 1:1 with the original source project for familiarity.
 */
public final class CucConstants {

    /** Column name used in data tables to give a row a reference id. */
    public static final String CUC_REF_ID_FIELD = "cucId";

    /** Prefix that marks an entity reference. */
    public static final String ENTITY_PARAM_PREFIX = "$";

    /** Separator used by {@link CucIdsToListDeserializer} to split id lists. */
    public static final String ID_NAMES_SEPARATOR = ",";

    /** Placeholder a feature file can use to represent an explicit null. */
    public static final String NULL_PLACEHOLDER = "(NULL)";

    private CucConstants() {
    }
}
