# HDL Support for IntelliJ — Verilog / SystemVerilog / VHDL

## What this project is

A free IntelliJ Platform plugin that adds:

1. **Syntax highlighting** for Verilog, SystemVerilog and VHDL (the main goal).
2. **Lint squiggles** from Verilator (Verilog/SV) and GHDL (VHDL), run on save.
3. An outline parser with PSI, giving a structure view, folding, `begin`/`end` matching and go-to-definition for modules/entities.
4. A Yosys "Show Synthesis Stats" action.

This is a personal tool and a learning project. Prefer clear, well-commented code over clever code.

## Tech stack

- **Language:** Kotlin. Generated lexer code is Java.
- **Build:** Gradle (Kotlin DSL), started from JetBrains' `intellij-platform-plugin-template`.
- **Plugin tooling:** IntelliJ Platform Gradle Plugin 2.x and the Grammar-Kit Gradle plugin (`org.jetbrains.grammarkit`) for JFlex lexer generation.
- **Target IDE:** IntelliJ IDEA (free tier). Keep the platform version in `gradle.properties`. Don't hardcode it elsewhere. Currently built against 2025.3 (the first unified IDEA release), `sinceBuild` 253, no upper bound.
- **JDK:** Gradle and the build need JDK 21 (`kotlin { jvmToolchain(21) }`). Run Gradle with `JAVA_HOME` set to a JDK 21, e.g. `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS.
- **External tools (runtime, optional):** `verilator`, `ghdl`, `yosys`. The plugin must still work, with colouring only, when these aren't installed.

## Repository layout

```
.
├── CLAUDE.md
├── README.md
├── CHANGELOG.md
├── LICENSE
├── .github/workflows/      (build.yml: CI; release.yml: publish on v* tags)
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── src/
    ├── main/
    │   ├── grammars/
    │   │   ├── Verilog.flex
    │   │   └── Vhdl.flex
    │   ├── kotlin/dev/sal/hdl/
    │   │   ├── HdlIcons.kt
    │   │   ├── psi/
    │   │   │   ├── HdlParsing.kt          (shared parser helpers: lookahead, bracket skipping)
    │   │   │   └── HdlPsiUtil.kt          (HdlKeywordBlock interface, PSI helpers)
    │   │   ├── verilog/
    │   │   │   ├── VerilogLanguage.kt
    │   │   │   ├── VerilogFileType.kt
    │   │   │   ├── VerilogFile.kt
    │   │   │   ├── VerilogParserDefinition.kt
    │   │   │   ├── VerilogTokenTypes.kt
    │   │   │   ├── VerilogLexerAdapter.kt
    │   │   │   ├── VerilogSyntaxHighlighter.kt
    │   │   │   ├── VerilogSyntaxHighlighterFactory.kt
    │   │   │   ├── VerilogColorSettingsPage.kt
    │   │   │   ├── VerilogCommenter.kt
    │   │   │   ├── VerilogStructureViewFactory.kt
    │   │   │   ├── VerilogFoldingBuilder.kt
    │   │   │   ├── VerilogBraceMatcher.kt
    │   │   │   └── psi/
    │   │   │       ├── VerilogElementTypes.kt
    │   │   │       ├── VerilogParser.kt       (outline parser, see "Parsers" below)
    │   │   │       └── VerilogPsi.kt          (design units, ports, declarations, instances, ...)
    │   │   ├── vhdl/            (mirror of verilog/)
    │   │   ├── structure/
    │   │   │   └── HdlStructureView.kt    (shared structure view; each language supplies an HdlStructureSpec)
    │   │   ├── folding/
    │   │   │   └── HdlFoldingBuilder.kt   (shared folding over HdlKeywordBlock elements)
    │   │   ├── editor/
    │   │   │   └── HdlCodeBlockSupportHandler.kt  (begin/end keyword matching for both languages)
    │   │   ├── navigation/
    │   │   │   ├── HdlUnitIndex.kt        (file-based index: design unit name → files)
    │   │   │   └── HdlUnits.kt            (unit lookup and HdlUnitReference for go-to-definition)
    │   │   ├── yosys/
    │   │   │   ├── ShowSynthesisStatsAction.kt
    │   │   │   ├── YosysScript.kt
    │   │   │   ├── YosysRunner.kt         (runs Yosys into a tab of the Yosys tool window)
    │   │   │   └── YosysToolWindowFactory.kt
    │   │   ├── lint/
    │   │   │   ├── LintMessage.kt
    │   │   │   ├── HdlLintAnnotator.kt    (shared ExternalAnnotator: temp copy, process, timeout, apply)
    │   │   │   ├── VerilatorAnnotator.kt
    │   │   │   ├── VerilatorOutputParser.kt
    │   │   │   ├── GhdlAnnotator.kt
    │   │   │   ├── GhdlOutputParser.kt
    │   │   │   ├── LintRanges.kt          (column → underline range)
    │   │   │   └── ToolLocator.kt
    │   │   └── settings/
    │   │       ├── HdlSettings.kt         (persistent state: tool paths, flags, enable toggles)
    │   │       └── HdlSettingsConfigurable.kt
    │   └── resources/
    │       ├── META-INF/plugin.xml
    │       └── icons/
    └── test/
        ├── kotlin/dev/sal/hdl/
        └── testData/
            ├── verilog/
            └── vhdl/
