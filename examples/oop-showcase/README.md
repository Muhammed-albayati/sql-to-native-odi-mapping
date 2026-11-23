# OOP architecture demonstration (illustrative only)

This folder contains a **small standalone Groovy design example**, not a SQL-to-ODI converter.

`OopArchitectureDemo.groovy` models an already-interpreted, fictional mapping plan. A validator checks graph references, then a console adapter **prints** a sequence of component descriptors. It does not read SQL files, access an ODI repository, create native ODI components, persist anything, or load data.

If you have a compatible local Groovy installation, try:

```bash
groovy OopArchitectureDemo.groovy
```

Expected console output begins with `DEMO mapping: MAP_PRODUCT_SALES_DEMO` and ends with `DEMO complete: graph checks only (no ODI mapping created)`.

**Status:** Provided as illustrative source; not executed in the author's ODI runtime for this publication. This example uses only standard Groovy/JDK types, but installation-specific runtime compatibility has not been verified.

See [the OOP design document](../../docs/oop-design.md) for architectural boundaries and what stays private.
