# Architecture and design decisions

This document describes the conceptual architecture of a SQL-to-native-ODI-mapping generator without releasing its production implementation.

## 1. Request interpretation

The generator requires a **mapping name**, a **target datastore**, and a **SELECT SQL query**. An orchestration layer hands these values to a Groovy worker running in an ODI environment. The request-handling implementation is not included in this repository.

## 2. SQL parsing and analysis

The SQL is normalized, inspected, and decomposed into logical query elements: data sources, aliases, joins, predicates, projections, aggregations, CTEs, and supported set operators.

Key engineering concerns include:

- Preserving join semantics and the scope of SQL aliases
- Resolving dependency order for nested and reused query elements
- Isolating separate uses of the same CTE so ODI branches do not unintentionally share references
- Preventing unsupported constructs from silently producing misleading mappings

These concerns are handled by the private parser and graph-planning implementation, not by an individual ODI SDK method.

## 3. Repository and datastore resolution

Source and target names are matched to metadata in an existing ODI repository. This involves looking up project/folder context, ODI models, and datastore objects. Model-routing and folder-placement policies are specific to each ODI installation and are intentionally not published as a fixed configuration.

An ODI model is where a datastore is resolved; a project folder is where a mapping is stored. These should not be confused.

## 4. Native mapping construction

The interpreted query is represented using ODI SDK components:

```text
Source datastores
       |
       v
Filter / Expression / Aggregate / Distinct (as needed)
       |
       v
Join or supported Set operation (as needed)
       |
       v
Target datastore
```

Actual mappings are dependency graphs, not necessarily linear pipelines. CTEs, derived tables, outer joins, and repeated references can require multiple branches. The graph-construction algorithm is not distributed.

## 5. Target and physical configuration

The target definition is inspected, including database primary-key metadata. The current integration policy selects MERGE when an applicable physical primary key exists and INSERT otherwise. A MERGE requires its key columns to appear in the query output; the generator does not silently downgrade a missing-key MERGE to INSERT.

The mapping's physical design is created and synchronized with its logical mapping. The target Knowledge Module is selected according to the integration strategy and environment. Production model codes, folder paths, connection identifiers, and operational conventions are intentionally omitted.

## 6. Validation and persistence

Logical and physical validation are performed before repository changes are committed. Failures trigger error handling and rollback where appropriate. A successful SDK validation is **not proof of SQL-result equivalence**; data-level comparisons are separate checks.

## Deployment assumptions

- An existing, correctly configured ODI 12c repository
- Accessible ODI project, models, and compatible source/target datastores
- Appropriate repository permissions and physical-design context
- Available Knowledge Modules suited to the target integration strategy
- Database metadata access for target-table inspection

This repository does not supply the worker, a universal install procedure, or an ODI package/scenario. It is not a deployable distribution.
