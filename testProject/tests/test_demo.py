import pytest
from pytest_lambda import static_fixture, lambda_fixture

my_toplevel_static_fixture = static_fixture(123)


@pytest.fixture
def caret(my_toplevel_static_fixture):
    return 20


@pytest.mark.parametrize('v', [
    pytest.param(1),
])
def test_stuff(v, monkeypatch):
    a = v
    monkeypatch.setattr()


a = lambda_fixture(lambda : 123)

class TestStuff:
    caret = lambda_fixture('caret')
    
    def test_it(self, caret, my_toplevel_static_fixture, ):
        assert caret
        a = caret
        b = my_toplevel_static_fixture

    def it_does_things(self, caret):
        pass

    def it_does_stuff(self, caret):
        pass

    def how_do_one_do_dis(self, caret):
        pass










class DescribeMe:
    def its_fantastic(self, la):
        assert True


class Describeme:
    def its_fantastic(self):
        assert True














l = [1, 2.0, 'c']

for o in l:
    print(o)


from pytest_lambda import lambda_fixture

renaming = lambda_fixture(lambda: None)


class TestStuff:
    renaming = lambda_fixture('renaming')

    def test_it(self, renaming):
        assert renaming

    def it_tests(self):
        pass


alpha = 24

class Things:
    alpha = 12
