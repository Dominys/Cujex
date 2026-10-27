# Use case 6: contribute your own JEXL functions.
#
# Cujex ships a few built-in functions (now(), uuid()) in the default namespace.
# A consuming project can add more without touching the framework by registering
# Spring beans that implement CujexJexlFunctions - see TestConfig for the two beans
# wired up here (OrderJexlFunctions and MathJexlFunctions).
@Jexl @CustomFunctions
Feature: Custom JEXL functions contributed by the test suite

  Scenario: Functions added to the default namespace sit next to the built-ins

    # taxed(...) is a project function contributed to the DEFAULT namespace
    # (OrderJexlFunctions extends CucJexlFunctions), so it is called without a prefix
    # - exactly like the inherited built-in uuid().
    Given I define scenario variables:
      | gross    | ${taxed(100, 20)} |
      | expected | ${120}            |
      | id       | ${uuid()}         |

    # The contributed function produced the expected value...
    Then I expect that ${gross} equals ${expected}
    # ...and the inherited built-in still works alongside it.
    And I expect that ${id} equals ${id}

  Scenario: Functions registered under a custom namespace use a prefix

    # max(...) lives in the "math" namespace (MathJexlFunctions.namespace() = "math"),
    # so it is called as ${math:max(...)}, leaving the default namespace untouched.
    Given I define scenario variables:
      | biggest  | ${math:max(3, 7)} |
      | expected | ${7}              |

    Then I expect that ${biggest} equals ${expected}

  Scenario: A context-aware function can read the scenario context

    # ctx:ref(...) is a context-aware function (ContextAwareJexlFunctions): it returns
    # its own Class from functions(), so JEXL builds a fresh functor per evaluation and
    # injects the live CujexJexlContext. The function uses it to resolve another
    # expression back through the ScenarioContext.
    #
    # Resolution is write-through: "limit" is stored before "echoed" is resolved, so
    # ctx:ref('limit') sees it at execution point even though both share one table.
    Given I define scenario variables:
      | limit    | ${42}               |
      | echoed   | ${ctx:ref('limit')} |
      | expected | ${42}               |

    # The function read the stored "limit" by delegating to the scenario context.
    Then I expect that ${echoed} equals ${expected}
