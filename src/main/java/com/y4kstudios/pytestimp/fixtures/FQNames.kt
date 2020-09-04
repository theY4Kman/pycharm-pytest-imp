package com.y4kstudios.pytestimp.fixtures

import com.jetbrains.python.nameResolver.FQNamesProvider

open class BaseFQNames constructor(private val myIsClass: Boolean, vararg names: String) : FQNamesProvider {
    private val myNames: Array<out String> = names

    override fun getNames(): Array<out String> {
        return myNames.clone()
    }

    override fun isClass(): Boolean {
        return myIsClass
    }
}


class LambdaFixtureFQNames constructor(vararg names: String) : BaseFQNames(false, *names) {
    companion object {
        val LAMBDA_FIXTURE = LambdaFixtureFQNames(
            "tests.util.fixtures.lambda_fixture",
            "pytest_lambda.fixtures.lambda_fixture")

        val STATIC_FIXTURE = LambdaFixtureFQNames(
            "tests.util.fixtures.static_fixture",
            "pytest_lambda.fixtures.static_fixture")

        val ERROR_FIXTURE = LambdaFixtureFQNames(
            "tests.util.fixtures.error_fixture",
            "pytest_lambda.fixtures.error_fixture")

        val DISABLED_FIXTURE = LambdaFixtureFQNames(
            "tests.util.fixtures.disabled_fixture",
            "pytest_lambda.fixtures.disabled_fixture")

        val NOT_IMPLEMENTED_FIXTURE = LambdaFixtureFQNames(
            "tests.util.fixtures.not_implemented_fixture",
            "pytest_lambda.fixtures.not_implemented_fixture")

        val PRECONDITION_FIXTURE = LambdaFixtureFQNames(
            "tests.util.fixtures.precondition_fixture",
            "pytest_common_subject.precondition_fixture")
    }
}


class PyTestFQNames constructor(myIsClass: Boolean, vararg names: String) : BaseFQNames(myIsClass, *names) {
    companion object {
        val PYTEST_PARAM = BaseFQNames(false,
            "pytest.param")
    }
}
