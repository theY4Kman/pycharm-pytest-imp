/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.y4kstudios.pytestimp.runconfigs

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.extensions.*
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.run.targetBasedConfiguration.targetAsPsiElement
import com.jetbrains.python.testing.*
import java.util.*


/**
 * Since runners report names of tests as qualified name, no need to convert it to PSI and back to string.
 * We just save its name and provide it again to rerun
 * @param metainfo additional info provided by test runner, in case of pytest it is test name with parameters (if test is parametrized)
 */
private class PyTargetBasedPsiLocation(val target: ConfigurationTarget,
                                       element: PsiElement,
                                       val metainfo: String?) : PsiLocation<PsiElement>(element) {
    override fun equals(other: Any?): Boolean {
        if (other is PyTargetBasedPsiLocation) {
            return target == other.target && metainfo == other.metainfo
        }
        return false
    }

    override fun hashCode(): Int {
        return target.hashCode()
    }
}

internal sealed class PyTestTargetForConfig(val configurationTarget: ConfigurationTarget,
                                            val workingDirectory: VirtualFile) {
    class PyTestPathTarget(target: String, workingDirectory: VirtualFile) :
        PyTestTargetForConfig(ConfigurationTarget(target, PyRunTargetVariant.PATH), workingDirectory)

    class PyTestPythonTarget(target: String, workingDirectory: VirtualFile, val namePaths: QualifiedNameParts) :
        PyTestTargetForConfig(ConfigurationTarget(target, PyRunTargetVariant.PYTHON), workingDirectory)
}

/**
 * Splits name owner's qname to [QualifiedNameParts]: filesystem part and element(symbol) part.
 *
 * @see [QualifiedName] extension
 * @return null if no file part found
 */
internal fun PyQualifiedNameOwner.tryResolveAndSplit(context: QNameResolveContext): QualifiedNameParts? {
    val qualifiedNameDottedString = this.qualifiedName ?: return getEmulatedQNameParts()

    val qualifiedName = QualifiedName.fromDottedString(qualifiedNameDottedString)
    val parts = qualifiedName.tryResolveAndSplit(context)
    if (parts != null) {
        return parts
    }
    val pyFile = containingFile as? PyFile ?: return null
    val fileQName = pyFile.getQName() ?: return null
    val relativePath = qualifiedName.getRelativeNameTo(fileQName) ?: return null
    return QualifiedNameParts(fileQName, relativePath, pyFile)
}

/**
 * For element containing dashes [PyQualifiedNameOwner.getQualifiedName] does not work.
 * this method creates [QualifiedNameParts] with file and qname path inside of it so even files with dashes could be supported
 * by test runners
 */
private fun PyQualifiedNameOwner.getEmulatedQNameParts(): QualifiedNameParts? {
    val ourFile = this.containingFile as? PyFile ?: return null
    val result = ArrayList<String>()
    var element: PsiElement = this
    while (element !is PsiFile) {
        if (element is NavigationItem) {
            val name = element.name
            if (name != null) {
                result.add(name)
            }
        }
        element = element.parent ?: return null
    }
    return QualifiedNameParts(QualifiedName.fromComponents(ourFile.virtualFile.nameWithoutExtension),
        QualifiedName.fromComponents(result.reversed()), ourFile)
}

/**
 * Splits qname to [QualifiedNameParts]: filesystem part and element(symbol) part.
 * Resolves parts sequentially until meets file. May be slow.
 *
 * @see [com.jetbrains.python.psi.PyQualifiedNameOwner] extensions
 * @return null if no file part found
 */
internal fun QualifiedName.tryResolveAndSplit(context: QNameResolveContext): QualifiedNameParts? {
    //TODO: May be slow, cache in this case

    // Find first element that may be file
    var i = this.componentCount
    while (i > 0) {
        val possibleFileName = this.subQualifiedName(0, i)
        val possibleFile = possibleFileName.resolveToElement(context)
        if (possibleFile is PyFile) {
            return QualifiedNameParts(possibleFileName,
                this.subQualifiedName(possibleFileName.componentCount, this.componentCount),
                possibleFile)
        }
        i--
    }
    return null
}

class PyTestImpConfigurationProducer : AbstractPythonTestConfigurationProducer<PyTestConfiguration>() {

    override val configurationClass: Class<PyTestConfiguration> = PyTestConfiguration::class.java

    private val myConfigurationFactory = PythonTestConfigurationType.getInstance().pyTestFactory

    override fun getConfigurationFactory() = myConfigurationFactory

