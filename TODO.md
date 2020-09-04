# TODO

 - [X] Support `is_stuff_working` test names (they should have the little run icon in the gutter)
 - [X] Support other shit in pytest.ini, like python_classes, etc
 
 - [X] Disable "shadows name from outer scope" inspection for lambda fixtures declared in test methods
 - [X] Supply typing info for lambda fixtures
 - [X] Support typing for lambda fixtures that are references (i.e. `lambda_fixture('other_fixture_name')`)
 - [X] Support typing for lambda fixtures that are multiple references (i.e. `lambda_fixture('a', 'b')`)
 
 - [ ] Support lambda fixtures declared in other files
 - [X] Don't add code sense for lambda fixtures out of scope
 - [ ] Don't reference own declaration in `data = lambda_fixture(lambda data: ...)`
 - [X] Support for lambda fixtures declared in enclosing class
 - [X] Support for regular pytest fixtures declared in enclosing class
 
 - [X] Code completion for parameters in lambda fixture `lambda` expressions
 
 - [ ] Support user-configurable lambda_fixture methods
 - [ ] OR: support anything that returns a `LambdaFixture` non-strict subclass

 - [ ] Add support for pytest-factoryboy model/factory fixtures
