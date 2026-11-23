# Object-oriented design showcase

This document illustrates a **possible OOP decomposition** of the private SQL-to-native-ODI automation. It is a **design proposal**, not a claim that the existing production worker has been refactored or tested in this form.

## Why separate responsibilities?

The original worker combines request processing, SQL analysis, source and target metadata resolution, mapping-graph construction, physical configuration, and transaction handling. Separating these concerns can improve unit testing, maintainability, and future SQL-feature extensions without requiring a rewrite of the mapping semantics.

### Proposed boundaries

| Layer / service | Responsibility | Publication status |
| --- | --- | --- |
| Request coordinator | Accept and validate mapping name, target and SQL; manage request lifecycle | Private |
| SQL analyzer / parser | Interpret Oracle SQL constructs and alias scopes | Private |
| Mapping plan | Describe operations and ordered graph dependencies without referencing ODI SDK objects | Public illustration only |
| Plan validator | Reject incomplete plans and invalid graph references before side effects | Public illustration only |
| Repository resolver | Locate ODI project, folders, models, datastores and target metadata | Private |
| Native mapping adapter | Convert planned operations into ODI SDK mapping components and wire graph edges | Private |
| Integration strategy | Determine target key behavior and appropriate INSERT/MERGE strategy | Private |
| Physical design and transaction coordinator | Configure KMs, validate SDK objects, persist or roll back | Private |

The private implementation would typically depend on interfaces/ports rather than a single concrete class so SQL interpretation can be tested without a running ODI repository.

## Conceptual control flow

```text
Mapping request
      |
      v
SQL analyzer (private) ---> MappingPlan
                                  |
                                  v
                         MappingPlanValidator
                                  |
                                  v
                       MappingGenerationService
                                  |
                                  v
                       MappingComponentPort
                          /             \
                         /               \
        Console demo adapter      ODI SDK adapter (private)
        (prints plan only)        (real native components)
```

## What the included Groovy example demonstrates

[OopArchitectureDemo.groovy](../examples/oop-showcase/OopArchitectureDemo.groovy) is an **ODI-independent** illustration of:

1. A small, immutable-ish plan data model (`MappingPlan`, `PlanStep`, `StepKind`)
2. Dependency and required-property validation before adapter calls
3. Constructor-injected `MappingComponentPort` (dependency inversion)
4. An orchestration class (`MappingGenerationService`)
5. A console adapter that prints fictional graph steps without creating a mapping

It deliberately **does not** contain a SQL parser, mapping-graph construction algorithm, CTE reuse implementation, JDBC/ODI connectivity, credentials, project codes, Knowledge Module configuration, or a runnable production worker. Its condition/measure strings are **display-only metadata**, not executed SQL.

### About ODI SDK integration

A real private adapter would translate typed plan nodes into ODI objects using methods such as `DatastoreComponent`, `JoinComponent.joinSources(...)`, `FilterComponent`, `AggregateComponent`, `IMapComponent.connectTo(...)` and other SDK APIs documented in [ODI SDK usage](sdk-usage.md).

Correct SQL-to-ODI translation also requires alias resolution, join/predicate semantics, CTE branch isolation, expression binding, target-column matching, physical design, and transaction safety. The public example implements **none of those production algorithms**.

## Verification and risk boundaries

- **No ODI test performed:** These files are a publication-only architecture example, not a validated deployment.
- **No SQL equivalence claim:** Printing a plan does not prove generated SQL or target data would match.
- **No runtime integration:** No ODI SDK, Oracle JDBC, configuration, repository, agent, or KM is used in the sample.
- **Production code remains private:** OOP refactoring is a separate development stream and must be regression-tested against the existing tested worker before adoption.

For production refactoring, verify compiled/runtime compatibility with the configured ODI Groovy and Java versions, followed by CTE reuse, outer joins, aggregates/HAVING, UNION ALL, PK-based MERGE, physical design validation, and data-level parity tests.
