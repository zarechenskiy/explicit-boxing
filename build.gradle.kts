import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Stay compatible with the Kotlin stdlib bundled into the target IDE
        apiVersion = KotlinVersion.KOTLIN_2_3
        languageVersion = KotlinVersion.KOTLIN_2_3
    }
}

// Standalone Kotlin compiler used by tests to produce real bytecode for analysis
val testKotlinCompiler: Configuration by configurations.creating

dependencies {
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")

    testImplementation("junit:junit:4.13.2")
    testKotlinCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.20")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.1.5")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks.test {
    val compilerClasspath: FileCollection = files(testKotlinCompiler)
    inputs.files(compilerClasspath).withPropertyName("testKotlinCompiler")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dtest.kotlin.compiler.classpath=${compilerClasspath.asPath}")
    })
}
