package com.y4kstudios.pytestimp

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.jetbrains.python.PyNames
import com.jetbrains.python.nameResolver.NameResolverTools
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyTestFrameworkService
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.pyTestFixtures.*

private val pyTestName = PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)

internal fun isPyTestEnabled(module: Module) =
        TestRunnerService.getInstance(module).projectConfiguration == pyTestName

private val decoratorNames = arrayOf("pytest.fixture", "fixture")

private fun PyDecoratorList.hasDecorator(vararg names: String) = names.any { findDecorator(it) != null }

internal fun PyFunction.isFixture() = decoratorList?.hasDecorator(*decoratorNames) ?: false
internal fun PyCallable.isFixture() =
        this.asMethod()?.isFixture() ?: false
                || PsiTreeUtil.getParentOfType(this, PyCallExpression::class.java)?.isLambdaFixture() ?: false


/**
 * If named parameter has fixture -- return it
 */
internal fun getLambdaFixture(element: PyNamedParameter, typeEvalContext: TypeEvalContext): PyTestFixture? {
    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return null
    val func = PsiTreeUtil.getParentOfType(element, PyCallable::class.java) ?: return null
    return getLambdaFixtures(module, func, typeEvalContext).firstOrNull { o -> o.name == element.name }
}

internal fun getLambdaFixture(element: PyStringLiteralExpression, typeEvalContext: TypeEvalContext): PyTestFixture? {
    return getLambdaFixtures(element, typeEvalContext).firstOrNull { o -> o.name == element.stringValue }
}


/**
 * Gets list of fixtures suitable for certain function.
 *
 * [forWhat] function that you want to use fixtures with. Could be test or fixture itself.
 *
 * @return all pytest fixtures in project that could be used by [forWhat]
 */
internal fun getLambdaFixtures(module: Module, forWhat: PyCallable, typeEvalContext: TypeEvalContext): List<PyTestFixture> {
    // Fixtures could be used only by test functions or other fixtures.
    if (!(isPyTestEnabled(module) || forWhat.isFixture())) {
        return emptyList()
    }

    val selfName = if (forWhat is PyFunction) forWhat.name else PsiTreeUtil.getParentOfType(forWhat, PyTargetExpression::class.java)?.name
    return getLambdaFixtures(forWhat as PsiElement, typeEvalContext).filter { it.name != selfName }
}

internal fun getLambdaFixtures(forWhat: PsiElement, typeEvalContext: TypeEvalContext): List<PyTestFixture> {
    val topLevelFixtures =
            ((forWhat.containingFile as? PyFile)?.topLevelAttributes ?: emptyList())
                    .mapNotNull { it.asFixture }
                    .toList()

    // Class fixtures can't be used for top level functions
    val forWhatClass = PsiTreeUtil.getParentOfType(forWhat, PyClass::class.java) ?: return topLevelFixtures

    val classBasedFixtures = mutableListOf<PyTestFixture>()
    forWhatClass.visitUpwardNestedClassAttributes(
        Processor { target ->
            target?.asFixture?.let { classBasedFixtures.add(it) }
            true
        },
        typeEvalContext
    )
    return classBasedFixtures + topLevelFixtures
}

internal fun PyClass.visitUpwardNestedClassAttributes(processor: Processor<PyTargetExpression>, context: TypeEvalContext) {
    var pyClass = this

    while (true) {
        pyClass.visitClassAttributes(processor, true, context)

        pyClass = PsiTreeUtil.getParentOfType(pyClass, PyStatementList::class.java)?.let {
            PsiTreeUtil.getParentOfType(it, PyClass::class.java)
        } ?: return
    }
}

internal fun PyTestFixture.getLambdaFunction(): PyLambdaExpression? =  (resolveTarget as? PyTargetExpression)?.getLambdaFunction()

internal fun PyTargetExpression.getLambdaFunction(): PyLambdaExpression? {
    val assignedValue = this.findAssignedValue() as? PyCallExpression ?: return null
    return if (assignedValue.isLambdaFixture()) assignedValue.getLambdaFunction() else null
}

internal fun PyTargetExpression.getStaticFixtureValue(): PyExpression? {
    val assignedValue = this.findAssignedValue() as? PyCallExpression ?: return null
    return if (assignedValue.isStaticFixture()) assignedValue.getStaticFixtureValue() else null
}


internal fun PyCallExpression.isLambdaFixture() = NameResolverTools.isCalleeShortCut(this, LambdaFixtureFQNames.LAMBDA_FIXTURE)
internal fun PyCallExpression.getLambdaFunction() = this.getArgument(0, PyLambdaExpression::class.java)

internal fun PyCallExpression.isStaticFixture() = NameResolverTools.isCalleeShortCut(this, LambdaFixtureFQNames.STATIC_FIXTURE)
internal fun PyCallExpression.getStaticFixtureValue(): PyExpression? = this.getArgument(0, PyExpression::class.java)

internal fun PyCallExpression.isAnyLambdaFixture() = NameResolverTools.isCalleeShortCut(this,
        LambdaFixtureFQNames.LAMBDA_FIXTURE,
        LambdaFixtureFQNames.STATIC_FIXTURE,
        LambdaFixtureFQNames.ERROR_FIXTURE,
        LambdaFixtureFQNames.DISABLED_FIXTURE,
        LambdaFixtureFQNames.NOT_IMPLEMENTED_FIXTURE
)

internal fun PyCallExpression.getLambdaFixtureType(context: TypeEvalContext): Ref<PyType>? {
    if (this.isLambdaFixture()) {
        val lambda = this.getLambdaFunction()
        if (lambda != null) {
            val returnType = context.getReturnType(lambda)
            return Ref(returnType);
        }

        val lambdaRefs = this.argumentList?.arguments
                ?.filterIsInstance<PyStringLiteralExpression>()
                ?.map { it.references.filterIsInstance<LambdaFixtureReference>().firstOrNull() }
                ?: return null

        return when (lambdaRefs.size) {
            1 -> lambdaRefs.first()?.getCall()?.getLambdaFixtureType(context) ?: Ref()
            else -> Ref(PyTupleType.create(this, lambdaRefs.map { it?.getCall()?.getLambdaFixtureType(context)?.get() }))
        }

    } else if (this.isStaticFixture()) {
        val staticVal = this.getStaticFixtureValue()
        if (staticVal != null) {
            return Ref(context.getType(staticVal))
        }
    }

    return null
}

internal fun PyTargetExpression.isAnyLambdaFixture(): Boolean {
    val assignedValue = this.findAssignedValue()
    return assignedValue is PyCallExpression && assignedValue.isAnyLambdaFixture()
}

internal val PyTargetExpression.asFixture: PyTestFixture?
    get() {
        val call = this.findAssignedValue() as? PyCallExpression ?: return null
        if (!call.isAnyLambdaFixture()) {
            return null
        }

        val fixtureName = this.name ?: return null
        return PyTestFixture(null, this, fixtureName)
    }
