package com.y4kstudios.pytestimp.fixtures

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jetbrains.python.BaseReference
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixture
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixtureReference

class LambdaFixtureReference(expression: PyExpression, fixture: PyTestFixture) : BaseReference(expression) {
    private val resolveRef = fixture.resolveTarget?.let { SmartPointerManager.createPointer(it) }
    private val callRef = ((fixture.resolveTarget as? PyTargetExpression)?.findAssignedValue() as? PyCallExpression)?.let { SmartPointerManager.createPointer(it) }
    private val functionRef = fixture.function?.let { SmartPointerManager.createPointer(it) }

    override fun resolve() = resolveRef?.element

    fun getCall() = callRef?.element

    fun getFunction() = functionRef?.element

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

    fun getType(context: TypeEvalContext): PyType? =
        getCall()?.getLambdaFixtureType(context)?.get()
            ?: getFunction()?.getReturnStatementType(context)

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (super.isReferenceTo(element))
            return true

        if (element is PyTargetExpression || element is PyFunction) {
            if (this.getCall()?.isLambdaFixtureImplicitRef() != true) {
                // If we're not an implicit lambda fixture (e.g. `fixy = lambda_fixture()`),
                // our target expression in this fixture does not reference anything —
                // our lambda_fixture arguments hold all the references (if any).
                return false
            }

            /**
             * Using references' resolved elements would make this test a cinch.
             * Unfortunately, though, at time of writing PyTargetExpressions
             * do not support externally-sourced references using a PsiReferenceContributor
             * (like PyStringLiteralExpressions do).
             *
             * @see com.jetbrains.python.psi.impl.PyTargetExpressionImpl.getReference
             * @see com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl.getReferences
             */
            val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
            val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return false
            return getFixtures(module, this.element, context).firstOrNull()?.resolveTarget == element
        }

        return false
    }
}

internal fun PyTestFixtureReference.getType(context: TypeEvalContext): PyType? =
    getFunction()?.let { func ->
        val returnType = context.getReturnType(func)
        return if (!func.isGenerator) {
            returnType
        } else {
            //If generator function returns collection this collection is generator
            // which generates iteratedItemType.
            // We also must open union (toStream)
            val itemTypes = PyTypeUtil.toStream(returnType)
                .map {
                    if (it is PyCollectionType && PyTypingTypeProvider.isGenerator(it))
                        it.iteratedItemType
                    else it
                }.toList()
            PyUnionType.union(itemTypes)
        }
    }

object LambdaFixtureTypeProvider : PyTypeProviderBase() {
    override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
        return getParameterType(param, func as PyCallable, context)
    }

    private fun getParameterType(param: PyNamedParameter, callable: PyCallable, context: TypeEvalContext): Ref<PyType>? {
        if (!context.maySwitchToAST(callable)) {
            return null
        }

        val explicitType = param.getArgumentType(context)
        if (explicitType != null) {
            return Ref(explicitType)
        }

        return (
            param.references
                .firstOrNull { it is LambdaFixtureReference || it is PyTestFixtureReference }
                ?.let { getFixtureReferenceType(it, context) }
            )
    }

    override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
        if (referenceTarget !is PyNamedParameter) return null;

        val paramList = referenceTarget.parent as? PyParameterList ?: return null;
        val callable = paramList.containingCallable ?: return null
        return getParameterType(referenceTarget, callable, context)
    }
}

class LambdaFixtureReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement(PyParameter::class.java),
            LambdaFixtureReferenceProvider,
            PsiReferenceRegistrar.HIGHER_PRIORITY + 10)

        registrar.registerReferenceProvider(
            psiElement(PyTargetExpression::class.java)
                .withParent(psiElement(PyAssignmentStatement::class.java)
                    .withLastChild(psiElement(PyCallExpression::class.java)
                        .with(object : PatternCondition<PyCallExpression>("callIsLambdaFixture") {
                            override fun accepts(call: PyCallExpression, context: ProcessingContext?) = call.isLambdaFixture()
                        })
                        .withChild(psiElement(PyArgumentList::class.java)
                            .with(object : PatternCondition<PyArgumentList>("lambdaFixtureCallHasNoArguments") {
                                override fun accepts(argList: PyArgumentList, context: ProcessingContext?) = argList.arguments.isEmpty()
                            })))),
            LambdaFixtureImplicitReferenceProvider,
            PsiReferenceRegistrar.HIGHER_PRIORITY + 10)

        registrar.registerReferenceProvider(
            psiElement(PyStringLiteralExpression::class.java)
                .withParent(psiElement(PyArgumentList::class.java)
                    .withParent(psiElement(PyCallExpression::class.java)
                        .with(object : PatternCondition<PyCallExpression>("callIsLambdaFixture") {
                            override fun accepts(call: PyCallExpression, context: ProcessingContext?) = call.isLambdaFixture()
                        })
                    )),
            LambdaFixtureStringReferenceProvider,
            PsiReferenceRegistrar.HIGHER_PRIORITY + 10)
    }
}

private object LambdaFixtureReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val namedParam = element as? PyNamedParameter ?: return emptyArray()
        val fixture = getFixture(namedParam, TypeEvalContext.codeAnalysis(element.project, element.containingFile))
            ?: return emptyArray()
        return arrayOf(
            if (fixture.isLambdaFixture()) LambdaFixtureReference(namedParam, fixture)
            else PyTestFixtureReference(namedParam, fixture)
        )
    }
}

private object LambdaFixtureImplicitReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val target = element as? PyTargetExpression ?: return emptyArray()
        val call = target.findAssignedValue() ?: return emptyArray()
        val implicitName = target.assignedQName?.lastComponent
        val fixture = getFixture(implicitName, call, TypeEvalContext.codeAnalysis(element.project, element.containingFile))
            ?: return emptyArray()
        return arrayOf(LambdaFixtureReference(target, fixture))
    }
}

private object LambdaFixtureStringReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val stringLiteral = element as? PyStringLiteralExpression ?: return emptyArray()
        val name = element.stringValue
        val call = element.parentOfType<PyCallExpression>() ?: return emptyArray()
        val target = call.parentOfType<PyAssignmentStatement>()?.targetsToValuesMapping?.firstOrNull { it.second == call }?.first
        val fixture =
            getFixtures(stringLiteral, TypeEvalContext.codeAnalysis(element.project, element.containingFile))
                .firstOrNull { it.name == name && it.resolveTarget != target }
            ?: return emptyArray()
        return arrayOf(LambdaFixtureReference(stringLiteral, fixture))
    }
}
