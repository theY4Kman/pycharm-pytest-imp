"""pytest-asyncio implementation."""
from typing import (
    Any,
    AsyncIterator,
    Awaitable,
    Callable,
    Iterable,
    Literal,
    Optional,
    TypeVar,
    Union,
    overload,
)

_R = TypeVar("_R")

_ScopeName = Literal["session", "package", "module", "class", "function"]
_T = TypeVar("_T")

SimpleFixtureFunction = TypeVar(
    "SimpleFixtureFunction", bound=Callable[..., Awaitable[_R]]
)
FactoryFixtureFunction = TypeVar(
    "FactoryFixtureFunction", bound=Callable[..., AsyncIterator[_R]]
)
FixtureFunction = Union[SimpleFixtureFunction, FactoryFixtureFunction]
FixtureFunctionMarker = Callable[[FixtureFunction], FixtureFunction]

Config = Any  # pytest < 7.0


@overload
def fixture(
    fixture_function: FixtureFunction,
    *,
    scope: "Union[_ScopeName, Callable[[str, Config], _ScopeName]]" = ...,
    params: Optional[Iterable[object]] = ...,
    autouse: bool = ...,
    ids: Union[
        Iterable[Union[str, float, int, bool, None]],
        Callable[[Any], Optional[object]],
        None,
    ] = ...,
    name: Optional[str] = ...,
) -> FixtureFunction:
    ...


@overload
def fixture(
    fixture_function: None = ...,
    *,
    scope: "Union[_ScopeName, Callable[[str, Config], _ScopeName]]" = ...,
    params: Optional[Iterable[object]] = ...,
    autouse: bool = ...,
    ids: Union[
        Iterable[Union[str, float, int, bool, None]],
        Callable[[Any], Optional[object]],
        None,
    ] = ...,
    name: Optional[str] = None,
) -> FixtureFunctionMarker:
    ...


def fixture(
    fixture_function: Optional[FixtureFunction] = None, **kwargs: Any
) -> Union[FixtureFunction, FixtureFunctionMarker]:
    ...
