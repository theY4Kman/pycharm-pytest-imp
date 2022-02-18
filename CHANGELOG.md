The Changelog
=============

History of changes in the pytest imp plugin for PyCharm.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project DOES NOT adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


[To Be Released]
----------------

**Fixed:**
 - Resolve `*args` types being reported incorrectly, for any function anywhere (whoops. see [GH#11](https://github.com/theY4Kman/pycharm-pytest-imp/issues/10 — thanks, [andrianovs](https://github.com/andrianovs) and [lancelote](https://github.com/lancelote))

_To Be Released..._


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
