package com.y4kstudios.pytestimp

import com.intellij.codeInsight.completion.CompletionType
import org.intellij.lang.annotations.Language

class LambdaFixtureCompletionTest : PyTestTestCase() {
    fun testCompleteToplevelFixturesFromLambdaParams() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture, static_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                pass

            my_toplevel_lambda_fixture = lambda_fixture(lambda: 123)
            my_toplevel_static_fixture = static_fixture('abc')

            caret = lambda_fixture(lambda <caret>: 123)
        """.trimIndent()

        doTest(
            testFile,
            "my_toplevel_pytest_fixture",
            "my_toplevel_lambda_fixture",
            "my_toplevel_static_fixture"
        )
    }

    fun testCompleteToplevelFixturesFromLambdaRefParams() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture, static_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                pass

            my_toplevel_lambda_fixture = lambda_fixture(lambda: 123)
            my_toplevel_static_fixture = static_fixture('abc')

            caret = lambda_fixture('<caret>')
        """.trimIndent()

        doTest(
            testFile,
            "my_toplevel_pytest_fixture",
            "my_toplevel_lambda_fixture",
            "my_toplevel_static_fixture"
        )
    }

    fun testCompleteToplevelFixturesFromLambdaTupleRefParams() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture, static_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                pass

            my_toplevel_lambda_fixture = lambda_fixture(lambda: 123)
            my_toplevel_static_fixture = static_fixture('abc')

            caret = lambda_fixture('my_toplevel_pytest_fixture','<caret>')
        """.trimIndent()

        doTest(
            testFile,
            "my_toplevel_pytest_fixture",
            "my_toplevel_lambda_fixture",
            "my_toplevel_static_fixture"
        )
    }

    fun testCompleteToplevelFixturesFromPytestParams() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture, static_fixture

            @pytest.fixture
            def my_toplevel_pytest_fixture():
                pass

            my_toplevel_lambda_fixture = lambda_fixture(lambda: 123)
            my_toplevel_static_fixture = static_fixture('abc')

            @pytest.fixture
            def caret(<caret>):
                pass
        """.trimIndent()

        doTest(
            testFile,
            "my_toplevel_pytest_fixture",
            "my_toplevel_lambda_fixture",
            "my_toplevel_static_fixture"
        )
    }

    fun testCompleteClassFixturesFromLambdaParams() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture, static_fixture

            class TestIt:
                @pytest.fixture
                def my_class_pytest_fixture(self):
                    pass

                my_class_lambda_fixture = lambda_fixture(lambda: 123)
                my_class_static_fixture = static_fixture('abc')

                caret = lambda_fixture(lambda <caret>: 123)
        """.trimIndent()

        doTest(
            testFile,
            "my_class_pytest_fixture",
            "my_class_lambda_fixture",
            "my_class_static_fixture"
        )
    }

    fun testCompleteClassFixturesFromLambdaRefParams() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture, static_fixture

            class TestIt:
                @pytest.fixture
                def my_class_pytest_fixture(self):
                    pass

                my_class_lambda_fixture = lambda_fixture(lambda: 123)
                my_class_static_fixture = static_fixture('abc')

                caret = lambda_fixture('<caret>')
        """.trimIndent()

        doTest(
            testFile,
            "my_class_pytest_fixture",
            "my_class_lambda_fixture",
            "my_class_static_fixture"
        )
    }

    fun testCompleteClassFixturesFromPytestParams() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture, static_fixture

            class TestIt:
                @pytest.fixture
                def my_class_pytest_fixture(self):
                    pass

                my_class_lambda_fixture = lambda_fixture(lambda: 123)
                my_class_static_fixture = static_fixture('abc')

                @pytest.fixture
                def caret(self, <caret>):
                    pass
        """.trimIndent()

        doTest(
            testFile,
            "my_class_pytest_fixture",
            "my_class_lambda_fixture",
            "my_class_static_fixture"
        )
    }

    private fun doTest(testFile: String, vararg expected: String) {
        myFixture.configureByText("test.py", testFile)
        myFixture.complete(CompletionType.BASIC, 1)

        val IGNORED = setOf("None", "False", "True")
        val actual = myFixture.lookupElementStrings?.filter { !IGNORED.contains(it) }?.sorted()
        assertEquals(expected.toList().sorted(), actual)
    }
}
