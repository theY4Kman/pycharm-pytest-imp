package com.y4kstudios.pytestimp.fixtures

import com.intellij.codeInsight.completion.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.completion.PythonLookupElement
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext


internal val PyParameterList.containingCallable: PyCallable?
    get() = this.parent as? PyCallable


/**
 * Contributes function argument names.
 */
class PyTestParameterCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().inside(PyParameterList::class.java), PyTestFunctionLambdaFixtureArgumentCompletion)
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(PyPlainStringElement::class.java)
                        .withAncestor(2, PlatformPatterns.psiElement(PyArgumentList::class.java)
                                .withParent(PlatformPatterns.psiElement(PyCallExpression::class.java)
                                        .with(object : PatternCondition<PyCallExpression>("") {
                                            override fun accepts(call: PyCallExpression, context: ProcessingContext?) = call.isLambdaFixture()
                                        })
                                )),
            LambdaFixtureReferenceArgumentCompletion)
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, result)
    }
}

private object PyTestFunctionLambdaFixtureArgumentCompletion : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = when (parameters.originalPosition.elementType) {
            PyTokenTypes.COLON -> parameters.originalPosition?.prevSibling
            else -> parameters.originalPosition
        } ?: return
        val pyCallable = PsiTreeUtil.getNonStrictParentOfType(element, PyParameterList::class.java)?.containingCallable ?: return
        val ourFixture = when (pyCallable) {
            is PyFunction -> pyCallable
            is PyLambdaExpression -> PsiTreeUtil.getParentOfType(pyCallable, PyCallExpression::class.java)
            else -> null
        }

        val module = ModuleUtilCore.findModuleForPsiElement(pyCallable) ?: return
        val typeEvalContext = TypeEvalContext.codeCompletion(pyCallable.project, pyCallable.containingFile)
        val usedParams = pyCallable.getParameters(typeEvalContext).mapNotNull { it.name }.toSet()

        getFixtures(module, pyCallable, typeEvalContext)
            .filter {
                if (usedParams.contains(it.name)) return@filter false
                return@filter when (val resolveTarget = it.resolveTarget) {
                    is PyTargetExpression -> resolveTarget.findAssignmentCall() != ourFixture
                    else -> resolveTarget != ourFixture
                }
            }
            .forEach {
                val icon =
                    if (it.isLambdaFixture()) AllIcons.Nodes.Variable
                    else AllIcons.Nodes.Function
                result.addElement(PythonLookupElement(it.name, false, icon))
            }
    }
}

private object LambdaFixtureReferenceArgumentCompletion : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val stringArg = parameters.originalPosition as? PyPlainStringElement ?: return
        val argList = PsiTreeUtil.getParentOfType(stringArg, PyArgumentList::class.java) ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(stringArg) ?: return
        val typeEvalContext = TypeEvalContext.codeCompletion(argList.project, argList.containingFile)

        val usedRefs = HashSet<String>()
        argList.acceptChildren(object : PyElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression) {
                usedRefs.add(node.stringValue)
            }
        })

        val target = argList.parentOfType<PyCallExpression>()?.let { call ->
            call.parentOfType<PyAssignmentStatement>()
                ?.targetsToValuesMapping
                ?.firstOrNull { it.second == call }
                ?.first
        }

        getFixtures(module, stringArg, typeEvalContext)
            .filter { !usedRefs.contains(it.name) && it.resolveTarget != target }
            .forEach {
                val icon =
                    if (it.isLambdaFixture()) AllIcons.Nodes.Variable
                    else AllIcons.Nodes.Function
                result.addElement(PythonLookupElement(it.name, false, icon))
            }
    }
}
