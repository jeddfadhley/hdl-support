# HDL Support for IntelliJ

[![Build](https://github.com/jeddfadhley/hdl-support/actions/workflows/build.yml/badge.svg)](https://github.com/jeddfadhley/hdl-support/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A free, open-source plugin that adds **Verilog**, **SystemVerilog** and **VHDL** support to IntelliJ IDEA
and other IntelliJ-based IDEs.

<!-- Plugin description -->
Free Verilog, SystemVerilog and VHDL support for IntelliJ-based IDEs.

- Syntax highlighting for `.v`, `.vh`, `.sv`, `.svh`, `.vhd` and `.vhdl` files that follows your colour scheme
- Line and block commenting with Ctrl+/ and Ctrl+Shift+/
- Lint warnings and errors from [Verilator](https://www.veripool.org/verilator/) (Verilog/SystemVerilog)
  and [GHDL](https://github.com/ghdl/ghdl) (VHDL), if you have them installed

Configure the linters under **Settings | Tools | HDL Support**.
<!-- Plugin description end -->

## Installation

- **From the IDE:** Settings → Plugins → Marketplace, search for "HDL Support", then click Install.
- **From a release:** download the zip from [Releases](https://github.com/jeddfadhley/hdl-support/releases),
  then go to Settings → Plugins → ⚙ → Install Plugin from Disk.

Requires IntelliJ IDEA 2025.3 or later (or another IntelliJ-based IDE of the same version).

## Linting (optional)

Highlighting works on its own. For lint squiggles, install one or both tools:

| Language | Tool | Install (macOS / Debian-Ubuntu) |
|---|---|---|
| Verilog / SystemVerilog | Verilator | `brew install verilator` / `apt install verilator` |
| VHDL | GHDL | `brew install ghdl` / `apt install ghdl` |

The plugin finds them on your `PATH`. If it can't, set the paths under **Settings | Tools | HDL Support**.
That page also has enable toggles, extra arguments, include directories (`-I`) and the VHDL standard
(`--std=08` or `93`).

Lint runs in the background as you edit, including on unsaved changes. If a tool is missing, you get one
notification per session and the plugin stops trying.

## Building from source

You need JDK 21.

```bash
./gradlew test          # run the tests (Verilator/GHDL not required)
./gradlew runIde        # start a sandbox IDE with the plugin, opened on sample files
./gradlew buildPlugin   # build the installable zip into build/distributions/
./gradlew verifyPlugin  # check compatibility with recent IDE versions
```

The lexers are generated from the JFlex grammars in `src/main/grammars/` by `./gradlew generateLexers`,
which runs automatically before compilation. See [CLAUDE.md](CLAUDE.md) for the project layout and
conventions.

## Contributing

Issues and pull requests are welcome. Please add a lexer test for any new token rule,
and run `./gradlew test` before opening a PR.

## License

[MIT](LICENSE)
