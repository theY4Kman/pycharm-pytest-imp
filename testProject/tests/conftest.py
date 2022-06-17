import pytest
from pytest_lambda import lambda_fixture

pytest_plugins = ['tests.plugins.fixtures']


@pytest.fixture
def py_conftest():
    return 800


lamb_conftest = lambda_fixture(lambda: 900.0)
