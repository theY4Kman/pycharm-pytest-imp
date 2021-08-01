# pytest imp PyCharm plugin

[![](https://img.shields.io/jetbrains/plugin/d/14202-pytest-imp.svg)](https://plugins.jetbrains.com/plugin/14202-pytest-imp)

**pytest imp**[rovements] is a PyCharm plugin offering a bit more editor integration with pytest than PyCharm currently provides.

 - Support for custom `python_classes` and `python_functions` in either `pytest.ini` or `pyproject.toml` (this means little green arrows next to your tests)
 - Support for completion/typing of fixtures from nested classes
 - Support for lambda fixtures from [pytest-lambda](https://github.com/theY4Kman/pytest-lambda)


# Want more?

pytest imp was originally written to scratch a personal itch: to have support for lambda fixtures, the main gimmick of [pytest-lambda](https://github.com/theY4Kman/pytest-lambda) (I'm the author), which I use every day.

Eventually, a coworker wanted run line markers (i.e. gutter icons) next to our tests, which were inside test classes that didn't start with `Test`, with names that didn't start with `test_`. So I added support for custom `python_classes` and `python_functions` configuration in `pytest.ini`. Later, by request, support for `pyproject.toml` configs was added.

All of this is to say: we all know how it feels to love PyCharm and use it every day with pytest, and yet it doesn't integrate with _&lt;insert feature here&gt;_, which is a huge part of my workflow. Well, I, too, know that feel, and I've come to know a thing or two about PyCharm plugin development, so **SEND ME YOUR FEATURE REQUESTS**!

... and if you want to write a pull request to implement something yourself, I just want to apologize for the messy state of the codebase. It's been more of a plugin development learning testbed than a maintainable product. And Kotlin, while an absolute pleasure to develop with, does offer lots of rope to hang yerself with.
