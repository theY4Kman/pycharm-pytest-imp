package com.y4kstudios.pytestimp

import com.intellij.psi.util.parentOfType
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.junit.Test

class LambdaFixtureTypingTest : PyTestTestCase() {
    private fun doTestStaticFixtureType(@Language("Python") fixtureValue: String,
                                        @Language("Python") expectedType: String,
                                        @Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import static_fixture, lambda_fixture

            my_toplevel_static_fixture = static_fixture(${fixtureValue})
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest(expectedType, testFile)
    }

    private fun doTestStaticFixtureType_int(@Language("Python") caretDef: String) {
        doTestStaticFixtureType("123", "int", caretDef)
    }

    fun testStaticFixtureTypeFromPytestParam() {
        doTestStaticFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_<caret>static_fixture):
                pass
        """)
    }

    fun testStaticFixtureRefTypeFromPytestParam() {
        doTestStaticFixtureType_int("""
            my_toplevel_static_fixture_ref = lambda_fixture('my_toplevel_static_fixture')

            @pytest.fixture
            def caret(my_toplevel_<caret>static_fixture_ref):
                pass
        """)
    }

    fun testStaticFixtureTypeFromPytestParamWithExplicitAnnotation() {
        doTestStaticFixtureType("123", "str", """
            @pytest.fixture
            def caret(my_toplevel_<caret>static_fixture: str):
                pass
        """)
    }

    fun testStaticFixtureTypeFromPytestParamLocal() {
        doTestStaticFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_static_fixture):
                _ = my_toplevel_<caret>static_fixture
        """)
    }

    fun testStaticFixtureRefTypeFromPytestParamLocal() {
        doTestStaticFixtureType_int("""
            my_toplevel_static_fixture_ref = lambda_fixture('my_toplevel_static_fixture')

            @pytest.fixture
            def caret(my_toplevel_static_fixture_ref):
                _ = my_toplevel_<caret>static_fixture_ref
        """)
    }

    fun testStaticFixtureTypeFromPytestParamLocalWithExplicitAnnotation() {
        doTestStaticFixtureType("123", "str", """
            @pytest.fixture
            def caret(my_toplevel_static_fixture: str):
                _ = my_toplevel_<caret>static_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testStaticFixtureTypeFromLambdaParam() {
        doTestStaticFixtureType_int( """
            caret = lambda_fixture(lambda my_toplevel_<caret>static_fixture: None)
        """)
    }

    fun testStaticFixtureTypeFromLambdaParamLocal() {
        doTestStaticFixtureType_int("""
            caret = lambda_fixture(lambda my_toplevel_static_fixture: my_toplevel_<caret>static_fixture)
        """)
    }

    fun testStaticFixtureRefTypeFromLambdaParamLocal() {
        doTestStaticFixtureType_int("""
            my_toplevel_static_fixture_ref = lambda_fixture('my_toplevel_static_fixture')

            caret = lambda_fixture(lambda my_toplevel_static_fixture_ref: my_toplevel_<caret>static_fixture_ref)
        """)
    }

    private fun doTestPytestFixtureType(@Language("Python") fixtureValue: String,
                                        @Language("Python") expectedType: String,
                                        @Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import static_fixture, lambda_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                return $fixtureValue
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest(expectedType, testFile)
    }

    private fun doTestPytestFixtureType_int(@Language("Python") caretDef: String) {
        doTestPytestFixtureType("345", "int", caretDef)
    }

    fun testPytestFixtureTypeFromPytestParam() {
        doTestPytestFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture):
                pass
        """)
    }

    fun testPytestFixtureRefTypeFromPytestParam() {
        doTestPytestFixtureType_int("""
            my_toplevel_pytest_fixture_ref = lambda_fixture('my_toplevel_pytest_fixture')

            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture_ref):
                pass
        """)
    }

    fun testPytestFixtureTypeFromPytestParamWithExplicitAnnotation() {
        doTestPytestFixtureType("345", "str", """
            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture: str):
                pass
        """)
    }

    fun testPytestFixtureTypeFromPytestParamLocal() {
        doTestPytestFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_pytest_fixture):
                _ = my_toplevel_<caret>pytest_fixture
        """)
    }

    fun testPytestFixtureRefTypeFromPytestParamLocal() {
        doTestPytestFixtureType_int("""
            my_toplevel_pytest_fixture_ref = lambda_fixture('my_toplevel_pytest_fixture')

            @pytest.fixture
            def caret(my_toplevel_pytest_fixture_ref):
                _ = my_toplevel_<caret>pytest_fixture_ref
        """)
    }

    fun testPytestFixtureTypeFromPytestParamLocalWithExplicitAnnotation() {
        doTestPytestFixtureType("345", "str", """
            @pytest.fixture
            def caret(my_toplevel_pytest_fixture: str):
                _ = my_toplevel_<caret>pytest_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testPytestFixtureTypeFromLambdaParam() {
        doTestPytestFixtureType_int("""
            caret = lambda_fixture(lambda my_toplevel_<caret>pytest_fixture: None)
        """)
    }

    fun testPytestFixtureTypeFromLambdaParamLocal() {
        doTestPytestFixtureType_int("""
            caret = lambda_fixture(lambda my_toplevel_pytest_fixture: my_toplevel_<caret>pytest_fixture)
        """)
    }

    fun testPytestFixtureRefTypeFromLambdaParamLocal() {
        doTestPytestFixtureType_int("""
            my_toplevel_pytest_fixture_ref = lambda_fixture('my_toplevel_pytest_fixture')

            caret = lambda_fixture(lambda my_toplevel_pytest_fixture_ref: my_toplevel_<caret>pytest_fixture_ref)
        """)
    }

    private fun doTestPytestYieldFixtureType(@Language("Python") fixtureValue: String,
                                             @Language("Python") expectedType: String,
                                             @Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import static_fixture, lambda_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                yield $fixtureValue
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest(expectedType, testFile)
    }

    private fun doTestPytestYieldFixtureType_int(@Language("Python") caretDef: String) {
        doTestPytestYieldFixtureType("567", "int", caretDef)
    }

    fun testPytestYieldFixtureTypeFromPytestParam() {
        doTestPytestYieldFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture):
                pass
        """)
    }

    fun testPytestYieldFixtureRefTypeFromPytestParam() {
        doTestPytestYieldFixtureType_int("""
            my_toplevel_pytest_fixture_ref = lambda_fixture('my_toplevel_pytest_fixture')

            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture_ref):
                pass
        """)
    }

    fun testPytestYieldFixtureTypeFromPytestParamWithExplicitAnnotation() {
        doTestPytestYieldFixtureType("567", "str", """
            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture: str):
                pass
        """)
    }

    fun testPytestYieldFixtureTypeFromPytestParamLocal() {
        doTestPytestYieldFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_pytest_fixture):
                _ = my_toplevel_<caret>pytest_fixture
        """)
    }

    fun testPytestYieldFixtureRefTypeFromPytestParamLocal() {
        doTestPytestYieldFixtureType_int("""
            my_toplevel_pytest_fixture_ref = lambda_fixture('my_toplevel_pytest_fixture')

            @pytest.fixture
            def caret(my_toplevel_pytest_fixture_ref):
                _ = my_toplevel_<caret>pytest_fixture_ref
        """)
    }

    fun testPytestYieldFixtureTypeFromPytestParamLocalWithExplicitAnnotation() {
        doTestPytestYieldFixtureType("567", "str", """
            @pytest.fixture
            def caret(my_toplevel_pytest_fixture: str):
                _ = my_toplevel_<caret>pytest_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testPytestYieldFixtureTypeFromLambdaParam() {
        doTestPytestYieldFixtureType_int("""
            caret = lambda_fixture(lambda my_toplevel_<caret>pytest_fixture: None)
        """)
    }

    fun testPytestYieldFixtureTypeFromLambdaParamLocal() {
        doTestPytestYieldFixtureType_int("""
            caret = lambda_fixture(lambda my_toplevel_pytest_fixture: my_toplevel_<caret>pytest_fixture)
        """)
    }

    fun testPytestYieldFixtureRefTypeFromLambdaParamLocal() {
        doTestPytestYieldFixtureType_int("""
            my_toplevel_pytest_fixture_ref = lambda_fixture('my_toplevel_pytest_fixture')

            caret = lambda_fixture(lambda my_toplevel_pytest_fixture_ref: my_toplevel_<caret>pytest_fixture_ref)
        """)
    }

    private fun doTestLambdaFixtureType(@Language("Python") fixtureValue: String,
                                        @Language("Python") expectedType: String,
                                        @Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            my_toplevel_lambda_fixture = lambda_fixture(lambda: ${fixtureValue})
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest(expectedType, testFile)
    }

    private fun doTestLambdaFixtureType_int(@Language("Python") caretDef: String) {
        doTestLambdaFixtureType("789", "int", caretDef)
    }

    fun testLambdaFixtureTypeFromPytestParam() {
        doTestLambdaFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture):
                pass
        """)
    }

    fun testLambdaFixtureRefTypeFromPytestParam() {
        doTestLambdaFixtureType_int("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture_ref):
                pass
        """)
    }

    fun testLambdaFixtureTypeFromPytestParamWithExplicitAnnotation() {
        doTestLambdaFixtureType("789", "str","""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture: str):
                pass
        """)
    }

    fun testLambdaFixtureTypeFromPytestParamLocal() {
        doTestLambdaFixtureType_int("""
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    fun testLambdaFixtureRefTypeFromPytestParamLocal() {
        doTestLambdaFixtureType_int("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            @pytest.fixture
            def caret(my_toplevel_lambda_fixture_ref):
                _ = my_toplevel_<caret>lambda_fixture_ref
        """)
    }

    fun testLambdaFixtureTypeFromPytestParamLocalWithExplicitAnnotation() {
        doTestLambdaFixtureType("789", "str","""
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture: str):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testLambdaFixtureTypeFromLambdaParam() {
        doTestLambdaFixtureType_int("""
            caret = lambda_fixture(lambda my_toplevel_<caret>lambda_fixture: None)
        """)
    }

    fun testLambdaFixtureTypeFromLambdaParamLocal() {
        doTestLambdaFixtureType_int("""
            caret = lambda_fixture(lambda my_toplevel_lambda_fixture: my_toplevel_<caret>lambda_fixture)
        """)
    }

    fun testLambdaFixtureRefTypeFromLambdaParamLocal() {
        doTestLambdaFixtureType_int("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            caret = lambda_fixture(lambda my_toplevel_lambda_fixture_ref: my_toplevel_<caret>lambda_fixture_ref)
        """)
    }

    private fun doTestLambdaParamsFixtureType(@Language("Python") fixtureValue: String,
                                              @Language("Python") expectedType: String,
                                              @Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            my_toplevel_lambda_fixture = lambda_fixture(params=${fixtureValue})
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest(expectedType, testFile)
    }

    private fun doTestLambdaParamsFixtureType_Union(@Language("Python") caretDef: String) {
        doTestLambdaParamsFixtureType("[1, 2.0, 'c']", "int | float | LiteralString", caretDef)
    }

    fun testLambdaParamsFixtureTypeFromPytestParam() {
        doTestLambdaParamsFixtureType_Union("""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture):
                pass
        """)
    }

    fun testLambdaParamsFixtureRefTypeFromPytestParam() {
        doTestLambdaParamsFixtureType_Union("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture_ref):
                pass
        """)
    }

    fun testLambdaParamsFixtureTypeFromPytestParamWithExplicitAnnotation() {
        doTestLambdaParamsFixtureType("[1, 2.0, 'c']", "str", """
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture: str):
                pass
        """)
    }

    fun testLambdaParamsFixtureTypeFromPytestParamLocal() {
        doTestLambdaParamsFixtureType_Union("""
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    fun testLambdaParamsFixtureRefTypeFromPytestParamLocal() {
        doTestLambdaParamsFixtureType_Union("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            @pytest.fixture
            def caret(my_toplevel_lambda_fixture_ref):
                _ = my_toplevel_<caret>lambda_fixture_ref
        """)
    }

    fun testLambdaParamsFixtureTypeFromPytestParamLocalWithExplicitAnnotation() {
        doTestLambdaParamsFixtureType("[1, 2.0, 'c']", "str", """
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture: str):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testLambdaParamsFixtureTypeFromLambdaParam() {
        doTestLambdaParamsFixtureType_Union("""
            caret = lambda_fixture(lambda my_toplevel_<caret>lambda_fixture: None)
        """)
    }

    fun testLambdaParamsFixtureTypeFromLambdaParamLocal() {
        doTestLambdaParamsFixtureType_Union("""
            caret = lambda_fixture(lambda my_toplevel_lambda_fixture: my_toplevel_<caret>lambda_fixture)
        """)
    }

    fun testLambdaParamsFixtureRefTypeFromLambdaParamLocal() {
        doTestLambdaParamsFixtureType_Union("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            caret = lambda_fixture(lambda my_toplevel_lambda_fixture_ref: my_toplevel_<caret>lambda_fixture_ref)
        """)
    }

    private fun doTestLambdaPytestParamsFixtureType(@Language("Python") expectedType: String,
                                                    @Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            my_toplevel_lambda_fixture = lambda_fixture(params=[
                pytest.param(1),
                pytest.param(2.0),
                pytest.param('c'),
                pytest.param('d', id='ddddddd'),
                'e',
            ])
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest(expectedType, testFile)
    }

    private fun doTestLambdaPytestParamsFixtureType_Union(@Language("Python") caretDef: String) {
        doTestLambdaPytestParamsFixtureType("int | float | LiteralString", caretDef)
    }

    fun testLambdaPytestParamsFixtureTypeFromPytestParam() {
        doTestLambdaPytestParamsFixtureType_Union("""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture):
                pass
        """)
    }

    fun testLambdaPytestParamsFixtureRefTypeFromPytestParam() {
        doTestLambdaPytestParamsFixtureType_Union("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture_ref):
                pass
        """)
    }

    fun testLambdaPytestParamsFixtureTypeFromPytestParamWithExplicitAnnotation() {
        doTestLambdaPytestParamsFixtureType("int","""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture: int):
                pass
        """)
    }

    fun testLambdaPytestParamsFixtureTypeFromPytestParamLocal() {
        doTestLambdaPytestParamsFixtureType_Union("""
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    fun testLambdaPytestParamsFixtureRefTypeFromPytestParamLocal() {
        doTestLambdaPytestParamsFixtureType_Union("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            @pytest.fixture
            def caret(my_toplevel_lambda_fixture_ref):
                _ = my_toplevel_<caret>lambda_fixture_ref
        """)
    }

    fun testLambdaPytestParamsFixtureTypeFromPytestParamLocalWithExplicitAnnotation() {
        doTestLambdaPytestParamsFixtureType("int", """
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture: int):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testLambdaPytestParamsFixtureTypeFromLambdaParam() {
        doTestLambdaPytestParamsFixtureType_Union("""
            caret = lambda_fixture(lambda my_toplevel_<caret>lambda_fixture: None)
        """)
    }

    fun testLambdaPytestParamsFixtureTypeFromLambdaParamLocal() {
        doTestLambdaPytestParamsFixtureType_Union("""
            caret = lambda_fixture(lambda my_toplevel_lambda_fixture: my_toplevel_<caret>lambda_fixture)
        """)
    }

    fun testLambdaPytestParamsFixtureRefTypeFromLambdaParamLocal() {
        doTestLambdaPytestParamsFixtureType_Union("""
            my_toplevel_lambda_fixture_ref = lambda_fixture('my_toplevel_lambda_fixture')

            caret = lambda_fixture(lambda my_toplevel_lambda_fixture_ref: my_toplevel_<caret>lambda_fixture_ref)
        """)
    }

    private fun doTest(expectedType: String, @Language("Python") testFile: String) {
        myFixture.configureByText("test.py", testFile.trimIndent())
        val element = elementAtCaret
        val expr = element as? PyTypedElement ?: element.parentOfType<PyTypedElement>()
        assertNotNull("No element at caret", expr)
        expr ?: return

        val codeAnalysis = TypeEvalContext.codeAnalysis(expr.project, expr.containingFile)
        val userInitiated = TypeEvalContext.userInitiated(expr.project, expr.containingFile).withTracing()
        assertType("Failed in code analysis context", expectedType, expr, codeAnalysis)
        assertType("Failed in user initiated context", expectedType, expr, userInitiated)
    }
}
