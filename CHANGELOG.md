# Changelog

## [Unreleased]

### Added

- Structure view for Verilog, SystemVerilog and VHDL: modules, entities, architectures and packages with their ports, parameters, signals, instances, processes, functions and named generate blocks
- Go to definition (Ctrl+click) from module and entity instances, architectures, base classes, package imports and use clauses, across files and between Verilog and VHDL
- Code folding for modules, entities, processes, `always` blocks, `begin`/`end`, `case`, `generate`, functions, port lists and block comments
- Matching of `()`, `[]` and `{}`, and of keyword pairs such as `begin`/`end`, `module`/`endmodule` and `if`/`elsif`/`else`/`end if`, including Move Caret to Matching Brace
- Tools | Show Synthesis Stats: synthesises the Verilog module at the caret with Yosys and shows the statistics in a Yosys tool window, with the Yosys path and synthesis command configurable in Settings | Tools | HDL Support

## [0.1.0] - 2026-10-16

### Added

- Syntax highlighting for Verilog, SystemVerilog and VHDL that follows your colour scheme, with colour settings pages
- Line and block commenting (`//` and `/* */` for Verilog, `--` and `/* */` for VHDL)
- Verilator lint warnings and errors for Verilog/SystemVerilog files
- GHDL syntax-check errors for VHDL files
- Settings page under Settings | Tools | HDL Support for tool paths, enable toggles, extra arguments, include directories and the VHDL standard
