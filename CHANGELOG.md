The Changelog
=============

History of changes in the pytest imp plugin for PyCharm.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project DOES NOT adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Stable versions use X.Y format.
EAP versions use X.Y.Z format.


[To Be Released]
----------------

_Available since 0.3.1 EAP:_

**Changed:**
 - Support for PyCharm 2020.3 EAP (203.4449.8)

_To Be Released..._


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
