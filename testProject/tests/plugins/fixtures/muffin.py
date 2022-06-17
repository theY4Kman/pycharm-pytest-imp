import pytest
from pytest_lambda import lambda_fixture

__all__ = ['py_muffin', 'lamb_muffin']


@pytest.fixture
def py_muffin():
    return 'muffin'


lamb_muffin = lambda_fixture(lambda: b'muffin')
