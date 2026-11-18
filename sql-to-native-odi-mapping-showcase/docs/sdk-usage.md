# ODI SDK components and methods used

The automation combines ODI SDK APIs with a private SQL interpretation and mapping-planning layer. The ODI SDK provides native component operations; it does not itself parse arbitrary SQL into complete ODI mappings.

The list below names classes and methods used by the implementation. It is **not** a copy of the private Groovy source and does not include the dependency-resolution or graph-generation algorithm.

## Repository objects and mapping setup

| API | Role in the solution |
| --- | --- |
| `Mapping` | Represent a new native ODI mapping within a resolved project folder |
| `IOdiDataStoreFinder.findByName(...)` | Locate datastores in the appropriate ODI models |
| `DatastoreComponent` | Add source or target datastore components to the mapping |
| `IMapComponent.connectTo(...)` | Connect components to form the mapping graph |

## Logical transformation components

| SQL concept | ODI SDK API | Usage |
| --- | --- | --- |
| JOIN | `JoinComponent.joinSources(...)` | Combine two already-resolved graph branches |
| Join type | `JoinComponent.setJoinType(...)` | Configure inner/left/right outer join behavior |
| WHERE or branch predicate | `FilterComponent` | Represent filter conditions at the appropriate point in the flow |
| Calculated projection | `ExpressionComponent.addExpression(...)` | Define calculated output expressions |
| GROUP BY / aggregation | `AggregateComponent.addAttribute(...)` | Define grouped and aggregated outputs |
| HAVING | `AggregateComponent.setHavingText(...)` | Attach a native HAVING predicate to an aggregate component |
| DISTINCT | `DistinctComponent.addAttribute(...)` | Define outputs of a distinct component |
| UNION ALL and supported set operators | `SetComponent.createSet(...)`, `SetComponent.addSetAttribute(...)` | Create a set node and its aligned output attributes |

A CTE is **not** a dedicated ODI SDK component in this approach. Its SQL body is represented by a combination of native components such as Expression, Aggregate, Distinct, or Set, depending on the query shape.

## Target integration and physical design

| API | Role in the solution |
| --- | --- |
| `DatastoreComponent.setIntegrationType(...)` | Select the target integration mode |
| Attribute key/insert/update indicators | Express target key and write behavior |
| `Mapping.createPhysicalDesign(...)` | Create a physical design for the mapping and context |
| Physical design `syncWithLogicalMapping(...)` | Align physical nodes with logical graph changes |
| Target physical node `setIKMByName(...)` | Assign the selected Integration Knowledge Module |
| `Mapping.validate(...)` | Check logical consistency |
| Physical design `validate(...)` | Check physical consistency |
| Repository entity manager `persist(...)` | Persist mapping objects |
| Transaction manager `commit(...)` / `rollback(...)` | Commit successful work or roll back failures |

## Where the engineering work lives

These methods are building blocks. The private implementation is responsible for deciding **which** objects to create, **when** to create them, **how** to connect them, and **which SQL expressions** belong on each component.

Examples of nontrivial orchestration include alias scoping, CTE reuse, outer-join predicate placement, preservation of explicit grouping grain, target column alignment, and fail-fast validation of unsupported query shapes.

No production Groovy classes, connection settings, or runnable generator code are distributed in this repository.
