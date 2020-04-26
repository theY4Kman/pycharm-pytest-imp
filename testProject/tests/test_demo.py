import pytest
from pytest_lambda import lambda_fixture, static_fixture


@pytest.fixture
def real():
    return 1


lamb = lambda_fixture(lambda: 2)
stat = static_fixture(3)


def test_stuff(real, lamb, stat):
    pass