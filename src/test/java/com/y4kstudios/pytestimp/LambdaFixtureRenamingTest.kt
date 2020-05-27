package com.y4kstudios.pytestimp

import org.intellij.lang.annotations.Language

class LambdaFixtureRenamingTest : PyTestTestCase() {
    fun testRenameToplevelFixtureReferencesFromOriginalPytestFixture() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            @pytest.fixture
            def renaming<caret>():
                pass

            @pytest.fixture
            def my_toplevel_pytest_fixture(renaming):
                return renaming

            my_toplevel_lambda_fixture = lambda_fixture(lambda renaming: renaming)

            my_toplevel_lambda_ref_fixture = lambda_fixture('renaming')

            def test_it(renaming):
                assert renaming
        """.trimIndent()

        @Language("Python")
        val expected = """
            import pytest
            from pytest_lambda import lambda_fixture

            @pytest.fixture
            def renamed():
                pass

            @pytest.fixture
            def my_toplevel_pytest_fixture(renamed):
                return renamed

            my_toplevel_lambda_fixture = lambda_fixture(lambda renamed: renamed)

            my_toplevel_lambda_ref_fixture = lambda_fixture('renamed')

            def test_it(renamed):
                assert renamed
        """.trimIndent()

        doTest(testFile, expected)
    }

    fun testRenameToplevelFixtureReferencesFromOriginalLambdaFixture() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            renaming<caret> = lambda_fixture(lambda: None)

            @pytest.fixture
            def my_toplevel_pytest_fixture(renaming):
                return renaming

            my_toplevel_lambda_fixture = lambda_fixture(lambda renaming: renaming)

            my_toplevel_lambda_ref_fixture = lambda_fixture('renaming')

            def test_it(renaming):
                assert renaming
        """.trimIndent()

        @Language("Python")
        val expected = """
            import pytest
            from pytest_lambda import lambda_fixture

            renamed = lambda_fixture(lambda: None)

            @pytest.fixture
            def my_toplevel_pytest_fixture(renamed):
                return renamed

            my_toplevel_lambda_fixture = lambda_fixture(lambda renamed: renamed)

            my_toplevel_lambda_ref_fixture = lambda_fixture('renamed')

            def test_it(renamed):
                assert renamed
        """.trimIndent()

        doTest(testFile, expected)
    }

    fun testRenameLambdaImplicitRefFixtureFromOriginalLambdaFixture() {
        @Language("Python")
        val testFile = """
            from pytest_lambda import lambda_fixture

            renaming<caret> = lambda_fixture(lambda: None)

            class TestStuff:
                renaming = lambda_fixture()

                def test_it(self, renaming):
                    assert renaming
        """.trimIndent()

        @Language("Python")
        val expected = """
            from pytest_lambda import lambda_fixture

            renamed = lambda_fixture(lambda: None)

            class TestStuff:
                renamed = lambda_fixture()

                def test_it(self, renamed):
                    assert renamed
        """.trimIndent()

        doTest(testFile, expected)
    }

    fun testRenameLambdaImplicitRefFixtureFromOriginalPytestFixture() {
        @Language("Python")
        val testFile = """
            import pytest
            from pytest_lambda import lambda_fixture

            @pytest.fixture
            def renaming<caret>():
                pass

            class TestStuff:
                renaming = lambda_fixture()

                def test_it(self, renaming):
                    assert renaming
        """.trimIndent()

        @Language("Python")
        val expected = """
            import pytest
            from pytest_lambda import lambda_fixture

            @pytest.fixture
            def renamed():
                pass

            class TestStuff:
                renamed = lambda_fixture()

                def test_it(self, renamed):
                    assert renamed
        """.trimIndent()

        doTest(testFile, expected)
    }

    fun testRenameSameNamedLambdaRefFixtureFromOriginalLambdaFixture() {
        @Language("Python")
        val testFile = """
            from pytest_lambda import lambda_fixture

            renaming<caret> = lambda_fixture(lambda: None)

            class TestStuff:
                renaming = lambda_fixture('renaming')

                def test_it(self, renaming):
                    assert renaming
        """.trimIndent()

        @Language("Python")
        val expected = """
            from pytest_lambda import lambda_fixture

            renamed = lambda_fixture(lambda: None)

            class TestStuff:
                renaming = lambda_fixture('renamed')

                def test_it(self, renaming):
                    assert renaming
        """.trimIndent()

        doTest(testFile, expected)
    }

    fun doTest(testFile: String, expected: String) {
        doTest(testFile, "renamed", expected)
    }

    fun doTest(testFile: String, renameTo: String, expected: String) {
        myFixture.configureByText("test.py", testFile)
        myFixture.renameElementAtCaret(renameTo)

        myFixture.checkResult(expected)
    }

}
