package com.y4kstudios.pytestimp

import com.intellij.openapi.util.Ref
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.BaseReference
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixture

class LambdaFixtureReference(expression: PyExpression, fixture: PyTestFixture) : BaseReference(expression) {
    private val resolveRef = fixture.resolveTarget?.let { SmartPointerManager.createPointer(it) }
    private val callRef = ((fixture.resolveTarget as? PyTargetExpression)?.findAssignedValue() as? PyCallExpression)?.let { SmartPointerManager.createPointer(it) }

    override fun resolve() = resolveRef?.element

    fun getCall() = callRef?.element

    override fun isSoft() = true

    override fun handleElementRename(newElementName: String): PsiElement {
        if (myElement is PyNamedParameter) {
            return myElement.replace(PyElementGenerator.getInstance(myElement.project).createParameter(newElementName))!!
        }
        if (myElement is PyStringLiteralExpression) {
            return myElement.replace(PyElementGenerator.getInstance(myElement.project).createStringLiteral(myElement, newElementName))!!
        }

        return super.handleElementRename(newElementName)
    }
}

object LambdaFixtureTypeProvider : PyTypeProviderBase() {
    override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
        return getParameterType(param, func as PyCallable, context)
    }

    private fun getParameterType(param: PyNamedParameter, callable: PyCallable, context: TypeEvalContext): Ref<PyType>? {
        if (! context.maySwitchToAST(callable)) {
            return null
        }

        return param.references.filterIsInstance<LambdaFixtureReference>().firstOrNull()?.getCall()?.getLambdaFixtureType(context)
    }

    override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
        if (referenceTarget !is PyNamedParameter) return null;

        val paramList = referenceTarget.parent as? PyParameterList ?: return null;
        val callable = paramList.containingCallable ?: return null
        return getParameterType(referenceTarget, callable, context)
    }
}

private object LambdaFixtureReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val namedParam = element as? PyNamedParameter ?: return emptyArray()
        val fixture = getLambdaFixture(namedParam, TypeEvalContext.codeAnalysis(element.project, element.containingFile))
                ?: return emptyArray()
        return arrayOf(LambdaFixtureReference(namedParam, fixture))
    }
}

private object LambdaFixtureStringReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val stringLiteral = element as? PyStringLiteralExpression ?: return emptyArray()
        val fixture = getLambdaFixture(stringLiteral, TypeEvalContext.codeAnalysis(element.project, element.containingFile))
                ?: return emptyArray()
        return arrayOf(LambdaFixtureReference(stringLiteral, fixture))
    }
}

class LambdaFixtureReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement(PyParameter::class.java), LambdaFixtureReferenceProvider,
                PsiReferenceRegistrar.HIGHER_PRIORITY)

        registrar.registerReferenceProvider(
            psiElement(PyStringLiteralExpression::class.java)
                    .withParent(psiElement(PyArgumentList::class.java)
                            .withParent(psiElement(PyCallExpression::class.java)
                                    .with(object : PatternCondition<PyCallExpression>("") {
                                        override fun accepts(call: PyCallExpression, context: ProcessingContext?) = call.isLambdaFixture()
                                    })
                            )),
                LambdaFixtureStringReferenceProvider,
            PsiReferenceRegistrar.HIGHER_PRIORITY)
    }
}
