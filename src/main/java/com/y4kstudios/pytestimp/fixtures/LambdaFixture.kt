package com.y4kstudios.pytestimp.fixtures

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.QualifiedName
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import com.intellij.util.asSafely
import com.jetbrains.python.extensions.python.isCalleeName
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.nameResolver.NameResolverTools
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyClassTypeImpl
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.rootManager
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.getFactoryById
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixture
import com.jetbrains.python.testing.pyTestFixtures.PyTestFixtureReference
import com.y4kstudios.pytestimp.PyTestImpService
import com.y4kstudios.pytestimp.PytestLoadPlugin

private val pyTestFactory = getFactoryById("py.test")

internal fun isPyTestEnabled(module: Module) =
        TestRunnerService.getInstance(module).selectedFactory == pyTestFactory

private val decoratorNames = arrayOf("pytest.fixture", "fixture", "pytest_asyncio.fixture")

private val PyFunction.asFixture: PyTestFixture?
  get() = decoratorList?.decorators?.firstOrNull { it.name in decoratorNames }?.let { createFixture(it) }

private fun PyDecoratorList.hasDecorator(vararg names: String) = names.any { findDecorator(it) != null }

internal fun PyFunction.isFixture() = decoratorList?.hasDecorator(*decoratorNames) ?: false

internal fun PyTestFixture.isLambdaFixture(): Boolean = this.resolveTarget is PyTargetExpression


/**
 * If named parameter has fixture -- return it
 */
internal fun getFixture(element: PyNamedParameter, typeEvalContext: TypeEvalContext): PyTestFixture? {
    val func = PsiTreeUtil.getParentOfType(element, PyCallable::class.java) ?: return null
    return getFixture(element.name, func, typeEvalContext)
}
fun PyNamedParameter.isFixture(typeEvalContext: TypeEvalContext) = getFixture(this, typeEvalContext) != null


internal fun getFixture(name: String?, source: PsiElement, typeEvalContext: TypeEvalContext): PyTestFixture? {
    return getFixtures(source, typeEvalContext).firstOrNull { o -> o.name == name }
}

internal fun getFixtures(source: PsiElement, typeEvalContext: TypeEvalContext): List<PyTestFixture> {
    val module = ModuleUtilCore.findModuleForPsiElement(source) ?: return emptyList()
    return getFixtures(module, source, typeEvalContext)
}

internal fun getFixtures(module: Module, forWhat: PsiElement, context: TypeEvalContext): List<PyTestFixture> {
    val forWhatFile = forWhat.containingFile?.originalFile
    val fixtureFiles = listOf(forWhatFile) + getContributingPluginFiles(forWhatFile)

    val topLevelFixtures =
        fixtureFiles
            .mapNotNull { it as? PyFile }
            .flatMap { it.iterateNames() }
            .mapNotNull {
                when (it) {
                    is PyTargetExpression -> it.asFixture
                    is PyFunction -> {
                        it.decoratorList?.decorators
                            ?.firstOrNull { decorator -> decorator.qualifiedName?.toString() in decoratorNames }
                            ?.let { decorator -> createFixture(decorator) }
                    }
                    else -> null
                }
            }

    // Class fixtures can't be used for top level functions
    val forWhatClass = PsiTreeUtil.getParentOfType(forWhat, PyClass::class.java) ?: return topLevelFixtures

    val classBasedFixtures = mutableListOf<PyTestFixture>()
    forWhatClass.visitNestingClasses { pyClass ->
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
    }
    return classBasedFixtures + topLevelFixtures
}

/**
 * Find a pytest `configfile` in the directory
 *
 * See https://docs.pytest.org/en/stable/reference/customize.html#finding-the-rootdir
 */
internal fun findConfigFileInDir(dir: PsiDirectory): PsiFile? =
    // TODO(zk): enforce add'l constraints for pyproject.toml, tox.ini, and setup.cfg
    listOf("pytest.ini",  "pyproject.toml", "tox.ini", "setup.cfg")
        .firstNotNullOfOrNull { filename -> dir.findFile(filename) }

