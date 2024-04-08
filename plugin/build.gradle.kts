import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.16.1"
    // Gradle Changelog Plugin
/* ROLL_BACK_CHANGELOG_PLUGIN BEGIN - https://github.com/JetBrains/gradle-changelog-plugin/issues/147
    id("org.jetbrains.changelog") version "2.0.0"
*/
    id("org.jetbrains.changelog") version "2.2.0"
/* ROLL_BACK_CHANGELOG_PLUGIN END */
    // Gradle Qodana Plugin
    id("org.jetbrains.qodana") version "0.1.13"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.10")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-gson:2.3.7")
    implementation("io.ktor:ktor-serialization-gson:2.3.7")
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
    jvmToolchain(17)
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.set(emptyList())
/* ROLL_BACK_CHANGELOG_PLUGIN BEGIN - https://github.com/JetBrains/gradle-changelog-plugin/issues/147
    repositoryUrl.set(properties("pluginRepositoryUrl"))
*/
/* ROLL_BACK_CHANGELOG_PLUGIN END */
    path.set(File(projectDir, "../CHANGELOG.md").absolutePath)
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    cachePath.set(file(".qodana").canonicalPath)
    reportPath.set(file("build/reports/inspections").canonicalPath)
    saveReport.set(true)
    showReport.set(System.getenv("QODANA_SHOW_REPORT")?.toBoolean() ?: false)
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    patchPluginXml {
        version.set(properties("pluginVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            file("../README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").let { markdownToHTML(it) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
/* ROLL_BACK_CHANGELOG_PLUGIN BEGIN - https://github.com/JetBrains/gradle-changelog-plugin/issues/147
            renderItem(
                getOrNull(properties("pluginVersion"))
                    ?: runCatching { getLatest() }.getOrElse { getUnreleased() },
                Changelog.OutputType.HTML,
            )
*/
            changelog.renderItem(changelog.run {
                getOrNull(properties("pluginVersion")) ?: getLatest()
            }, Changelog.OutputType.HTML)
/* ROLL_BACK_CHANGELOG_PLUGIN END */
        })
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    // Exclude Kotlin packages from the plugin in order to use the ones packed in IntelliJ platform 2022.1.
    // Note: to see list of packages included, go to folder: build/idea-sandbox/plugins/Rossynt/lib
    //
    // References:
    // https://youtrack.jetbrains.com/issue/IDEA-285839
    // https://youtrack.jetbrains.com/issue/KTIJ-20529
    //
    buildPlugin {
        exclude {
            it.name.startsWith("kotlinx-coroutines-") || it.name.startsWith("kotlin-stdlib-") || it.name.startsWith("kotlin-reflect-")
        }
    }
    prepareSandbox {
        exclude {
            it.name.startsWith("kotlinx-coroutines-") || it.name.startsWith("kotlin-stdlib-") || it.name.startsWith("kotlin-reflect-")
        }
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }
}