    override fun setupConfigurationFromContext(configuration: PyTestConfiguration,
                                               context: ConfigurationContext,
                                               sourceElement: Ref<PsiElement>): Boolean {
        val element = sourceElement.get() ?: return false

        if (element.containingFile !is PyFile && element !is PsiDirectory) {
            return false
        }

        val location = context.location
        configuration.module = context.module
        configuration.isUseModuleSdk = true
        if (location is PyTargetBasedPsiLocation) {
            location.target.copyTo(configuration.target)
            location.metainfo?.let { configuration.setMetaInfo(it) }
        }
        else {
            val targetForConfig = getTargetForConfig(configuration, element)
                ?: return false
            targetForConfig.configurationTarget.copyTo(configuration.target)
            // Directory may be set in Default configuration. In that case no need to rewrite it.
            if (configuration.workingDirectory.isNullOrEmpty()) {
                configuration.workingDirectory = targetForConfig.workingDirectory.path
            }
        }
        configuration.setGeneratedName()
        return true
    }

    override fun isConfigurationFromContext(configuration: PyTestConfiguration, context: ConfigurationContext): Boolean {
        val location = context.location
        if (location is PyTargetBasedPsiLocation) {
            // With derived classes several configurations for same element may exist
            return configuration.isSameAsLocation(location.target, location.metainfo)
        }

        val psiElement = context.psiLocation ?: return false
        val configurationFromContext = createConfigurationFromContext(context)?.configuration as? PyTestConfiguration ?: return false
        if (configuration.target != configurationFromContext.target) {
            return false
        }

        //Even of both configurations have same targets, it could be that both have same qname which is resolved
        // to different elements due to different working folders.
        // Resolve them and check files
        if (configuration.target.targetType != PyRunTargetVariant.PYTHON) return true

        val targetPsi = targetAsPsiElement(configuration.target.targetType, configuration.target.target, configuration,
            configuration.getWorkingDirectoryAsVirtual()) ?: return true
        return targetPsi.containingFile == psiElement.containingFile
    }
}

internal fun PyTestConfiguration.getWorkingDirectoryAsVirtual(): VirtualFile? {
    if (!workingDirectory.isNullOrEmpty()) {
        return LocalFileSystem.getInstance().findFileByPath(workingDirectory)
    }
    return null
}

/**
 * Creates [ConfigurationTarget] to make  onfiguration work with provided element.
 * Also reports working dir what should be set to configuration to work correctly
 * @return [target, workingDirectory]
 */
internal fun getTargetForConfig(configuration: PyAbstractTestConfiguration,
                                baseElement: PsiElement
): PyTestTargetForConfig? {
    var element = baseElement
    // Go up until we reach top of the file
    // asking configuration about each element if it is supported or not
    // If element is supported -- set it as configuration target
    do {
        if (isPytestIniConfiguredTestElement(element)) {
            when (element) {
                is PyQualifiedNameOwner -> { // Function, class, method

                    val module = configuration.module ?: return null

                    val elementFile = element.containingFile as? PyFile ?: return null
                    val workingDirectory = getDirectoryForFileToBeImportedFrom(elementFile)
                        ?: return null
                    val context = QNameResolveContext(
                        ModuleBasedContextAnchor(module),
                        evalContext = TypeEvalContext.userInitiated(configuration.project, null),
                        folderToStart = workingDirectory.virtualFile
                    )
                    val parts = element.tryResolveAndSplit(context) ?: return null
                    val qualifiedName = parts.getElementNamePrependingFile(workingDirectory)
                    return PyTestTargetForConfig.PyTestPythonTarget(
                        qualifiedName.toString(),
                        workingDirectory.virtualFile,
                        parts
                    )
                }
                is PsiFileSystemItem -> {
                    val virtualFile = element.virtualFile

                    val workingDirectory: VirtualFile = when (element) {
                        is PyFile -> getDirectoryForFileToBeImportedFrom(element)?.virtualFile
                        is PsiDirectory -> virtualFile
                        else -> return null
                    } ?: return null
                    return PyTestTargetForConfig.PyTestPathTarget(virtualFile.path, workingDirectory)
                }
            }
        }
        element = element.parent ?: break
    }
    while (element !is PsiDirectory) // if parent is folder, then we are at file level
    return null
}

/**
 * Inspects file relative imports, finds farthest and returns folder with imported file
 */
private fun getDirectoryForFileToBeImportedFrom(file: PyFile): PsiDirectory? {
    val maxRelativeLevel = file.fromImports.maxOfOrNull { it.relativeLevel } ?: 0
    var elementFolder = file.parent ?: return null
    for (i in 1..maxRelativeLevel) {
        elementFolder = elementFolder.parent ?: return null
    }
    return elementFolder
}
