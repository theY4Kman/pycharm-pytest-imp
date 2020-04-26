package com.y4kstudios.pytestimp

import com.intellij.codeInsight.completion.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
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
                PlatformPatterns.psiElement(PyStringLiteralExpression::class.java)
                        .withParent(PlatformPatterns.psiElement(PyArgumentList::class.java)
                                .withParent(PlatformPatterns.psiElement(PyCallExpression::class.java)
                                        .with(object : PatternCondition<PyCallExpression>("") {
                                            override fun accepts(call: PyCallExpression, context: ProcessingContext?) = call.isLambdaFixture()
                                        })
                                )),
                LambdaFixtureReferenceArgumentCompletion)
    }
}

private object PyTestFunctionLambdaFixtureArgumentCompletion : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val pyCallable = PsiTreeUtil.getParentOfType(parameters.position, PyParameterList::class.java)?.containingCallable ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(pyCallable) ?: return
        val typeEvalContext = TypeEvalContext.codeCompletion(pyCallable.project, pyCallable.containingFile)
        val usedParams = pyCallable.getParameters(typeEvalContext).mapNotNull { it.name }.toSet()

        getLambdaFixtures(module, pyCallable, typeEvalContext)
                .filter { !usedParams.contains(it.name) }
                .forEach {
                    result.addElement(PythonLookupElement(it.name, false, AllIcons.Nodes.Variable))
                }
    }
}

private object LambdaFixtureReferenceArgumentCompletion : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val argList = PsiTreeUtil.getParentOfType(parameters.position, PyArgumentList::class.java) ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(argList) ?: return
        val typeEvalContext = TypeEvalContext.codeCompletion(argList.project, argList.containingFile)

        val usedRefs = HashSet<String>()
        argList.acceptChildren(object : PyElementVisitor() {
            override fun visitPyStringLiteralExpression(node: PyStringLiteralExpression?) {
                node?.stringValue?.let { usedRefs.add(it) }
            }
        })

        getLambdaFixtures(module, parameters.position, typeEvalContext)
                .filter { !usedRefs.contains(it.name) }
                .forEach {
                    result.addElement(PythonLookupElement(it.name, false, AllIcons.Nodes.Variable))
                }
    }
}
