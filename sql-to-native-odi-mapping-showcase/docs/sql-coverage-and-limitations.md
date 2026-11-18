# SQL coverage and limitations

The generator targets supported Oracle SQL query patterns, not arbitrary SQL. The matrix below reflects the implementation's documented behavior and testing scope.

| Structure | Status / notes |
| --- | --- |
| Standard datastore sources, aliases, repeated datastore usage | Implemented |
| INNER / LEFT OUTER / RIGHT OUTER JOIN | Implemented; semantics still require query-specific validation |
| Nested join groups | Implemented for supported patterns |
| WHERE and derived-table projections | Implemented |
| Expression / common Oracle functions | Implemented for tested patterns |
| SUM / COUNT / MIN / MAX / AVG, GROUP BY | Implemented |
| HAVING | Implemented using the native aggregate component |
| Nonrecursive CTEs and CTE reuse | Implemented, including independent branches for reused references |
| DISTINCT | Implemented for supported query shapes |
| UNION ALL | Implemented and tested on real mappings |
| UNION / INTERSECT / MINUS | SDK/parser path exists; less extensively validated than UNION ALL |
| SELECT * / alias.* | Restricted; only certain wildcard references to known CTE outputs can be safely expanded. Other cases fail fast |
| Top-level ORDER BY | Not represented as a native Sorter component; preprocessing may remove it. Output ordering is not preserved |
| Recursive CTE | Not supported |
| Correlated scalar subquery | Not generically supported |
| CROSS JOIN / FULL OUTER JOIN | Not supported as general runtime mapping patterns |
| Arbitrarily complex SQL / every Oracle dialect feature | Not guaranteed |

## Important validation boundary

A successful repository commit or successful logical/physical ODI validation does not prove the generated SQL is semantically identical to the source query. Complex cases should be compared at the data level (for example, source vs. simulation row counts and bidirectional set comparisons).

## Integration assumptions

The current target policy uses physical Oracle primary-key metadata to select MERGE or INSERT. If a MERGE is selected, the source output must include the required key columns. This does not establish source uniqueness automatically.

## Illustration vs. production behavior

The SQL example and diagram in this repository use fictional data objects and represent the **intended supported flow**. They are not screenshots of a tested instance of those fictional tables.
