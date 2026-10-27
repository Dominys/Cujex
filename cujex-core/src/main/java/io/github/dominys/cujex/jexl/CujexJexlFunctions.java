package io.github.dominys.cujex.jexl;

/**
 * Extension point for exposing <em>your own</em> functions to JEXL expressions.
 *
 * <p>Any Spring bean implementing this interface is discovered automatically and
 * its functions become callable from {@code ${...}} cells. There are two shapes:
 *
 * <h2>1. Add functions to the default (anonymous) namespace</h2>
 * Return a blank {@link #namespace()} to place your functions next to the built-in
 * ones ({@code now()}, {@code uuid()}), callable <em>without</em> a prefix:
 * <pre>{@code
 * public class OrderJexlFunctions extends CucJexlFunctions implements CujexJexlFunctions {
 *     public String namespace() { return ""; }   // default namespace
 *     public Object functions() { return this; }  // this bean holds the functions
 *     public int taxed(int amount, int percent) { return amount + amount * percent / 100; }
 * }
 * // usage in a feature: ${taxed(100, 20)}  and  ${now()} both work
 * }</pre>
 * Subclass {@link CucJexlFunctions} (as above) so the built-ins stay available -
 * registering a bare holder under the default namespace would otherwise hide them.
 *
 * <h2>2. Register a custom (prefixed) namespace</h2>
 * Return a non-blank {@link #namespace()} to expose functions under a prefix,
 * leaving the default namespace untouched:
 * <pre>{@code
 * public class MathJexlFunctions implements CujexJexlFunctions {
 *     public String namespace() { return "math"; }
 *     public Object functions() { return this; }
 *     public int max(int a, int b) { return Math.max(a, b); }
 * }
 * // usage in a feature: ${math:max(3, 7)}
 * }</pre>
 *
 * <p>{@link #functions()} may return either {@code this} (instance methods) or a
 * {@code Class} (its {@code public static} methods) - JEXL supports both.
 */
public interface CujexJexlFunctions {

    /**
     * The JEXL namespace prefix these functions live under.
     *
     * @return a blank/{@code null} value for the default namespace (no prefix), or a
     * prefix such as {@code "math"} to expose functions as {@code ${math:...}}.
     */
    String namespace();

    /**
     * The object holding the callable functions.
     *
     * @return {@code this} to expose the bean's {@code public} instance methods, or a
     * {@code Class} to expose its {@code public static} methods.
     */
    Object functions();
}