```

## Milestones

Work through these in order. Each milestone should build, pass its tests, and be manually checked with `./gradlew runIde` before moving on.

### M1 — Skeleton
- Generate the project from the plugin template and rename the package to `dev.sal.hdl`.
- Strip out the template's sample code.
- Register the Verilog file type (`v`, `vh`, `sv`, `svh`) and the VHDL file type (`vhd`, `vhdl`) in `plugin.xml`.
- **Done when:** `runIde` opens, and `.v`/`.vhd` files show the plugin's file type in the status bar.

### M2 — Verilog/SystemVerilog lexer and highlighting
- Write `Verilog.flex` with tokens for:
  - keywords (IEEE 1364 + 1800 keyword lists; keep them in one clearly marked block so they're easy to extend)
  - line `//` and block `/* */` comments
  - strings
  - sized/based numbers (`8'hFF`, `4'b10x1`, `'0`, `32'd1_000`), plus plain integers and reals
  - system tasks/functions (`$display`, `$finish`, `$clog2`)
  - compiler directives (`` `define ``, `` `include ``, `` `timescale ``, `` `ifdef ``)
  - identifiers, escaped identifiers (`\foo+bar `)
  - operators and punctuation
  - whitespace and a `BAD_CHARACTER` fallback
- The lexer **must never throw** and must handle unterminated comments and strings gracefully.
- Map tokens to `DefaultLanguageHighlighterColors` so the plugin follows the user's theme.
- Add a `ColorSettingsPage` with a realistic demo snippet.
- Add a `Commenter` (`//` line, `/* */` block) so Ctrl+/ works.
- **Done when:** a real module (counter, FSM, testbench) is fully and correctly coloured.

### M3 — VHDL lexer and highlighting
- Same structure as M2. Key VHDL differences:
  - **Case-insensitive** keywords (`%ignorecase` in JFlex).
  - Comments are `--` line comments plus VHDL-2008 `/* */` block comments.
  - Literals: character `'0'`, bit strings `x"FF"`, `b"1010"`, based literals `16#FF#`.
  - Attributes after a tick (`clk'event`, `sig'length`). The lexer must not confuse these with character literals: after an identifier or `)`, a tick starts an attribute.
  - Highlight common `ieee` types (`std_logic`, `std_logic_vector`, `unsigned`, `signed`) as a distinct token.
- Commenter: `--`.
- **Done when:** an entity/architecture with a clocked process is correctly coloured, including attributes.

