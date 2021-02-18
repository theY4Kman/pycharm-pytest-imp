package com.y4kstudios.pytestimp.runconfigs

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentsOfType
import com.intellij.util.ThreeState
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.isTestElement
import com.y4kstudios.pytestimp.PyTestImpService

object PyTestLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if ((element !is LeafPsiElement) || element.elementType != PyTokenTypes.IDENTIFIER) {
            return null
        }
        val testElement = element.parent ?: return null

        val typeEvalContext = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
        if ((testElement is PyClass || testElement is PyFunction)
            && (testElement is PsiNamedElement)
            && !isTestElement(testElement, ThreeState.UNSURE, typeEvalContext)  // don't add gutter icon if PyCharm already adds one
            && isPytestIniConfiguredTestElement(testElement)
            && testElement.parentsOfType(PyClass::class.java).all { isPytestIniConfiguredTestElement(it) }  // all parent classes should have valid test names, too
        ) {
            return withExecutorActions(AllIcons.RunConfigurations.TestState.Run)
        }
        return null
    }
}

fun isPytestIniConfiguredTestElement(element: PsiElement): Boolean {
    val service = PyTestImpService.getInstance(element.project)
    val pytestIni = service.pytestConfig ?: return false

    return when (element) {
        is PyFunction -> element.name?.let { pytestIni.pythonFunctions.matches(it) } ?: false
        is PyClass -> element.name?.let { pytestIni.pythonClasses.matches(it) } ?: false
        else -> false
    }
}
