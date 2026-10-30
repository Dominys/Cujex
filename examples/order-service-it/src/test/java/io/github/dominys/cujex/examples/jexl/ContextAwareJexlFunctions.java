package io.github.dominys.cujex.examples.jexl;

import io.github.dominys.cujex.context.CujexJexlContext;
import io.github.dominys.cujex.jexl.CujexJexlFunctions;
import java.util.Objects;

/**
 * Use case 3: a <b>context-aware</b> function that reads the scenario context.
 *
 * <p>Unlike {@link OrderJexlFunctions}/{@link MathJexlFunctions} (which expose a
 * singleton via {@code functions() -> this}), this holder returns its own
 * {@link Class} from {@link #functions()}. That tells JEXL to build a fresh functor
 * for <em>every</em> evaluation, passing in the live {@link CujexJexlContext} through
 * the {@code (CujexJexlContext)} constructor - so the function can reach back into the
 * owning {@code ScenarioContext}.
 *
 * <p>Two constructors cooperate:
 * <ul>
 *     <li>the no-arg one is used by Spring to create the descriptor bean (it only
 *         answers {@link #namespace()}/{@link #functions()});</li>
 *     <li>the {@code (CujexJexlContext)} one is the one JEXL picks per evaluation to
 *         create the actual functor with the context injected.</li>
 * </ul>
 *
 * <pre>
 * Given I define scenario variables:
 *   | limit   | ${42}              |
 * Then I expect that ${ctx:ref('limit')} equals ${42}  # function resolved "limit" via the context
 * </pre>
 */
public class ContextAwareJexlFunctions implements CujexJexlFunctions {

    private final transient CujexJexlContext context;

    /** Descriptor constructor - used by Spring; holds no context. */
    public ContextAwareJexlFunctions() {
        this.context = null;
    }

    /** Functor constructor - used by JEXL per evaluation; receives the live context. */
    public ContextAwareJexlFunctions(CujexJexlContext context) {
        this.context = context;
    }

    @Override
    public String namespace() {
        return "ctx";
    }

    @Override
    public Object functions() {
        return ContextAwareJexlFunctions.class; // a Class -> JEXL injects the context
    }

    /**
     * {@code ${ctx:ref('created.id()')}} - resolves another expression by delegating
     * back to the scenario context, demonstrating access to the whole
     * {@code ScenarioContext} from inside a function.
     */
    public Object ref(String expression) {
        return Objects.requireNonNull(context, "ref(...) must run on a JEXL functor with an injected context")
                .scenarioContext().resolveField("${" + expression + "}");
    }
}
