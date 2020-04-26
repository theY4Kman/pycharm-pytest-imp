import pytest
from pytest_lambda import lambda_fixture, static_fixture, error_fixture, disabled_fixture, not_implemented_fixture

lamb = lambda_fixture(lambda: 3.0)
ref = lambda_fixture('lamb', 'stat')
butt = lambda_fixture('lamb')

stat = static_fixture(24)

err = error_fixture(Exception)

dis = disabled_fixture()

nim = not_implemented_fixture()


@pytest.fixture
def real():
    return 36


def test_stuff(ref, butt):
    pass


class TestThings:
    barf = lambda_fixture(lambda stat, err: stat)

    @pytest.fixture
    def stuff(self):
        pass

    def test_shit(self):
        pass

    class TestSub:
        def test_biz(self):
            pass
