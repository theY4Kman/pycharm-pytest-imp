import pytest
from pytest_lambda import lambda_fixture


@pytest.fixture
def py_pluggo():
    return 'eight hundred'


lamb_pluggo = lambda_fixture(lambda: b'nine hundred')
