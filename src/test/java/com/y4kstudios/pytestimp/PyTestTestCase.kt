package com.y4kstudios.pytestimp

import com.jetbrains.python.PyNames
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.testing.PyTestFrameworkService
import com.jetbrains.python.testing.TestRunnerService

open class PyTestTestCase : PyTestCase() {
    override fun setUp() {
        super.setUp()

        TestRunnerService.getInstance(myFixture.module).projectConfiguration =
            PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)

        myFixture.copyFileToProject("pytest.py")
        myFixture.copyFileToProject("pytest_lambda.py")
    }

    override fun getTestDataPath(): String {
        return TestUtils.computeBasePath
    }
}
