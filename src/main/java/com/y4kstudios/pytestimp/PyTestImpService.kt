package com.y4kstudios.pytestimp

import ca.szc.configparser.Ini
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths


@State(name = "PyTestImpService", storages = [Storage("other.xml")])
class PyTestImpService(val project: Project) : PersistentStateComponent<PyTestImpService.State> {
    companion object {
        fun getInstance(project: Project): PyTestImpService =
            ServiceManager.getService(project, PyTestImpService::class.java)

        fun getAllInstances(): List<PyTestImpService> =
            ProjectManager.getInstance().openProjects.map { getInstance(it) }

    }

    class State {
        var pytestIniPath: String = "pytest.ini"
    }

    private var myState = State()

    override fun getState(): State = this.myState
    override fun loadState(state: State) {
        pytestConfigPath = state.pytestIniPath
    }

    var pytestConfigPath: String
        get() = myState.pytestIniPath
        set(value) {
            myState.pytestIniPath = value

            LocalFileSystem.getInstance().findFileByNioFile(Paths.get(value)).let {
                refreshPytestConfig(it)
            }
        }

    var pytestConfig: PyTestConfig? = PyTestConfig.parse(null)

    fun refreshPytestConfig(file: VirtualFile?) {
        val newPytestConfig = PyTestConfig.parse(file)

        if (newPytestConfig != pytestConfig) {
            pytestConfig = newPytestConfig

            // TODO: make more specific
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}


/**
 * Listen for filesystem changes, and refresh PyTestImpService.pytestIni on
 * changes to the configured pytest.ini
 */
class PyTestIniListener : BulkFileListener {
    override fun after(events: MutableList<out VFileEvent>) {
        val pytestConfigs = PyTestImpService.getAllInstances().groupBy({ it.pytestConfigPath }, { it })

        events
            .filter { event -> event.path in pytestConfigs }
            .forEach { event ->
                val file = event.file
                val services = pytestConfigs[event.path]

                services!!.forEach { service -> service.refreshPytestConfig(file) }
            }
    }
}


/**
 * Convert an fnmatch/wildcard (e.g. "test_*") pattern to a RegEx pattern string.
 *
 * NOTE: a handmade parser is used, in order to properly parse CamelCase word
 *       boundary dashes — a simple string replace would bungle legitimate dashes
 *       used within character classes ("[A-Z]")
 *
 * Heavy inspiration for this parser is derived from:
 *   https://stackoverflow.com/a/1248627/148585
 *
 * @param withDashes Whether to treat dashes as CamelCase word boundaries
 */
fun convertWildcardPatternToRegexPattern(wildcard: String, withDashes: Boolean): String {
    val chars = StringBuilder()
    var isEscaping = false
    var charClassLevel = 0

    var i = 0
    while (i < wildcard.length) {
        when (val char = wildcard[i]) {
            '*' -> chars.append(if (charClassLevel > 0) "*" else if (isEscaping) "\\*" else ".*")
            '?' -> chars.append(if (charClassLevel > 0) "?" else if (isEscaping) "\\?" else ".")
            '.', '(', ')', '^', '+', '|', '$' -> {
                if (charClassLevel == 0 || char == '^') {
                    chars.append("\\")
                }
                chars.append(char)
                isEscaping = false
            }
            '\\' -> {
                isEscaping =
                    if (isEscaping) {
                        chars.append("\\")
                        false
                    } else {
                        true
                    }
            }
            '[' -> {
                if (isEscaping) {
                    chars.append("\\[")
                } else {
                    chars.append('[')
                    charClassLevel++
                }
            }
            ']' -> {
                if (isEscaping) {
                    chars.append("\\]")
                } else {
                    chars.append(']')
                    charClassLevel--
                }
            }
            '!' -> {
                if (charClassLevel > 0 && wildcard[i - 1] == '[') {
                    chars.append('^')
                } else {
                    chars.append('!')
                }
            }
            '-' -> {
                if (withDashes && charClassLevel == 0) {
                    chars.append("[A-Z0-9]")
                } else {
                    chars.append('-')
                }
            }
            else -> {
                chars.append(char)
                isEscaping = false
            }
        }

        i++
    }

    /* Unclosed character classes would result in a PatternSyntaxException.
     * As a guard against this, we return a pattern matching nothing in cases
     * of unclosed character classes.
     */
    if (charClassLevel > 0) {
        // "Match nothing" pattern sourced from: https://stackoverflow.com/a/942122/148585
        return "(?!)"
    }

    return chars.toString()
}

/**
 * Convert a list of fnmatch/wildcard patterns to a single Regex
 *
 * @param withDashes Whether to treat dashes as CamelCase word boundaries
 */
fun convertWildcardPatternsToRegex(patterns: List<String>, withDashes: Boolean): Regex =
    patterns
        .joinToString("|") {
            convertWildcardPatternToRegexPattern(it, withDashes)
        }
        .let { Regex(it) }

/**
 * Convert a space-delimited list of fnmatch/wildcard patterns to a single Regex
 *
 * @param withDashes Whether to treat dashes as CamelCase word boundaries
 */
fun convertWildcardPatternsStringToRegex(patterns: String, withDashes: Boolean): Regex =
    convertWildcardPatternsToRegex(patterns.split(Regex(" +")), withDashes)


/**
 * Interface for providing access to a pytest config file (pytest.ini file or pyproject.toml section)
 */
abstract class PyTestConfig {
    protected open val pythonClassesRaw: String? = null
    protected open val pythonFunctionsRaw: String? = null

    open val pythonClasses: Regex by lazy { convertWildcardPatternsStringToRegex(pythonClassesRaw ?: DEFAULT_PYTHON_CLASSES, true) }
    open val pythonFunctions: Regex by lazy { convertWildcardPatternsStringToRegex(pythonFunctionsRaw ?: DEFAULT_PYTHON_FUNCTIONS, false) }

    companion object {
        const val CONFIG_PYTHON_CLASSES = "python_classes"
        const val CONFIG_PYTHON_FUNCTIONS = "python_functions"

        const val DEFAULT_PYTHON_CLASSES = "Test*"
        const val DEFAULT_PYTHON_FUNCTIONS = "test_*"

        fun parse(file: VirtualFile?): PyTestConfig? {
            if (file == null) return null

            return when (file.extension) {
                "ini" -> PyTestIni(file)
                "toml" -> PyTestPyProjectToml(file)
                else -> null
            }
        }
    }
}


/**
 * Parse a pytest.ini file and expose its contents
 */
class PyTestIni(pytestIniFile: VirtualFile): PyTestConfig() {
    private val pytestIni: Ini by lazy { Ini().read(BufferedReader(InputStreamReader(pytestIniFile.inputStream))) }

    override val pythonClassesRaw: String? by lazy { pytestIni.getValue(PYTEST_INI_SECTION, CONFIG_PYTHON_CLASSES) }
    override val pythonFunctionsRaw: String? by lazy { pytestIni.getValue(PYTEST_INI_SECTION, CONFIG_PYTHON_FUNCTIONS) }

    companion object {
        const val PYTEST_INI_SECTION = "pytest"
    }
}


/**
 * Parse a pyproject.toml file and expose its tool.pytest.ini_options section
 */
class PyTestPyProjectToml(pyprojectTomlFile: VirtualFile): PyTestConfig() {
    private val pyprojectToml: TomlParseResult by lazy { Toml.parse(pyprojectTomlFile.inputStream) }

    override val pythonClasses: Regex by lazy { parseWildcardPatternsFromToml("$PYPROJECT_PYTEST_SECTION.$CONFIG_PYTHON_CLASSES", DEFAULT_PYTHON_CLASSES, true) }
    override val pythonFunctions: Regex by lazy { parseWildcardPatternsFromToml("$PYPROJECT_PYTEST_SECTION.$CONFIG_PYTHON_FUNCTIONS", DEFAULT_PYTHON_FUNCTIONS, false) }

    private fun parseWildcardPatternsFromToml(dottedKey: String, defaultPatterns: String, withDashes: Boolean): Regex {
        return if (pyprojectToml.isArray(dottedKey)) {
            val patternsArray = pyprojectToml.getArray(dottedKey)!!
            if (patternsArray.containsStrings()) {
                convertWildcardPatternsToRegex(patternsArray.toList() as List<String>, withDashes)
            } else {
                // In the case of a non-string array (or heterogeneous array), pytest will fail.
                // We instead simply treat the pattern as unmatchable.
                // TODO: warn the user about this
                Regex(".^")
            }
        } else if (pyprojectToml.isString(dottedKey)) {
            convertWildcardPatternsStringToRegex(pyprojectToml.getString(dottedKey)!!, withDashes)
        } else {
            convertWildcardPatternsStringToRegex(defaultPatterns, withDashes)
        }
    }

    companion object {
        const val PYPROJECT_PYTEST_SECTION = "tool.pytest.ini_options"
    }
}
