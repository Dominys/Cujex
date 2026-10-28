package io.github.dominys.cujex.steps;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the shared addressing primitives that keep the creation and
 * validation decomposed-table grammars from drifting apart.
 */
class DecompositionSupportTest {

    @Test
    void blankRecognisesNullEmptyAndWhitespace() {
        assertThat(DecompositionSupport.isBlank(null)).isTrue();
        assertThat(DecompositionSupport.isBlank("")).isTrue();
        assertThat(DecompositionSupport.isBlank("   ")).isTrue();
        assertThat(DecompositionSupport.isBlank("x")).isFalse();
    }

    @Test
    void stripBracketsUnwrapsSelectorCells() {
        assertThat(DecompositionSupport.stripBrackets("[sku=widget]")).isEqualTo("sku=widget");
        assertThat(DecompositionSupport.stripBrackets("  [0]  ")).isEqualTo("0");
    }

    @Test
    void stripBracketsTrimsNonSelectorCells() {
        assertThat(DecompositionSupport.stripBrackets("  plain  ")).isEqualTo("plain");
    }

    @Test
    void stripBracketsLeavesHalfBracketedCellsAlone() {
        assertThat(DecompositionSupport.stripBrackets("[open")).isEqualTo("[open");
        assertThat(DecompositionSupport.stripBrackets("close]")).isEqualTo("close]");
    }

    @Test
    void requireScopeReturnsActiveScope() {
        assertThat(DecompositionSupport.requireScope("items", ".field")).isEqualTo("items");
    }

    @Test
    void requireScopeFailsWhenNoScopeOpen() {
        assertThatThrownBy(() -> DecompositionSupport.requireScope(null, ".field"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".field")
                .hasMessageContaining("no open scope");
    }

    @Test
    void scopePathNestsRelativeNamesInsideOpenScope() {
        assertThat(DecompositionSupport.scopePath("items", ".price", ".price")).isEqualTo("items.price");
    }

    @Test
    void scopePathTreatsPlainNamesAsRootPaths() {
        assertThat(DecompositionSupport.scopePath("items", "customer", "customer")).isEqualTo("customer");
    }

    @Test
    void scopePathFailsWhenRelativeNameHasNoScope() {
        assertThatThrownBy(() -> DecompositionSupport.scopePath(null, ".price", ".price"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no open scope");
    }
}
