import pytest
from pytest_lambda import lambda_fixture

des_a, des_b, des_c = lambda_fixture(params=[
    pytest.param(1, 2.0, 3j),
    pytest.param('a', 'b', 'c'),
])


def test_params(scalar_params, tuple_params, des_a, des_b, des_c):
    pass
