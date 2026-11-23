# SQL-to-Native ODI Mapping — Starter Kit

**Experimental developer starter — not the production generator.** This folder contains a restricted Groovy SQL parser and a concrete Oracle Data Integrator 12c SDK adapter. It is intended to make the first version of a SQL-to-native-ODI generator easier to build, rather than merely describe an architecture.

**Runtime status:** The code is published as a reference implementation. Neither the parser test suite nor the ODI adapter has been executed or validated in the author's ODI environment for this release. **Do not run this against production ODI repositories.**

## Features and boundaries

| Area | Starter behavior |
| --- | --- |
| SQL | Explicit \`ALIAS.COLUMN [AS TARGET_COLUMN]\` projections |
| FROM | One source, optionally schema-qualified |
| JOIN | One \`INNER JOIN\` with \`alias.column = alias.column\` |
| WHERE | Simple comparisons joined by \`AND\` |
| Source models | Injected default or schema-to-model map |
| ODI mapping | Source and target \`DatastoreComponent\`, \`JoinComponent\`, \`FilterComponent\` |
| Target expressions | Map output aliases to target datastore column names |
| Integration | Control Append only; installed KMs/topology are environment-specific |
| Physical design | Creation, synchronization and validation attempted by SDK adapter |
| Repository write safety | Explicit \`writeEnabled: true\`; abort if mapping exists; rollback on exception |
| CTE, GROUP BY, HAVING, DISTINCT, UNION, MERGE | **Not implemented** in the public starter |

The root repository documents the much broader *private* engine. Its supported features must not be confused with this starter.

## Browse the code

- [Restricted SQL parser](core/RestrictedSqlParser.groovy) — ODI-independent parser and typed SQL plan.
- [ODI SDK adapter](odi/OdiSdkAdapter.groovy) — metadata lookup, native mapping component graph, target expression binding, physical design, transaction.
- [Configuration and entrypoint](odi/EntryPoint.groovy) — synthetic sample names and SQL; \`writeEnabled: false\` initially.
- [Single-file Groovy Procedure](dist/SqlToOdiStarter.groovy) — generated from the modules above; **this is the easiest entry point for ODI users**.
- [Parser contracts](tests/ParserContractTest.groovy) — positive and rejection cases.
- [Example SELECT](examples/simple_select.sql) and [INNER JOIN](examples/inner_join.sql).
- [Build script](build_odi_procedure.py) — assembles the Groovy Procedure from the OOP modules without dependencies.

## Try it step by step

1. **Use an isolated ODI 12c development environment** with a disposable project, folder, context, source models and target datastore. Never test this against existing production mappings.
2. To inspect parser behavior without ODI, from \`starter-kit/\` run \`groovy tests/ParserContractTest.groovy\` with a compatible local Groovy installation. These tests are provided, **not claimed as passed**.
3. For ODI Procedure use, open [\`dist/SqlToOdiStarter.groovy\`](dist/SqlToOdiStarter.groovy), copy its complete contents into a Groovy-language ODI Procedure task.
4. At the bottom of the Groovy script, edit \`starterConfig\` with actual **test** project, folder, model codes, context, mapping name and target datastore. Add a valid installed \`ikmName\` if needed by that physical target.
5. Supply one of the supported SQL patterns in \`starterSql\`. Projection aliases must correspond to column names on the target datastore; source and target tables must already be reverse-engineered in ODI.
6. **First leave \`writeEnabled: false\`** and review the parser plan summary. No repository operation is invoked by the entrypoint in this mode.
7. Only in your disposable ODI environment, change \`writeEnabled: true\`. This instructs the adapter to attempt a mapping creation. If an existing mapping has the same name, it fails rather than replacing it.
8. Confirm logical graph, target expressions, physical context, IKM, ODI validation errors, and **Simulation SQL**. Compare source query output with the generated SQL and, when loading, compare actual rows. ODI validation alone does not prove semantic equivalence.

To edit and regenerate the standalone Groovy file:

\`\`\`bash
python3 build_odi_procedure.py
\`\`\`

The generated output is \`dist/SqlToOdiStarter.groovy\`. It contains the same source classes, ODI imports and configuration entrypoint.

### Example

\`\`\`sql
SELECT S.ORDER_ID AS ORDER_ID,
       P.PRODUCT_NAME AS PRODUCT_NAME
FROM SALES_ORDERS S
INNER JOIN DIM_PRODUCT P ON S.PRODUCT_ID = P.PRODUCT_ID
WHERE S.STATUS = 'COMPLETED';
\`\`\`

The parser creates an intermediate plan; the SDK adapter resolves the ODI datastores, builds source → join → filter → target, and binds the selected output columns. This is a design-and-integration starting point, **not a guarantee of executable mappings** on any particular ODI installation.

## Extend it responsibly

The natural next steps are a real Oracle SQL grammar/parser; typed expression trees; safe alias and CTE scope resolution; aggregate/distinct/set component factories; target key and MERGE policy; configurable IKM and physical staging; and integration regression tests. Do not enable an unsupported SQL feature merely by stripping its syntax or transforming it into something different.

## Security, provenance and licensing

- No production worker, customer SQL, credentials, private paths, institution identifiers, actual model names or request queue details are present.
- All configuration and datastores in examples are fictional.
- The parser is deliberately **fail-fast**, but the ODI adapter must still be treated as privileged: do not expose it to arbitrary user-submitted SQL or public endpoints.
- The publicly available source was written as a separate educational starter; it does not reproduce the private production implementation.
- **No open-source license is currently included.** Public visibility is not a license to reuse, modify or distribute this code. Repository owners should choose an appropriately authorized license before encouraging third-party reuse.

## References

- [Oracle ODI Mapping SDK overview](https://blogs.oracle.com/dataintegration/odi-12c-mapping-sdk-overview)
- [Oracle ODI SDK Mapping JavaDoc](https://docs.oracle.com/middleware/122126/odi/reference-java-api/oracle/odi/domain/mapping/Mapping.html)
- [Oracle DatastoreComponent JavaDoc](https://docs.oracle.com/middleware/12212/odi/reference-java-api/oracle/odi/domain/mapping/component/DatastoreComponent.html)
