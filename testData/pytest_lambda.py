# stubs for the pytest_lambda module


from typing import Union, Callable, Iterable, Any


def lambda_fixture(fixture_name_or_lambda: Union[str, Callable]=None,
                   *other_fixture_names: Iterable[str],
                   bind=False,
                   scope="function", params=None, autouse=False, ids=None, name=None):
    pass


def static_fixture(value: Any, **fixture_kwargs):
    pass


def error_fixture(error_fn: Callable, **fixture_kwargs):
    pass


def disabled_fixture(**fixture_kwargs):
    pass


def not_implemented_fixture(**fixture_kwargs):
    pass
