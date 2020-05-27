from dataclasses import dataclass

import factory
from pytest_factoryboy import register


@dataclass
class SuperModel:
    id: int


@register
class SuperModelFactory(factory.Factory):
    class Meta:
        model = SuperModel

    id = factory.Faker('random_int')