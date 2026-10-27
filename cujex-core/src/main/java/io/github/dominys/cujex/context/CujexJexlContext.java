package io.github.dominys.cujex.context;

import org.apache.commons.jexl3.MapContext;

import java.util.Map;

/**
 * The JEXL evaluation context used by {@link ScenarioContext#resolveField}.
 *
 * <p>It is a plain {@link MapContext} (so {@code ${created.id()}} still resolves
 * stored variables by name) that <b>additionally carries a back-reference to the
 * owning {@link ScenarioContext}</b>. This is what lets a custom function reach the
 * scenario's "brain" - e.g. to resolve another expression or read stored state.
 *
 * <h2>How a function receives it</h2>
 * JEXL inspects the object registered for a namespace. When that object is a
 * {@link Class} with a constructor that accepts the evaluation context, JEXL creates
 * a <em>fresh</em> functor for every evaluation, passing in the live context:
 * <pre>
 * public final class MyFns {
 *     private final CujexJexlContext ctx;
 *     public MyFns(CujexJexlContext ctx) { this.ctx = ctx; } // JEXL injects it here
 *     public Object ref(String expr) {
 *         return ctx.scenarioContext().resolveField("${" + expr + "}");
 *     }
 * }
 * </pre>
 *
 * <p>Because a new functor is built per evaluation against the current context, this
 * pattern is inherently thread-safe and always sees the scenario's latest state.
 */
public class CujexJexlContext extends MapContext {

    private final transient ScenarioContext scenarioContext;

    CujexJexlContext(Map<String, Object> variables, ScenarioContext scenarioContext) {
        super(variables);
        this.scenarioContext = scenarioContext;
    }

    /** The {@link ScenarioContext} that owns this evaluation - never {@code null}. */
    public ScenarioContext scenarioContext() {
        return scenarioContext;
    }
}
