package com.y4kstudios.pytestimp

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import org.jetbrains.annotations.NotNull
import javax.swing.JComponent
import javax.swing.JPanel


class PyTestImpSettingsComponent(val project: Project) {
    private val myPyTestIniPathText = TextFieldWithBrowseButton()

    init {
        myPyTestIniPathText.addBrowseFolderListener(
            project,
            FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle(@Suppress("DialogTitleCapitalization") "Choose py.test Config File")
                .withDescription("Path to pytest.ini or pyproject.toml:")
                .withFileFilter {
                    Comparing.equal(it.name, "pytest.ini", SystemInfo.isFileSystemCaseSensitive)
                            || Comparing.equal(it.name, "pyproject.toml", SystemInfo.isFileSystemCaseSensitive)
                }
                .withRoots(ProjectRootManager.getInstance(project).contentRoots.asList())
        )
    }

    val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(
            JBLabel("Path to pytest configuration (pytest.ini or pyproject.toml): "),
            myPyTestIniPathText,
            1,
            true,
        )
        .addComponentFillVertically(JPanel(), 0)
        .panel

    fun getPreferredFocusedComponent(): JComponent = myPyTestIniPathText

    var pyTestIniPathText: String
        get() = myPyTestIniPathText.text
        set(@NotNull text) {
            myPyTestIniPathText.text = text
        }
}

class PyTestImpSettingsConfigurable(val project: Project) : Configurable {
    private lateinit var mySettingsComponent: PyTestImpSettingsComponent

    override fun isModified(): Boolean {
        val settings = PyTestImpService.getInstance(project)
        return settings.pytestConfigPath != mySettingsComponent.pyTestIniPathText
    }

    override fun getDisplayName(): String {
        @Suppress("DialogTitleCapitalization")
        return "py.test"
    }

    override fun apply() {
        val settings = PyTestImpService.getInstance(project)
        settings.pytestConfigPath = mySettingsComponent.pyTestIniPathText
    }

    override fun reset() {
        val settings = PyTestImpService.getInstance(project)
        mySettingsComponent.pyTestIniPathText = settings.pytestConfigPath
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = PyTestImpSettingsComponent(project)
        return mySettingsComponent.panel
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mySettingsComponent.getPreferredFocusedComponent()
    }

    override fun disposeUIResources() {
//        mySettingsComponent = null
    }
}
