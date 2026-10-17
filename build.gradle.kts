import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit")
    id("org.jetbrains.changelog")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(21)
}

// Declared here rather than in settings.gradle.kts: the Grammar-Kit plugin adds project-level
// repositories, and Gradle then ignores any declared in settings.
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// ---- Lexer generation (JFlex via Grammar-Kit) ----
// Lexers are generated from src/main/grammars/*.flex into the build directory and are never edited by hand.
val generatedLexerDir = layout.buildDirectory.dir("generated/lexer")

val generateVerilogLexer by tasks.registering(GenerateLexerTask::class) {
    sourceFile = layout.projectDirectory.file("src/main/grammars/Verilog.flex")
    targetOutputDir = generatedLexerDir.map { it.dir("verilog/dev/sal/hdl/verilog") }
    purgeOldFiles = true
}

val generateVhdlLexer by tasks.registering(GenerateLexerTask::class) {
    sourceFile = layout.projectDirectory.file("src/main/grammars/Vhdl.flex")
    targetOutputDir = generatedLexerDir.map { it.dir("vhdl/dev/sal/hdl/vhdl") }
    purgeOldFiles = true
}

val generateLexers by tasks.registering {
    group = "grammarkit"
    description = "Generates all JFlex lexers."
    dependsOn(generateVerilogLexer, generateVhdlLexer)
}

sourceSets {
    main {
        java {
            srcDir(generatedLexerDir.map { it.dir("verilog") })
            srcDir(generatedLexerDir.map { it.dir("vhdl") })
        }
    }
}

tasks.named("compileKotlin") { dependsOn(generateLexers) }
tasks.named("compileJava") { dependsOn(generateLexers) }

// ---- Keyword lists for completion ----
// Completion reads its keywords from resources extracted from the .flex files, so each language keeps
// one keyword list. A section is the quoted words between "// ---- <Section> ----" and
// "// ---- End of <section> ----" (matched ignoring case).
abstract class ExtractWordListTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val section: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        val lines = sourceFile.get().asFile.readLines().map { it.trim() }
        val start = lines.indexOfFirst { it.equals("// ---- ${section.get()} ----", ignoreCase = true) }
        val end = lines.indexOfFirst { it.equals("// ---- End of ${section.get()} ----", ignoreCase = true) }
        if (start < 0 || end < start) throw GradleException("Section '${section.get()}' not found in ${sourceFile.get()}")
        val words = lines.subList(start + 1, end)
            .filterNot { it.startsWith("//") }
            .flatMap { line -> Regex("\"([^\"]+)\"").findAll(line).map { it.groupValues[1] }.toList() }
        if (words.isEmpty()) throw GradleException("Section '${section.get()}' in ${sourceFile.get()} is empty")
        outputFile.get().asFile.writeText(words.distinct().sorted().joinToString("\n", postfix = "\n"))
    }
}

val generatedKeywordDir = layout.buildDirectory.dir("generated/keywords")

fun registerWordList(name: String, flex: String, section: String, output: String) =
    tasks.register<ExtractWordListTask>(name) {
        sourceFile = layout.projectDirectory.file("src/main/grammars/$flex")
        this.section = section
        outputFile = generatedKeywordDir.map { it.file("dev/sal/hdl/completion/$output") }
    }

val wordLists = listOf(
    registerWordList("extractVerilogKeywords", "Verilog.flex", "Keywords", "verilog-keywords.txt"),
    registerWordList("extractVhdlKeywords", "Vhdl.flex", "Keywords", "vhdl-keywords.txt"),
    registerWordList("extractVhdlStdTypes", "Vhdl.flex", "Standard types", "vhdl-std-types.txt"),
)

val generateKeywordLists by tasks.registering {
    group = "grammarkit"
    description = "Extracts the keyword lists from the .flex files for completion."
    dependsOn(wordLists)
}

sourceSets {
    main {
        resources.srcDir(generatedKeywordDir)
    }
}

tasks.named("processResources") { dependsOn(generateKeywordLists) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    // IntelliJ Platform Gradle Plugin Dependencies Extension
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        // The Marketplace description is the part of README.md between the "Plugin description" markers.
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        // The change notes are the CHANGELOG.md section for this version, or "Unreleased" if there isn't one.
        val changelog = project.changelog
        changeNotes = providers.gradleProperty("version").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    // Release signing and publishing read their secrets from environment variables (GitHub Actions
    // secrets in CI). See "Releasing" in CLAUDE.md.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Open the sample HDL files in the sandbox IDE, so there's something to look at straight away.
// It opens a fresh copy under build/, so edits made while trying things out can't change the test data.
val prepareSampleProject by tasks.registering(Sync::class) {
    from("src/test/testData") {
        exclude("**/*.txt")
    }
    into(layout.buildDirectory.dir("sample-project"))
}

tasks.runIde {
    dependsOn(prepareSampleProject)
    args(layout.buildDirectory.dir("sample-project").get().asFile.absolutePath)
}
