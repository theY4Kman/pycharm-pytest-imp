import asyncio

import pytest
from pytest_lambda import lambda_fixture, static_fixture, error_fixture, disabled_fixture, not_implemented_fixture

lamb = lambda_fixture(lambda: 3.0)
ref = lambda_fixture('lamb', 'stat')
butt = lambda_fixture('lamb')

faux = lambda_fixture(lambda real: real)
refaux = lambda_fixture('real')

asink = lambda_fixture(lambda: asyncio.sleep(0, 'test'), async_=True)

# stat = static_fixture(24)

err = error_fixture(Exception)

dis = disabled_fixture()

nim = not_implemented_fixture()


@pytest.fixture
def real():
    return 36


@pytest.fixture
async def a_real_sink():
    return 48


ref_sinks = lambda_fixture(lambda asink, a_real_sink: (asink, a_real_sink))


def test_stuff(ref, butt, faux, real, asink, a_real_sink, ):
    a = faux
    b = butt
    c = real
    d = asink
    e = a_real_sink


stat = static_fixture(86)

that = lambda_fixture(lambda stat: stat)


class TestThings:
    barf = lambda_fixture(lambda stat, err: stat)
    butt = lambda_fixture()
    butt = lambda_fixture()

    @pytest.fixture
    def stuff(self):
        return 'butts'

    def test_shit(self, real, refaux, stuff, asink, a_real_sink):
        pass

    class TestSub:
        nut = lambda_fixture(lambda barf: barf)
        fut = lambda_fixture('stuff', 'barf')

        def test_biz(self, fut, nut, butt, stuff):
            pass
