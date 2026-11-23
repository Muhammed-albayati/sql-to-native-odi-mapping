#!/usr/bin/env python3
"""Assemble a self-contained Groovy ODI Procedure from the OOP modules."""
from pathlib import Path
root = Path(__file__).resolve().parent
core = (root / "core/RestrictedSqlParser.groovy").read_text(encoding="utf-8")
sdk = (root / "odi/OdiSdkAdapter.groovy").read_text(encoding="utf-8")
entry = (root / "odi/EntryPoint.groovy").read_text(encoding="utf-8")
imports = "\n".join(line for line in sdk.splitlines() if line.startswith("import "))
sdk_body = "\n".join(line for line in sdk.splitlines() if not line.startswith("import "))
bundle = ("// EXPERIMENTAL STARTER / NOT ODI-RUNTIME TESTED\n" +
          imports + "\n\n" + core + "\n\n" + sdk_body + "\n\n" + entry + "\n")
out = root / "dist/SqlToOdiStarter.groovy"
out.parent.mkdir(exist_ok=True)
out.write_text(bundle, encoding="utf-8")
print("Built", out.relative_to(root), len(bundle.splitlines()), "lines")