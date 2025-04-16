import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory

group = "com.y4kstudios"
version = "1.3.1"

buildscript {
    val kotlinVersion = "2.1.0"
    val ideVersion = "251-EAP-SNAPSHOT"

    project.extra.set("kotlinVersion", kotlinVersion)
    project.extra.set("ideVersion", ideVersion)

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

repositories {
    mavenCentral()

    maven {
        name = "rd-snapshots"
        url = uri("https://www.myget.org/F/rd-snapshots/maven/")
    }

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.tomlj:tomlj:1.0.0")
    implementation("ca.szc.configparser:java-configparser:0.2")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.extra.get("kotlinVersion")}")

    testImplementation("junit", "junit", "4.13.1")
    testImplementation("org.opentest4j", "opentest4j", "1.3.0")

    intellijPlatform {
        pycharmProfessional(project.ext.get("ideVersion").toString(), useInstaller = false)

        testFramework(TestFrameworkType.Platform)

        bundledPlugins("PythonCore")
        pluginsInLatestCompatibleVersion(
            // https://plugins.jetbrains.com/plugin/227-psiviewer/versions/stable
            "PsiViewer",

            // https://plugins.jetbrains.com/plugin/8195-toml/versions/stable
            "org.toml.lang",
        )

        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "pytest imp"
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    runIde {
        jvmArgs = listOf("-Didea.ProcessCanceledException=disabled")
    }

    test {
        /**
         * Testing with PY since 2022.1 requires to also add com.intellij.platform.images (automatically provided in PC)
         *
         * Ref: https://youtrack.jetbrains.com/issue/IJSDK-1427/Running-tests-for-PyCharm-Professional-is-not-working
         * Source: https://github.com/bigbear3001/pycharm-plugin-test-test/commit/38df5b1b999ccde10d6eb262600744e3214680cc
         */
        dependencies {
            intellijPlatform {
                bundledPlugins(
                    "com.intellij.platform.images",
                )
            }
        }
    }

    jar {
        exclude("com/jetbrains/**")
    }

    patchPluginXml {
        changeNotes = extractChangeNotes()
        sinceBuild = "251"
        untilBuild = "251.*"
    }

    buildPlugin {
        archiveBaseName = "pytest imp"
    }

    publishPlugin {
        channels = listOf("stable")
        token = providers.gradleProperty("publishToken")
    }
}


// Very primitive changelog extraction code
// Source: https://github.com/JetBrains/ideavim/blob/d5055506b019b7cdb4ba786a086dfc9385a42705/build.gradle#L107-L129
fun extractChangeNotes(): String {
    val header = "<ul>\n"
    val footer = "</ul>\n<p>See also the complete <a href=\"https://github.com/theY4Kman/pycharm-pytest-imp/blob/master/CHANGELOG.md\">changelog</a>.</p>"

    val startLine = "$version, "
    val endLine = "----"
    val skipAfterStart = 1

    var skipLines = 0
    var changeType = ""
    var startSaving = false
    val res = StringBuilder(header)
    File("./CHANGELOG.md").forEachLine { line ->
        if (skipLines > 0) {
            skipLines--
            return@forEachLine
        }

        if (startSaving) {
            if (line.startsWith(endLine)) {
                startSaving = false
            }
            else if (line.startsWith("**") && line.endsWith(":**")) {
                changeType = line.substring(2, line.length - 3)
                changeType = changeType.replaceFirst("s$", "")
            }
            else if (line.startsWith(" - ")) {
                val item = line.substring(3)
                res.append("  <li><b>$changeType:</b> $item</li>").append('\n')
            }
        }
        else {
            if (line.startsWith(startLine)) {
                startSaving = true
                skipLines = skipAfterStart
            }
        }
    }

    res.append(footer)
    return res.toString()
}

// Source: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-recipes.html#resolve-plugin-from-jetbrains-marketplace-in-the-latest-compatible-version
val IntelliJPlatformDependenciesExtension.pluginRepository by lazy {
    PluginRepositoryFactory.create("https://plugins.jetbrains.com")
}

fun IntelliJPlatformDependenciesExtension.pluginsInLatestCompatibleVersion(vararg pluginIds: String) =
    plugins(provider {
        pluginIds.map { pluginId ->
            val platformType = intellijPlatform.productInfo.productCode
            val platformVersion = intellijPlatform.productInfo.buildNumber

            val plugin = pluginRepository.pluginManager.searchCompatibleUpdates(
                build = "$platformType-$platformVersion",
                xmlIds = listOf(pluginId),
            ).firstOrNull()
                ?: throw GradleException("No plugin update with id='$pluginId' compatible with '$platformType-$platformVersion' found in JetBrains Marketplace")

            "${plugin.pluginXmlId}:${plugin.version}"
        }
    })
