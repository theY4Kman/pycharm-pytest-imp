The Changelog
=============

History of changes in the pytest imp plugin for PyCharm.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project DOES NOT adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


[To Be Released]
----------------

_To Be Released..._


1.3.2, 2025-09-16
-----------------

**Changed:**
 - Add support for 2025.2


1.3.2, 2025-04-16
-----------------

**Changed:**
 - Add support for 2025.1


1.3.1, 2024-12-02
-----------------

**Features:**
 - Add support for explicit typing annotations on lambda fixtures (e.g. `pear: LambdaFixture[str] = static_fixture(123)`)

**Changed:**
 - Add support for 2024.3

**Changed:**
 - Raised minimum version to 2024.3 to avoid compat issues


~~1.3.0, 2024-11-30~~
-----------------

_NOTE: this version is not published to marketplace, due to compat issues with 2024.2

~~**Features:**~~
 - Add support for explicit typing annotations on lambda fixtures (e.g. `pear: LambdaFixture[str] = static_fixture(123)`)~~

~~**Changed:**~~
 - ~~Add support for 2024.3~~


1.2.1, 2024-05-28
-----------------

**Fixed:**
 - Resolve improper typing for async yield fixtures


1.2.0, 2024-05-28
-----------------

**Features:**
 - Add proper typing for implicit reference lambda fixtures (e.g. `apple = lambda_fixture()`)

**Changed:**
 - Add support for 2024.2


1.1.3, 2023-10-07
-----------------

**Fixed:**
 - Resolve incorrect renaming behaviour

**Changed:**
 - Add support for 2024.1


1.1.2, 2023-10-07
-----------------

**Changed:**
 - Add support for 2023.3


1.1.1, 2023-05-27
-----------------

**Changed:**
 - Add support for 2023.2


1.1.0, 2023-02-17
-----------------

**Features:**
 - Allow fixtures to be defined with `@pytest_asyncio.fixture`
 - Hide "shadows name from outer scope" message for fixture functions
 - Prevent fixture request parameters being marked unused

**Changed:**
 - Add support for 2023.1

**Fixed:**
 - Ensure current fixture is not suggested in fixture completions


1.0.3, 2022-11-08
-----------------

**Fixed:**
 - Resolved lambda_fixture refs to yield fixtures providing incorrect type


1.0.2, 2022-11-03
-----------------

**Fixed:**
 - Resolved indirectly-defined lambda_fixture fixture definitions (e.g. `lambda_fixture(my_callable)`) always being typed as `tuple`
 - Resolved explicit typing annotations on requested fixtures being ignored again, in 2022.3


1.0.1, 2022-11-02
-----------------

**Fixed:**
 - Resolve incorrect type inference for non-async yield fixtures (see [GH#16](https://github.com/theY4Kman/pycharm-pytest-imp/issues/16))


1.0.0, 2022-09-23
-----------------

**BREAKING:**
 - Added support for 2022.3, requiring a source/target Java compatibility of 17. Minimum supported PyCharm version is now 2022.3.


0.8.0, 2022-07-15
-----------------

**Features:**
 - Support for lambda_fixture destructuring

**Fixed:**
 - Resolved null pointer exception related to pytest plugin/conftest fixtures search, which could impact code sense for completely unrelated files.


0.7.1, 2022-06-28
-----------------

**Fixed:**
 - Resolved artifact build including test sources, some of which caused plugin incompatibilities when scanned.


0.7.0, 2022-06-17
-----------------

**Features:**
 - Add completion/references for external lambda fixtures (from appropriate `conftest.py` files, and from plugins listed in the root conftest's `pytest_plugins` and from `-p <plugin>` arguments in the pytest config's `addopts`)


0.6.0, 2022-06-15
-----------------

**Features:**
 - Support for async fixtures (both pytest and pytest-lambda fixtures)

**Added:**
 - Expose pretty types for lambda_fixture lambda expression callables


0.5.6, 2022-05-29
-----------------

**Changed:**
 - Support for PyCharm 2022.2 EAP (tested against 222.2270.35)


0.5.5, 2022-02-17
-----------------

**Fixed:**
 - Resolve `*args` types being reported incorrectly, for any function anywhere (whoops. see [GH#11](https://github.com/theY4Kman/pycharm-pytest-imp/issues/11 — thanks, [andrianovs](https://github.com/andrianovs) and [lancelote](https://github.com/lancelote))


0.5.4, 2022-02-14
-----------------

**Changed:**
 - Support for PyCharm 2022.1 EAP (tested against 221.3427.103)
 - Minimum JVM version changed from 10 to 11


0.5.3, 2021-11-12
-----------------

**Fixed:**
 - Allow relative paths to `pytest.ini` or `pyproject.toml` to be used in settings (see [GH#10](https://github.com/theY4Kman/pycharm-pytest-imp/issues/10) — thanks, [kbakk](https://github.com/kbakk)!)


0.5.2, 2021-09-29
-----------------

**Changed:**
 - Support for PyCharm 2021.3 EAP (tested against 213.3714.452)

**Fixed:**
 - Resolve error with [java-configparser](https://github.com/ASzc/java-configparser) when either `python_functions` or `python_classes` were undefined when using `pytest.ini` for config.


0.5.1, 2021-07-31
-----------------

**Fixed:**
 - Resolve issue where multiline values caused error in INI config parsing  
   
   _(previously, [ini4j](http://ini4j.sourceforge.net/) was used to parse `pytest.ini`, but Python's [ConfigParser](https://docs.python.org/3/library/configparser.html), which pytest uses, is not a strict implementation of INI parsing; we have switched to using the third-party [java-configparser](https://github.com/ASzc/java-configparser) to resolve this.)_


0.5, 2021-07-12
---------------

**Changed:**
 - Support for PyCharm 2021.2 EAP (tested against 212.4321.41)

**Fixed:**
 - Allow array values for `python_classes` and `python_functions` when using pyproject.toml
 - Resolve incorrect typing when using `pytest.param(..., id='xyz')`


0.4.2, 2021-03-19
-----------------

**Fixed:**
 - Properly parse dashes used within character classes of `python_classes` patterns (see GH#9, thanks @WIRUT!)


0.4.1, 2021-02-27
-----------------

**Fixed:**
 - Use proper name for pytest config in `pyproject.toml` (previously, `tools.pytest.ini_options` was used, instead of `tool.pytest.ini_options`)


0.4, 2021-02-17
---------------

**Features:**
 - Added support for reading pytest config from pyproject.toml

**Changed:**
 - Support for PyCharm 2021.1 EAP (211.5538.22)


0.3.2, 2020-12-08
-----------------

**Fixed:**
 - Respect explicit type annotations on parameters, rather than inferred type of fixture


0.3.1, 2020-10-20
-----------------

**Changed:**
 - Support for PyCharm 2020.3 EAP (203.4449.8)


0.3, 2020-08-30
---------------

**Features:**
 - Add run line markers (i.e. "Run pytest for selected_test_method") for classes/methods based on patterns in `pytest.ini`
 - Configure path to `pytest.ini` in settings (Tools > Python Integrated Tools > py.test)
 - Support autocompletion / typing / renaming of `precondition_fixture` (from pytest-common-subject)


0.2, 2020-08-30
---------------

**Changed:**
 - Support for PyCharm 2020.2.1

**Features:**
 - Completion/typing support for pytest_lambda lambda/static fixtures
 - Completion/typing support for any pytest fixtures defined in enclosing classes