internal fun getContributingPluginFiles(fromFile: PsiFile?): List<PsiFile> {
    if (fromFile == null) return emptyList()

    val containingModule = ModuleUtil.findModuleForFile(fromFile) ?: return emptyList()
    val contentRoots = containingModule.rootManager.contentRoots
    val pluginFiles = ArrayList<PsiFile>()

    // Add all conftest.py files up the chain, until pytest rootdir is found
    //  See also https://docs.pytest.org/en/stable/reference/customize.html#finding-the-rootdir
    var curDir = fromFile.containingDirectory ?: return emptyList()
    var topLevelConftest: PsiFile? = null
    while (true) {
        val conftest = curDir.findFile("conftest.py")
        if (conftest != null) {
            pluginFiles.add(conftest)
            topLevelConftest = conftest
        }

        if (curDir.virtualFile in contentRoots || findConfigFileInDir(curDir) != null) {
            break
        }

        curDir = curDir.parentDirectory ?: break
    }

    val extraPlugins = ArrayList<PytestLoadPlugin>()

    // If root conftest contains a pytest_plugins decl, also add those referenced modules
    topLevelConftest
        ?.asSafely<PyFile>()
        ?.findTopLevelAttribute("pytest_plugins")
        ?.findAssignedValue()
        ?.asSafely<PySequenceExpression>()
        ?.elements?.mapNotNull { it.asSafely<PyStringLiteralExpression>()?.stringValue }?.map { PytestLoadPlugin(it) }
        ?.toCollection(extraPlugins)

    // Add any plugins loaded explicitly from pytest config
    val project = fromFile.project
    val pytestImpService = project.service<PyTestImpService>()
    val pytestConfig = pytestImpService.pytestConfig
    if (pytestConfig != null) {
        extraPlugins.addAll(pytestConfig.loadedPlugins)
    }

    if (extraPlugins.isNotEmpty()) {
        val pyPsiFacade = fromFile.project.service<PyPsiFacade>()
        val resolveCtx = pyPsiFacade.createResolveContextFromFoothold(curDir)

        extraPlugins
            // TODO(zk): handle excluded plugins
            .asSequence()
            .filter { !it.excluded }
            .mapNotNull { plugin ->
                // plugin decls can be a full dotted path, or shorthand of `pytest_<name>`
                val candidates = arrayListOf(plugin.name)
                if ("." !in plugin.name) candidates.add("pytest_${plugin.name}")

                candidates
                    .map { QualifiedName.fromDottedString(it) }
                    .firstNotNullOfOrNull { qn ->
                        pyPsiFacade.resolveQualifiedName(qn, resolveCtx).getOrNull(0)
                    }
            }
            .mapNotNull { PyUtil.turnDirIntoInit(it) }
            .filterIsInstance<PyFile>()
            .toCollection(pluginFiles)
    }

    // TODO: Check for any pytest plugins within sdk

    return pluginFiles
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

internal fun PyCallExpression.isLambdaFixture() = NameResolverTools.isCalleeShortCut(this, LambdaFixtureFQNames.LAMBDA_FIXTURE)
internal fun PyCallExpression.getLambdaFixtureCallable(resolveContext: PyResolveContext): PyCallable? {
    var firstArg = this.getArgument(0, PyElement::class.java)
    if (firstArg is PyReferenceExpression) {
        val resolveResult = firstArg.followAssignmentsChain(resolveContext)
        firstArg = resolveResult.element as? PyElement
    }
    return firstArg as? PyCallable
}
internal fun PyCallExpression.isLambdaFixtureAsync() = this.getKeywordArgument("async_")?.asSafely<PyBoolLiteralExpression>()?.value ?: false

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

/** Lambda fixtures that accept lambda functions (which may request other fixtures) */
internal fun PyCallExpression.isDynamicLambdaFixture() = NameResolverTools.isCalleeShortCut(this,
        LambdaFixtureFQNames.LAMBDA_FIXTURE,
        LambdaFixtureFQNames.ERROR_FIXTURE,
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

internal fun PyCallExpression.supportsLambdaFixtureDestructuring(context: TypeEvalContext): Boolean {
    if (!isLambdaFixture()) return false

    val paramsArg = this.argumentList?.getKeywordArgument("params")
    if (paramsArg != null) {
        val paramsList = paramsArg.valueExpression as? PySequenceExpression ?: return false
        return paramsList.elements.any {
            if (it is PyCallExpression && it.isCalleeName(PyTestFQNames.PYTEST_PARAM))
                it.arguments.filterNot { arg -> arg is PyKeywordArgument }.let { arg -> arg.size > 1 }
            else
                context.getType(it) is PyTupleType
        }
    }

    return arguments.filterIsInstance<PyStringLiteralExpression>().size > 1
}

internal fun PyCallExpression.getLambdaFixtureType(context: TypeEvalContext): Ref<PyType>? {
    if (this.isLambdaFixture()) {
        val pyResolveContext = PyResolveContext.defaultContext(context)
        val callable = this.getLambdaFixtureCallable(pyResolveContext)
        if (callable != null) {
            val returnType = context.getReturnType(callable)

            // If an async fixture evaluates to an awaitable, use its wrapped type
            if (this.isLambdaFixtureAsync() && returnType != null) {
                PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType)?.let { return it }
            }

            return Ref(returnType)
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
                                argumentList.arguments.filterNot { arg -> arg is PyKeywordArgument }.let { arguments ->
                                    when (arguments.size) {
                                        // pytest.param('single-value')
                                        1 -> context.getType(arguments[0])
                                        // pytest.param('multiple', 'values')
                                        else -> PyTupleType.create(argumentList, arguments.map {
                                                arg -> context.getType(arg)
                                        })
                                    }
                                }
                            }
                        else
                            context.getType(it)
                    }
            return Ref(PyUnionType.union(paramTypes))
        }

        val arguments = this.argumentList?.arguments
        val fixtureRefs = mutableListOf<PsiReference>()

        // Implicit reference to fixture from higher scope
        //   e.g. `muffin = lambda_fixture()`
        if (arguments.isNullOrEmpty()) {
            val target = this.parentOfType<PyAssignmentStatement>()
                ?.targetsToValuesMapping
                ?.first { it.second == this }
                ?.first

            if (target != null) {
                val fixtureName = target.name
                val fixture = getFixtures(target, context).firstOrNull { it.name == fixtureName && it.resolveTarget != target }
                if (fixture != null) {
                    val fixtureRef =
                        if (fixture.isLambdaFixture()) LambdaFixtureReference(target, fixture)
                        else PyTestFixtureReference(target, fixture, null)
                    fixtureRefs.add(fixtureRef)
                }
            }
        }

        // Potentially, named references to other fixtures
        //  e.g. `muffin = lambda_fixture('gwar')`
        else {
            arguments.filterIsInstance<PyStringLiteralExpression>()
                .mapNotNull {
                    it.references.firstOrNull {
                        ref -> ref is LambdaFixtureReference || ref is PyTestFixtureReference
                    }
                }
                .toCollection(fixtureRefs)
        }

        return when (fixtureRefs.size) {
            1 -> getFixtureReferenceType(fixtureRefs.first(), context, this)
            else -> Ref(PyTupleType.create(this, fixtureRefs.map {
                it.let { getFixtureReferenceType(it, context, this)?.get() }
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

internal fun PyTargetExpression.findAssignmentCall(): PyCallExpression? {
    /**
     * NB: findAssignedValue() will fabricate a PySubscriptionExpression (e.g. `(assignedValue)[2]`) when the target is
     *     part of a tuple expression (i.e. destructuring assignment). This fabricated expression will exist in a dummy
     *     file. When trying to find the type of this fabricated expression, PyCharm will try to find the Python SDK
     *     configured for the dummy file — but won't be able to.
     *
     *     To skirt this issue, we pull the assigned value directly from the containing PyAssignmentStatement. This
     *     parent relationship *should* hold for all situations we're concerned with.
    */
    val assignmentExpr = this.parentOfType<PyAssignmentStatement>() ?: return null
    val assignedValue = assignmentExpr.assignedValue
    return assignedValue as? PyCallExpression
}

/**
 * If a lambda_fixture (or similar) target name is explicitly annotated with `LambdaFixture[V]`,
 * return the type of `V`.
 */
internal fun PyTargetExpression.getLambdaFixtureAnnotationType(context: TypeEvalContext): Ref<PyType>? {
    val annotation = this.annotation?.value ?: return null
    if (annotation !is PySubscriptionExpression) return null

    val qualifier = annotation.qualifier ?: return null
    if (!NameResolverTools.isNameShortCut(qualifier, LambdaFixtureFQNames.LAMBDA_FIXTURE_TYPE)) return null

    val index = annotation.indexExpression as? PyTypedElement ?: return null
    val annotationType = context.getType(index)?.let {
        if (it is PyClassTypeImpl) it.toInstance()
        else it
    } ?: return null

    return Ref(annotationType)
}

internal fun PyTargetExpression.isAnyLambdaFixture(): Boolean =
    findAssignmentCall()?.isAnyLambdaFixture() ?: false

internal val PyTargetExpression.asFixture: PyTestFixture?
    get() {
        val call = findAssignmentCall() ?: return null
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
