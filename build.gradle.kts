import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit")
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
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Open the sample HDL files in the sandbox IDE, so there's something to look at straight away.
tasks.runIde {
    args(layout.projectDirectory.dir("src/test/testData").asFile.absolutePath)
}
