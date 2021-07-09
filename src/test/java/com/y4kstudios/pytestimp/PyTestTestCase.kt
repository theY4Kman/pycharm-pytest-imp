package com.y4kstudios.pytestimp

import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.testing.TestRunnerService
import com.jetbrains.python.testing.getFactoryById
import org.junit.Ignore

@Ignore
open class PyTestTestCase : PyTestCase() {
    override fun setUp() {
        super.setUp()

        TestRunnerService.getInstance(myFixture.module).selectedFactory = getFactoryById("py.test")!!

        myFixture.copyFileToProject("pytest.py")
        myFixture.copyFileToProject("pytest_lambda.py")
    }

    override fun getTestDataPath(): String {
        return TestUtils.computeBasePath
    }
}
