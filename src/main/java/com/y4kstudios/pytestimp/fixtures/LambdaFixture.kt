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
import com.intellij.util.castSafelyTo
import com.jetbrains.extensions.python.isCalleeName
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.nameResolver.NameResolverTools
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyEvaluator
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

private val decoratorNames = arrayOf("pytest.fixture", "fixture")

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

internal fun isTestFile(element: PsiElement?): Boolean {
    if (element == null) return false

    val containingFile = element.containingFile?.virtualFile ?: return false

    val project = element.project
    val pytestImpService = project.service<PyTestImpService>()
    val pytestConfig = pytestImpService.pytestConfig ?: return false

    val containingFileName = containingFile.name
    return pytestConfig.pythonFiles.matches(containingFileName)
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
        ?.castSafelyTo<PyFile>()
        ?.findTopLevelAttribute("pytest_plugins")
        ?.findAssignedValue()
        ?.castSafelyTo<PySequenceExpression>()
        ?.elements?.mapNotNull { it.castSafelyTo<PyStringLiteralExpression>()?.stringValue }?.map { PytestLoadPlugin(it) }
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
    val assignedValue = findAssignmentCall() ?: return null
    return if (assignedValue.isLambdaFixture()) assignedValue.getLambdaFunction() else null
}

internal fun PyTargetExpression.getStaticFixtureValue(): PyExpression? {
    val assignedValue = findAssignmentCall() ?: return null
    return if (assignedValue.isStaticFixture()) assignedValue.getStaticFixtureValue() else null
}


internal fun PyCallExpression.isLambdaFixture() = NameResolverTools.isCalleeShortCut(this, LambdaFixtureFQNames.LAMBDA_FIXTURE)
internal fun PyCallExpression.getLambdaFunction() = this.getArgument(0, PyLambdaExpression::class.java)
internal fun PyCallExpression.isLambdaFixtureAsync() = this.getKeywordArgument("async_")?.castSafelyTo<PyBoolLiteralExpression>()?.value ?: false

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
                it.arguments.filterNot { it is PyKeywordArgument }.let { it.size > 1 }
            else
                context.getType(it) is PyTupleType
        }
    }

    return arguments.filterIsInstance<PyStringLiteralExpression>().size > 1
}

internal fun PyCallExpression.getLambdaFixtureType(context: TypeEvalContext): Ref<PyType>? {
    if (this.isLambdaFixture()) {
        val lambda = this.getLambdaFunction()
        if (lambda != null) {
            val returnType = context.getReturnType(lambda)

            // If an async fixture evaluates to an awaitable, use its wrapped type
            if (this.isLambdaFixtureAsync() && returnType != null) {
                PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType)?.let { return it }
            }

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
