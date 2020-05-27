package com.y4kstudios.pytestimp

import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyNames
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.PyTestFrameworkService
import com.jetbrains.python.testing.TestRunnerService
import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.junit.Test

class LambdaFixtureTypingTest : PyTestTestCase() {
    private fun doTestStaticFixtureType(@Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import static_fixture, lambda_fixture

            my_toplevel_static_fixture = static_fixture(123)
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest("int", testFile)
    }

    fun testStaticFixtureTypeFromPytestParam() {
        doTestStaticFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_<caret>static_fixture):
                pass
        """)
    }

    fun testStaticFixtureTypeFromPytestParamLocal() {
        doTestStaticFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_static_fixture):
                _ = my_toplevel_<caret>static_fixture
        """)
    }

    fun testStaticFixtureTypeFromLambdaParam() {
        doTestStaticFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_<caret>static_fixture: None)
        """)
    }

    fun testStaticFixtureTypeFromLambdaParamLocal() {
        doTestStaticFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_static_fixture: my_toplevel_<caret>static_fixture)
        """)
    }

    private fun doTestPytestFixtureType(@Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import static_fixture, lambda_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                return 12
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest("int", testFile)
    }

    fun testPytestFixtureTypeFromPytestParam() {
        doTestPytestFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture):
                pass
        """)
    }

    fun testPytestFixtureTypeFromPytestParamLocal() {
        doTestPytestFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_pytest_fixture):
                _ = my_toplevel_<caret>pytest_fixture
        """)
    }

    fun testPytestFixtureTypeFromLambdaParam() {
        doTestPytestFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_<caret>pytest_fixture: None)
        """)
    }

    fun testPytestFixtureTypeFromLambdaParamLocal() {
        doTestPytestFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_pytest_fixture: my_toplevel_<caret>pytest_fixture)
        """)
    }

    private fun doTestPytestYieldFixtureType(@Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import static_fixture, lambda_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                yield 12
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest("int", testFile)
    }

    fun testPytestYieldFixtureTypeFromPytestParam() {
        doTestPytestYieldFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_<caret>pytest_fixture):
                pass
        """)
    }

    fun testPytestYieldFixtureTypeFromPytestParamLocal() {
        doTestPytestYieldFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_pytest_fixture):
                _ = my_toplevel_<caret>pytest_fixture
        """)
    }

    fun testPytestYieldFixtureTypeFromLambdaParam() {
        doTestPytestYieldFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_<caret>pytest_fixture: None)
        """)
    }

    fun testPytestYieldFixtureTypeFromLambdaParamLocal() {
        doTestPytestYieldFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_pytest_fixture: my_toplevel_<caret>pytest_fixture)
        """)
    }

    private fun doTestLambdaFixtureType(@Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            my_toplevel_lambda_fixture = lambda_fixture(lambda: 123)
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest("int", testFile)
    }

    @Test(expected = ComparisonFailure::class)
    fun testLambdaFixtureTypeFromPytestParam() {
        doTestLambdaFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture):
                pass
        """)
    }

    fun testLambdaFixtureTypeFromPytestParamLocal() {
        doTestLambdaFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testLambdaFixtureTypeFromLambdaParam() {
        doTestLambdaFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_<caret>lambda_fixture: None)
        """)
    }

    fun testLambdaFixtureTypeFromLambdaParamLocal() {
        doTestLambdaFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_lambda_fixture: my_toplevel_<caret>lambda_fixture)
        """)
    }

    private fun doTestLambdaParamsFixtureType(@Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            my_toplevel_lambda_fixture = lambda_fixture(params=[
                1,
                2.0,
                'c',
            ])
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest("Union[int, float, str]", testFile)
    }

    fun testLambdaParamsFixtureTypeFromPytestParam() {
        doTestLambdaParamsFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture):
                pass
        """)
    }

    fun testLambdaParamsFixtureTypeFromPytestParamLocal() {
        doTestLambdaParamsFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testLambdaParamsFixtureTypeFromLambdaParam() {
        doTestLambdaParamsFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_<caret>lambda_fixture: None)
        """)
    }

    fun testLambdaParamsFixtureTypeFromLambdaParamLocal() {
        doTestLambdaParamsFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_lambda_fixture: my_toplevel_<caret>lambda_fixture)
        """)
    }

    private fun doTestLambdaPytestParamsFixtureType(@Language("Python") caretDef: String) {
        @Language("Python")
        val baseFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            my_toplevel_lambda_fixture = lambda_fixture(params=[
                pytest.param(1),
                pytest.param(2.0),
                pytest.param('c'),
                'd',
            ])
        """

        val testFile = "${baseFile.trimIndent()}\n\n${caretDef.trimIndent()}"
        doTest("Union[int, float, str]", testFile)
    }

    fun testLambdaPytestParamsFixtureTypeFromPytestParam() {
        doTestLambdaPytestParamsFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_<caret>lambda_fixture):
                pass
        """)
    }

    fun testLambdaPytestParamsFixtureTypeFromPytestParamLocal() {
        doTestLambdaPytestParamsFixtureType("""
            @pytest.fixture
            def caret(my_toplevel_lambda_fixture):
                _ = my_toplevel_<caret>lambda_fixture
        """)
    }

    @Test(expected = ComparisonFailure::class)
    fun testLambdaPytestParamsFixtureTypeFromLambdaParam() {
        doTestLambdaPytestParamsFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_<caret>lambda_fixture: None)
        """)
    }

    fun testLambdaPytestParamsFixtureTypeFromLambdaParamLocal() {
        doTestLambdaPytestParamsFixtureType("""
            caret = lambda_fixture(lambda my_toplevel_lambda_fixture: my_toplevel_<caret>lambda_fixture)
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
