package com.y4kstudios.pytestimp

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
import org.ini4j.Ini
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

    override fun getState(): State? = this.myState
    override fun loadState(state: State) {
        pytestIniPath = state.pytestIniPath
    }

    var pytestIniPath: String
        get() = myState.pytestIniPath
        set(value) {
            myState.pytestIniPath = value

            LocalFileSystem.getInstance().findFileByNioFile(Paths.get(value)).let {
                refreshPytestIni(it)
            }
        }

    var pytestIni: PyTestIni? = PyTestIni(null)

    fun refreshPytestIni(file: VirtualFile?) {
        val newPytestIni = PyTestIni(file)

        if (newPytestIni != pytestIni) {
            pytestIni = newPytestIni

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
        val pytestInis = PyTestImpService.getAllInstances().groupBy({ it.pytestIniPath }, { it })

        events
            .filter { event -> event.path in pytestInis }
            .forEach { event ->
                val file = event.file
                val services = pytestInis[event.path]

                services!!.forEach { service -> service.refreshPytestIni(file) }
            }
    }
}


const val PYTEST_INI_SECTION = "pytest"


/**
 * Convert an fnmatch/wildcard (e.g. "test_*") pattern to a RegEx pattern string.
 *
 * @param withDashes Whether to treat dashes as CamelCase word boundaries
 */
fun convertWildcardPatternToRegexPattern(wildcard: String, withDashes: Boolean): String =
    wildcard.replace("*", ".*").let {
        if (withDashes) it.replace("-", "[A-Z0-9]")
        else it
    }

/**
 * Convert a space-delimited list of fnmatch/wildcard patterns to a single Regex
 *
 * @param withDashes Whether to treat dashes as CamelCase word boundaries
 */
fun convertWildcardPatternsToRegex(patterns: String, withDashes: Boolean): Regex =
    patterns
        .split(Regex(" +"))
        .joinToString("|") {
            convertWildcardPatternToRegexPattern(it, withDashes)
        }
        .let { Regex(it) }


/**
 * Parse a pytest.ini file and expose its contents
 */
class PyTestIni(pytestIniFile: VirtualFile?) {
    val pytestIni: Ini? by lazy {
        if (pytestIniFile != null)
            Ini(pytestIniFile.inputStream)
        else
            null
    }

    val pythonClassesRaw: String by lazy { pytestIni?.get(PYTEST_INI_SECTION, "python_classes") ?: "Test*" }
    val pythonFunctionsRaw: String by lazy { pytestIni?.get(PYTEST_INI_SECTION, "python_functions") ?: "test_*" }

    val pythonClasses: Regex by lazy { convertWildcardPatternsToRegex(pythonClassesRaw, true) }
    val pythonFunctions: Regex by lazy { convertWildcardPatternsToRegex(pythonFunctionsRaw, false) }
}
