package com.y4kstudios.pytestimp

import ca.szc.configparser.Ini
import ca.szc.configparser.exceptions.NoOptionError
import ca.szc.configparser.exceptions.NoSectionError
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.commandInterface.commandLine.CommandLineLanguage
import com.jetbrains.commandInterface.commandLine.psi.CommandLineArgument
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Paths


@State(name = "PyTestImpService", storages = [Storage("other.xml")])
@Service(Service.Level.PROJECT)
class PyTestImpService(val project: Project) : PersistentStateComponent<PyTestImpService.State> {
    companion object {
        fun getInstance(project: Project): PyTestImpService =
            project.getService(PyTestImpService::class.java)

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

            val fileSystem = LocalFileSystem.getInstance()
            val path = Paths.get(value)
            val file =
                if (path.isAbsolute) {
                    fileSystem.findFileByNioFile(path)
                } else {
                    ProjectRootManager.getInstance(project)
                        .contentRoots
                        .map { root -> root.findFileByRelativePath(path.toString()) }
                        .firstOrNull()
                }

            refreshPytestConfig(file)
        }

    var pytestConfig: PyTestConfig? = PyTestConfig.parse(null, project)

    fun refreshPytestConfig(file: VirtualFile?) {
        val newPytestConfig = PyTestConfig.parse(file, project)

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


data class PytestLoadPlugin(
    val name: String,
    val excluded: Boolean = false,
)


/**
 * Interface for providing access to a pytest config file (pytest.ini file or pyproject.toml section)
 */
abstract class PyTestConfig(val project: Project) {
    protected open val pythonClassesRaw: String? = null
    protected open val pythonFunctionsRaw: String? = null
    protected open val pythonFilesRaw: String? = null

    open val pythonClasses: Regex by lazy { convertWildcardPatternsStringToRegex(pythonClassesRaw ?: DEFAULT_PYTHON_CLASSES, true) }
    open val pythonFunctions: Regex by lazy { convertWildcardPatternsStringToRegex(pythonFunctionsRaw ?: DEFAULT_PYTHON_FUNCTIONS, false) }
    open val pythonFiles: Regex by lazy { convertWildcardPatternsStringToRegex(pythonFilesRaw ?: DEFAULT_PYTHON_FILES, false) }

    /** Split/shlex'd extra py.test command-line options, from config's `addopts` */
    open val cmdlineOpts: List<String> = emptyList()

    /**
     * String representation of py.test command-line invocation
     * WARNING: NOT INTENDED FOR EXECUTION — see {@link #com.intellij.execution.configurations.GeneralCommandLine.getCommandLineString()}
     */
    open val cmdlineString: String by lazy { ParametersListUtil.join(cmdlineOpts) }

    /** Parsed CommandLine PsiFile */
    open val cmdlineFile: CommandLineFile by lazy {
        val psiFileFactory = project.service<PsiFileFactory>()
        psiFileFactory.createFileFromText(CommandLineLanguage.INSTANCE, "py.test $cmdlineString") as CommandLineFile
    }

    /** Pytest plugins explicitly loaded/excluded by `-p plugin`/`-p no:plugin` in `addopts` */
    open val loadedPlugins: List<PytestLoadPlugin> by lazy {
        cmdlineFile.options
            .filter { it.optionName == "-p" }
            .mapNotNull { PsiTreeUtil.getNextSiblingOfType(it, CommandLineArgument::class.java) }
            .map {
                val name = it.valueNoQuotes
                if (name.startsWith("no:")) {
                    PytestLoadPlugin(name.substring(3), true)
                } else {
                    PytestLoadPlugin(name)
                }
            }
    }

    companion object {
        const val CONFIG_PYTHON_CLASSES = "python_classes"
        const val CONFIG_PYTHON_FUNCTIONS = "python_functions"
        const val CONFIG_PYTHON_FILES = "python_files"
        const val CONFIG_ADDOPTS = "addopts"

        const val DEFAULT_PYTHON_CLASSES = "Test*"
        const val DEFAULT_PYTHON_FUNCTIONS = "test_*"
        const val DEFAULT_PYTHON_FILES = "test_*.py *_test.py"

        fun parse(file: VirtualFile?, project: Project): PyTestConfig? {
            if (file == null) return null

            return when (file.extension) {
                "ini" -> PyTestIni(file, project)
                "toml" -> PyTestPyProjectToml(file, project)
                else -> null
            }
        }
    }
}

internal fun Ini.getValueOrDefault(sectionName: String, optionName: String, default: String?) =
    try {
        getValue(sectionName, optionName)
    } catch (e: Exception) {
        when (e) {
            is NoSectionError, is NoOptionError -> default
            else -> throw e
        }
    }


/**
 * Parse a pytest.ini file and expose its contents
 */
class PyTestIni(pytestIniFile: VirtualFile, project: Project): PyTestConfig(project) {
    private val pytestIni: Ini by lazy { Ini().read(BufferedReader(InputStreamReader(pytestIniFile.inputStream))) }

    override val pythonClassesRaw: String? by lazy { pytestIni.getValueOrDefault(PYTEST_INI_SECTION, CONFIG_PYTHON_CLASSES, null) }
    override val pythonFunctionsRaw: String? by lazy { pytestIni.getValueOrDefault(PYTEST_INI_SECTION, CONFIG_PYTHON_FUNCTIONS, null) }
    override val pythonFilesRaw: String? by lazy { pytestIni.getValueOrDefault(PYTEST_INI_SECTION, CONFIG_PYTHON_FILES, null) }

    override val cmdlineString: String by lazy { pytestIni.getValueOrDefault(PYTEST_INI_SECTION, CONFIG_ADDOPTS, null) ?: "" }
    override val cmdlineOpts: List<String> by lazy { ParametersListUtil.parse(cmdlineString, false, true) }

    companion object {
        const val PYTEST_INI_SECTION = "pytest"
    }
}


/**
 * Parse a pyproject.toml file and expose its tool.pytest.ini_options section
 */
class PyTestPyProjectToml(pyprojectTomlFile: VirtualFile, project: Project): PyTestConfig(project) {
    private val pyprojectToml: TomlParseResult by lazy { Toml.parse(pyprojectTomlFile.inputStream) }

    override val pythonClasses: Regex by lazy { parseWildcardPatternsFromToml("$PYPROJECT_PYTEST_SECTION.$CONFIG_PYTHON_CLASSES", DEFAULT_PYTHON_CLASSES, true) }
    override val pythonFunctions: Regex by lazy { parseWildcardPatternsFromToml("$PYPROJECT_PYTEST_SECTION.$CONFIG_PYTHON_FUNCTIONS", DEFAULT_PYTHON_FUNCTIONS, false) }
    override val pythonFiles: Regex by lazy { parseWildcardPatternsFromToml("$PYPROJECT_PYTEST_SECTION.$CONFIG_PYTHON_FILES", DEFAULT_PYTHON_FILES, false) }

    private fun parseWildcardPatternsFromToml(dottedKey: String, defaultPatterns: String, withDashes: Boolean): Regex {
        return if (pyprojectToml.isArray(dottedKey)) {
            val patternsArray = pyprojectToml.getArray(dottedKey)!!
            if (patternsArray.containsStrings()) {
                convertWildcardPatternsToRegex(patternsArray.toList().filterIsInstance<String>(), withDashes)
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

    override val cmdlineOpts: List<String> by lazy {
        val dottedKey = "$PYPROJECT_PYTEST_SECTION.$CONFIG_ADDOPTS"
        if (pyprojectToml.isString(dottedKey)) {
            ParametersListUtil.parse(pyprojectToml.getString(dottedKey)!!, false, true)
        } else if (pyprojectToml.isArray(dottedKey)) {
            pyprojectToml.getArray(dottedKey)!!.toList().filterIsInstance<String>()
        } else {
            emptyList()
        }
    }

    override val cmdlineString: String by lazy {
        val dottedKey = "$PYPROJECT_PYTEST_SECTION.$CONFIG_ADDOPTS"
        if (pyprojectToml.isString(dottedKey)) {
            pyprojectToml.getString(dottedKey)!!
        } else {
            super.cmdlineString
        }
    }

    companion object {
        const val PYPROJECT_PYTEST_SECTION = "tool.pytest.ini_options"
    }
}