### M4 — Verilator lint (ExternalAnnotator)
- `VerilatorAnnotator`:
  - `collectInformation`: file path, plus the text if the file is unsaved (write it to a temp file).
  - `doAnnotate`: run `verilator --lint-only -Wall <file>` with a timeout (default 10 s). Never run on the EDT.
  - Parse lines matching `%(Warning|Error)(-CODE)?: <path>:<line>:<col>: <message>`, and treat the column as optional.
  - `apply`: Error → `HighlightSeverity.ERROR`, Warning → `WARNING`. Highlight from the column to the end of the token or line. Put the Verilator code (e.g. `UNUSEDSIGNAL`) in the tooltip.
- `ToolLocator`: use the path from settings, otherwise look on `PATH`. If the tool isn't found, show one balloon notification per session and stop trying. Never spam the user.
- Include directories (`-I`) and extra flags come from settings.
- **Done when:** an unused signal shows a warning squiggle and a syntax error shows a red one, on the right lines.

### M5 — GHDL lint
- Same pattern as M4, using `ghdl -s --std=08 <file>`.
- GHDL output format: `<file>:<line>:<col>: <message>`. Lines containing `warning` are warnings; everything else is an error.
- **Done when:** a VHDL syntax error is underlined on the correct line.

### M6 — Settings page
- Add a settings page under **Settings → Tools → HDL Support** with:
  - path fields for Verilator and GHDL
  - enable/disable toggles for each linter
  - an extra-arguments field for each linter
  - a GHDL `--std` dropdown (93 / 08)
- Persist these with `PersistentStateComponent`.

### Stretch goals (done)
- Brace matching for `()`/`[]`/`{}`, and keyword matching for `begin`/`end`, `module`/`endmodule`, `if`/`elsif`/`else`/`end if` and so on (`HdlCodeBlockSupportHandler`).
- An outline parser with PSI, enabling a structure view (modules, ports, signals, always blocks, instances) and go-to-definition for module instantiations, architectures, imports and use clauses.
- Code folding for modules, entities, `always`, `process`, `generate`, `begin`/`end` and other keyword blocks, port lists and block comments.
- A Yosys action, **Tools → Show Synthesis Stats**: `yosys -p "read_verilog -sv <f>; hierarchy -check -top <m> -libdir .; synth -top <m>; stat"`, with the output in a Yosys tool window.

### Parsers

The parsers are hand-written `PsiBuilder` parsers (`VerilogParser.kt`, `VhdlParser.kt`), not Grammar-Kit `.bnf` grammars. The lexers return every keyword as one `KEYWORD` token, so a BNF grammar would need a token type per keyword, and these parsers have to cope with half-typed code. They are *outline* parsers:

- They build nodes only for constructs the IDE features use (design units, ports, declarations, instances, processes, subprograms, keyword blocks) and skip other statements up to their `;`.
- They **never report errors**. A `PsiErrorElement` would show as a red mark; real errors come from Verilator and GHDL.
- Recovery: each construct records the end keywords it expects. An end keyword that belongs to an enclosing construct closes the inner ones early, so a missing `end` doesn't swallow the rest of the file.
- Names aren't wrapped in nodes. The PSI classes work them out from their tokens (`VerilogDeclaration.names`, `VhdlInterfaceDeclaration.names`).

## Commands

| Task | Command |
|---|---|
| Run sandbox IDE with plugin | `./gradlew runIde` (opens a throwaway copy of the test data from `build/sample-project`) |
| Run tests | `./gradlew test` |
| Regenerate lexers | `./gradlew generateLexers` (runs `generateVerilogLexer` and `generateVhdlLexer`; output goes to `build/generated/lexer`. Compilation runs this automatically.) |
| Build installable zip | `./gradlew buildPlugin` → `build/distributions/*.zip` |
| Verify plugin compatibility | `./gradlew verifyPlugin` |

Install locally via **Settings → Plugins → ⚙ → Install Plugin from Disk**.

Build notes:
- Repositories are declared in `build.gradle.kts`, not `settings.gradle.kts`, because the Grammar-Kit plugin adds project-level repositories, and Gradle then ignores any declared in settings.
- JFlex's `%ignorecase` applies to quoted strings but **not** to character classes. In `Vhdl.flex`, write classes with both cases, e.g. `[a-zA-Z]`.

