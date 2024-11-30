import org.jetbrains.intellij.platform.gradle.TestFrameworkType

group = "com.y4kstudios"
version = "1.2.1"

buildscript {
    val kotlinVersion = "1.9.0"
    val ideVersion = "2024.2.2"

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
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij.platform") version "2.0.0-beta3"
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

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${project.ext.get("kotlinVersion")}")

    testImplementation("junit", "junit", "4.13.1")
    testImplementation("org.opentest4j", "opentest4j", "1.3.0")

    intellijPlatform {
        pycharmProfessional(project.ext.get("ideVersion").toString())

        instrumentationTools()
        testFramework(TestFrameworkType.Platform.JUnit4)

        bundledPlugins("Pythonid")
        plugins(
            // https://plugins.jetbrains.com/plugin/227-psiviewer/versions/stable
            "PsiViewer:242.4697",

            // XXX(zk): this version number must be manually looked up waaay too often;
            //          is there a way to pull this version number automatically?
            // https://plugins.jetbrains.com/plugin/8195-toml/versions/stable
            "org.toml.lang:242.10180.28",
        )
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "pytest imp"
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
                bundledPlugin("com.intellij.platform.images")
            }
        }
    }

    jar {
        exclude("com/jetbrains/**")
    }

    patchPluginXml {
        changeNotes = extractChangeNotes()
        sinceBuild = "241"
        untilBuild = "242.*"
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
