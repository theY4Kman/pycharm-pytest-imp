package com.y4kstudios.pytestimp

import com.intellij.codeInsight.completion.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
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
        val pyCallable = PsiTreeUtil.getParentOfType(parameters.position, PyParameterList::class.java)?.containingCallable ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(pyCallable) ?: return
        val typeEvalContext = TypeEvalContext.codeCompletion(pyCallable.project, pyCallable.containingFile)
        val usedParams = pyCallable.getParameters(typeEvalContext).mapNotNull { it.name }.toSet()

        val target = pyCallable.parentOfType<PyCallExpression>()?.let { call ->
            call.parentOfType<PyAssignmentStatement>()
                ?.targetsToValuesMapping
                ?.firstOrNull { it.second == call }
                ?.first
        }

        getFixtures(module, pyCallable, typeEvalContext)
                .filter { !usedParams.contains(it.name) && it.resolveTarget != target }
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
        val argList = PsiTreeUtil.getParentOfType(parameters.position, PyArgumentList::class.java) ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(parameters.position) ?: return
        val typeEvalContext = TypeEvalContext.codeCompletion(argList.project, argList.containingFile)

        val usedRefs = HashSet<String>()
        argList.acceptChildren(object : PyElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression?) {
                node?.stringValue?.let { usedRefs.add(it) }
            }
        })

        val target = argList.parentOfType<PyCallExpression>()?.let { call ->
            call.parentOfType<PyAssignmentStatement>()
                ?.targetsToValuesMapping
                ?.firstOrNull { it.second == call }
                ?.first
        }

        getFixtures(module, parameters.position, typeEvalContext)
                .filter { !usedRefs.contains(it.name) && it.resolveTarget != target }
                .forEach {
                    val icon =
                            if (it.isLambdaFixture()) AllIcons.Nodes.Variable
                            else AllIcons.Nodes.Function
                    result.addElement(PythonLookupElement(it.name, false, icon))
                }
    }
}