## Releasing

The project is MIT-licensed open source (`LICENSE`).

- **Plugin ID:** `io.github.jeddfadhley.hdl` in `plugin.xml`. It's permanent once uploaded to the Marketplace, so never change it. Kotlin packages (`dev.sal.hdl`) are separate and can stay as they are.
- **Marketplace description** comes from the part of `README.md` between `<!-- Plugin description -->` and `<!-- Plugin description end -->`. Edit it there, not in `plugin.xml`.
- **Change notes** come from `CHANGELOG.md`: the section matching `version` in `gradle.properties`, or `[Unreleased]`.
- **Icon:** `src/main/resources/META-INF/pluginIcon.svg` (40×40) and `pluginIcon_dark.svg`.
- **CI:** `.github/workflows/build.yml` runs tests, `buildPlugin` and `verifyPlugin` on every push and PR.

To cut a release:
1. Add the changes under `## [Unreleased]` in `CHANGELOG.md`, bump `version` in `gradle.properties`, then run `./gradlew patchChangelog` (it moves Unreleased to the new version).
2. Commit, then tag `v<version>` and push the tag. `.github/workflows/release.yml` checks the tag matches the version, signs the plugin, runs `publishPlugin` and creates a GitHub release with the signed zip.
3. Repository secrets needed: `PUBLISH_TOKEN` (Marketplace → profile → My Tokens), and `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` (see https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

The very first Marketplace upload must be done by hand on plugins.jetbrains.com. Only later versions can go through `publishPlugin`.

## Testing

- **Lexer tests:** extend `HdlLexerTestCase` (a `LexerTestCase`). Put sample HDL files in `src/test/testData/` and compare against expected token dumps. A missing `.txt` dump is created on the first run, and that run fails. Review the new dump by hand before committing it. Every new token rule needs a test.
- **Must-pass edge cases:**
  - unterminated `/*` comments and strings at EOF
  - an empty file
  - a file containing only whitespace
  - very long lines
  - non-ASCII characters inside comments
  - VHDL `clk'event` vs `'1'`
  - VHDL keywords in mixed case (`ENTITY`, `Entity`)
  - Verilog escaped identifiers
  - sized numbers containing `x`/`z`/`_`
- **Parser tests:** extend `HdlParsingTestCase`. File tests compare against `<name>.psi.txt` dumps next to the sample file; review a new dump by hand, as with lexer dumps. `assertTree` checks short snippets inline. Every recovery rule needs a test, and the parser must never produce a `PsiErrorElement` or lose text (both checked automatically).
- **Feature tests:** `HdlStructureViewTest`, `HdlFoldingTest`, `HdlBraceMatchingTest` and `HdlNavigationTest` are fixture tests with the expected result written inline.
- **Annotator tests:** unit-test the output parsers against captured Verilator/GHDL output strings. Don't require the tools to be installed for `./gradlew test` to pass. `LintAnnotatorIntegrationTest` runs the real tools end to end and quietly passes when a tool is missing. `YosysTest` does the same for Yosys.

## Conventions

- Kotlin, 4-space indent, no wildcard imports.
- Keep generated lexer sources out of hand-edited directories. Generate them into `src/main/gen` (or the build dir) and don't edit them by hand.
- Keep keyword lists in one place per language with a comment citing the standard (IEEE 1364-2005, 1800-2017, 1076-2008).
- Every extension point registered in `plugin.xml` gets a one-line comment saying what it does.
- Never block the UI thread. All process execution happens in `doAnnotate` or in background tasks.
- Small, focused commits, one per milestone step.

## Out of scope

- A full language server, simulation, or waveform viewing.
- Paid or closed-source dependencies.
- Supporting non-IntelliJ editors.

## Notes for Claude

- Before starting a milestone, read this file and say which milestone you're on.
- If the IntelliJ Platform API has changed (deprecated classes, renamed Gradle tasks), check the current IntelliJ Platform SDK docs instead of guessing, and update this file if a command above is wrong.
- After each milestone, summarise what was built and anything you'd like manually checked in `runIde`.
