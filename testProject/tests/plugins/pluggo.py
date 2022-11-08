import pytest
from pytest_lambda import lambda_fixture


@pytest.fixture
def py_pluggo():
    return 'eight hundred'


@pytest.fixture
def yield_pluggo():
    yield 'eight hundred'


@pytest.fixture
def explicit_yield_pluggo() -> int:
    yield 'eight hundred'


@pytest.fixture
async def async_yield_pluggo():
    yield 'eight hundred'


@pytest.fixture
async def explicit_async_yield_pluggo() -> int:
    yield 'eight hundred'


lamb_pluggo = lambda_fixture(lambda: b'nine hundred')
