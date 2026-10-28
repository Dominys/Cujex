package io.github.dominys.cujex.steps;

/**
 * Shared addressing primitives for the decomposed-table grammar used by both the
 * creation ({@link TemplateSteps}) and validation ({@link ModelValidationSteps})
 * steps.
 *
 * <p>Both sides accept the same column grammar - {@code x:} opens an object scope,
 * {@code x[]} / {@code .child[]} open (root / nested) collection scopes whose cell is
 * a {@code [selector]}, and {@code .field} is relative to the open scope. The small
 * pieces that parse selectors and join scope paths live here so the two sides cannot
 * drift: the JavaDoc contract that validation selectors are "the addressing subset of
 * the creation ones" is enforced by a single implementation rather than by hand.
 */
final class DecompositionSupport {

    private DecompositionSupport() {
    }

    /** A {@code null}, empty or whitespace-only cell - treated as "inherit / continue". */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Unwraps a {@code [selector]} cell to its inner {@code selector}; other cells pass through trimmed. */
    static String stripBrackets(String cell) {
        var trimmed = cell.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }

    /** Returns the active {@code scope}, or fails if a relative ({@code .}-prefixed) column has none open. */
    static String requireScope(String scope, String header) {
        if (scope == null) {
            throw new IllegalStateException(
                    "Relative column '%s' has no open scope - precede it with a '...:' or '...[]' opener"
                            .formatted(header));
        }
        return scope;
    }

    /**
     * Joins a (possibly relative) collection/field {@code name} onto the active scope:
     * a {@code .}-prefixed name nests inside the open scope, a plain name is a full
     * root path.
     */
    static String scopePath(String scope, String name, String header) {
        return name.startsWith(".") ? requireScope(scope, header) + name : name;
    }
}
