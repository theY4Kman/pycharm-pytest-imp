package com.y4kstudios.pytestimp.fixtures

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import com.jetbrains.extensions.python.isCalleeName
import com.jetbrains.python.nameResolver.NameResolverTools
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.types.*
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.getFactoryById
import com.jetbrains.python.testing.pyTestFixtures.*

private val pyTestFactory = getFactoryById("py.test")

internal fun isPyTestEnabled(module: Module) =
        TestRunnerService.getInstance(module).selectedFactory == pyTestFactory

private val decoratorNames = arrayOf("pytest.fixture", "fixture")

private val PyFunction.asFixture: PyTestFixture?
  get() = decoratorList?.decorators?.firstOrNull { it.name in decoratorNames }?.let { createFixture(it) }

private fun PyDecoratorList.hasDecorator(vararg names: String) = names.any { findDecorator(it) != null }

internal fun PyFunction.isFixture() = decoratorList?.hasDecorator(*decoratorNames) ?: false
internal fun PyCallable.isFixture() =
        this.asMethod()?.isFixture() ?: false
                || PsiTreeUtil.getParentOfType(this, PyCallExpression::class.java)?.isLambdaFixture() ?: false

internal fun PyTestFixture.isLambdaFixture(): Boolean = this.resolveTarget is PyTargetExpression


/**
 * If named parameter has fixture -- return it
 */
internal fun getFixture(element: PyNamedParameter, typeEvalContext: TypeEvalContext): PyTestFixture? {
    val func = PsiTreeUtil.getParentOfType(element, PyCallable::class.java) ?: return null
    return getFixture(element.name, func, typeEvalContext)
}

internal fun getFixture(element: PyStringLiteralExpression, typeEvalContext: TypeEvalContext): PyTestFixture? {
    return getFixture(element.stringValue, element, typeEvalContext)
}

internal fun getFixture(name: String?, source: PsiElement, typeEvalContext: TypeEvalContext): PyTestFixture? {
    return getFixtures(source, typeEvalContext).firstOrNull { o -> o.name == name }
}

internal fun getFixtures(source: PsiElement, typeEvalContext: TypeEvalContext): List<PyTestFixture> {
    val module = ModuleUtilCore.findModuleForPsiElement(source) ?: return emptyList()
    return getFixtures(module, source, typeEvalContext)
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
    forWhatClass.visitNestingClassAttributes(
            Processor { target ->
            target?.asFixture?.let { classBasedFixtures.add(it) }
            true
        },
        typeEvalContext
    )
    return classBasedFixtures + topLevelFixtures
}

internal fun getOnlyNestingRegularFixtures(forWhat: PsiElement, typeEvalContext: TypeEvalContext): List<PyTestFixture> {
    // Class fixtures can't be used for top level functions
    val forWhatClass = PsiTreeUtil.getParentOfType(forWhat, PyClass::class.java)
            ?: return emptyList()

    val classBasedFixtures = mutableListOf<PyTestFixture>()
    forWhatClass.visitNestingMethods(Processor { func ->
        func.asFixture?.let { classBasedFixtures.add(it) }
        true
    }, false, typeEvalContext)
    return classBasedFixtures
}

internal fun getFixtures(module: Module, forWhat: PsiElement, context: TypeEvalContext): List<PyTestFixture> {
    val forWhatFile = forWhat.containingFile?.originalFile

    val topLevelFixtures =
            (findDecoratorsByName(module, *decoratorNames)
                    .filter { it.target?.containingClass == null } //We need only top-level functions, class-based fixtures processed above
                    .filter {  it.target?.containingFile == forWhatFile }
                    .mapNotNull { createFixture(it) }
             + ((forWhat.containingFile as? PyFile)?.topLevelAttributes ?: emptyList())
                    .mapNotNull { it.asFixture }
            ).toList()

    // Class fixtures can't be used for top level functions
    val forWhatClass = PsiTreeUtil.getParentOfType(forWhat, PyClass::class.java) ?: return topLevelFixtures

    val classBasedFixtures = mutableListOf<PyTestFixture>()
    forWhatClass.visitNestingClasses(Processor { pyClass ->
        // Get self/nesting class lambda fixtures
        pyClass.visitClassAttributes({ target ->
            target?.asFixture?.let { classBasedFixtures.add(it) }
            true
        }, true, context)

        // Get self/nesting class regular fixtures
        pyClass.visitMethods({ func ->
            func.asFixture?.let { classBasedFixtures.add(it) }
            true
        }, true, context)
    })
    return classBasedFixtures + topLevelFixtures
}

internal fun PyClass.visitNestingClasses(processor: Processor<PyClass>) =
        this.visitNestingClasses(processor, true)

internal fun PyClass.visitNestingClasses(processor: Processor<PyClass>, withSelf: Boolean) {
    if (withSelf) {
        processor.process(this)
    }

    var pyClass = this
    do {
        pyClass = PsiTreeUtil.getParentOfType(pyClass, PyStatementList::class.java)?.let {
            PsiTreeUtil.getParentOfType(it, PyClass::class.java)
        } ?: return

        processor.process(pyClass)
    } while (true)
}

internal fun PyClass.visitNestingMethods(processor: Processor<PyFunction>, context: TypeEvalContext) =
        this.visitNestingMethods(processor, true, context)

internal fun PyClass.visitNestingMethods(processor: Processor<PyFunction>, withSelf: Boolean, context: TypeEvalContext) {
    this.visitNestingClasses(Processor {
        it.visitMethods(processor, true, context)
        true
    }, withSelf)
}

internal fun PyClass.visitNestingClassAttributes(processor: Processor<PyTargetExpression>, context: TypeEvalContext) =
        this.visitNestingClassAttributes(processor, true, context)

internal fun PyClass.visitNestingClassAttributes(processor: Processor<PyTargetExpression>, withSelf: Boolean, context: TypeEvalContext) {
    this.visitNestingClasses(Processor {
        it.visitClassAttributes(processor, true, context)
        true
    }, withSelf)
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
        LambdaFixtureFQNames.NOT_IMPLEMENTED_FIXTURE,
        LambdaFixtureFQNames.PRECONDITION_FIXTURE
)

internal fun getFixtureReferenceType(reference: PsiReference, context: TypeEvalContext): Ref<PyType>? =
    getFixtureReferenceType(reference, context, null)

internal fun getFixtureReferenceType(reference: PsiReference, context: TypeEvalContext, ignore: PyCallExpression?): Ref<PyType>? {
    val type = when (reference) {
        is LambdaFixtureReference -> if (reference.getCall() != ignore) reference.getType(context) else null
        is PyTestFixtureReference -> reference.getType(context)
        else -> return null
    }
    return type?.let{ Ref(type) } ?: Ref()
}

internal fun PyCallExpression.isLambdaFixtureImplicitRef(): Boolean =
    this.isLambdaFixture() && this.arguments.isEmpty() && this.argumentList?.getKeywordArgument("params") == null

internal fun PyCallExpression.getLambdaFixtureType(context: TypeEvalContext): Ref<PyType>? {
    if (this.isLambdaFixture()) {
        val lambda = this.getLambdaFunction()
        if (lambda != null) {
            val returnType = context.getReturnType(lambda)
            return Ref(returnType);
        }

        val paramsArg = this.argumentList?.getKeywordArgument("params")
        if (paramsArg != null) {
            val paramsList = paramsArg.valueExpression as? PySequenceExpression ?: return Ref()
            val paramTypes =
                paramsList.elements
                    .map {
                        // Support pytest.param()
                        if (it is PyCallExpression && it.isCalleeName(PyTestFQNames.PYTEST_PARAM))
                            it.argumentList?.let { argumentList ->
                                argumentList.arguments.filterNot { it is PyKeywordArgument }.let { arguments ->
                                    when (arguments.size) {
                                        // pytest.param('single-value')
                                        1 -> context.getType(arguments[0])
                                        // pytest.param('multiple', 'values')
                                        else -> PyTupleType.create(argumentList, arguments.map { arg -> context.getType(arg) })
                                    }
                                }
                            }
                        else
                            context.getType(it)
                    }
            return Ref(PyUnionType.union(paramTypes))
        }

        val arguments = this.argumentList?.arguments
        val refParents =
                if (arguments.isNullOrEmpty()) listOf(this.parentOfType<PyTargetExpression>() ?: return null)
                else arguments.filterIsInstance<PyStringLiteralExpression>()
        val fixtureRefs = refParents.map { it.references.firstOrNull { ref -> ref is LambdaFixtureReference || ref is PyTestFixtureReference } }

        return when (fixtureRefs.size) {
            1 -> fixtureRefs.first()?.let { getFixtureReferenceType(it, context, this) }
            else -> Ref(PyTupleType.create(this, fixtureRefs.map {
                it?.let { getFixtureReferenceType(it, context, this)?.get() }
            }))
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

private fun createFixture(decorator: PyDecorator): PyTestFixture? {
    val target = decorator.target ?: return null
    val nameValue = decorator.argumentList?.getKeywordArgument("name")?.valueExpression
    if (nameValue != null) {
        val name = PyEvaluator.evaluate(nameValue, String::class.java) ?: return null
        return PyTestFixture(target, nameValue, name)
    }
    else {
        val name = target.name ?: return null
        return PyTestFixture(target, target, name)
    }
}
