def f1(a):  # Noncompliant {{Remove the unused function parameter "a".}}
#      ^
    print("foo")


def g1(a):
    print(a)


def f2(a):
    return locals()


def g2(a):  # Noncompliant
    b = 1
    c = 2
    compute(b)

class MyInterface:

    def write_alignment(self, a):
        """This method should be replaced by any derived class to do something
        useful.
        """
        raise NotImplementedError("This object should be subclassed")

class Parent:
    def do_something(self, a, b):  # Noncompliant {{Remove the unused function parameter "a".}}
        #                  ^
        return compute(b)

    def do_something_else(self, a, b):
        return compute(a + b)


class Child(Parent):
    def do_something_else(self, a, b):
        return compute(a)


class AnotherChild(UnknownParent):
    def _private_method(self, a, b):  # OK
        return compute(b)

    def is_overriding_a_parent_method(self, a, b):
        return compute(b)

class ClassWithoutArgs:
    def do_something(self, a, b):  # Noncompliant
        return compute(b)

class ClassWithoutArgs2():
    def do_something(self, a, b):  # Noncompliant
        return compute(b)


@some_decorator
def decorated_function(x, y):
    print("foo")

class MyClass:
    @classmethod
    def foo(cls):
        ...
    def __exit__(self, exc_type, exc_val, exc_tb):
        return exc_val

    def __other(self, x): # Noncompliant
        return 42

def empty_method(x):
    ...

def mapper(x, y):
    return x + 1

for i in map(mapper, [1, 2], [3, 4]):
    print(i)

def some_fun(x, y): # Noncompliant
    return x + 1

some_fun(1, 1)

def ambiguous_f(x): # Noncompliant
    print("foo")

def ambiguous_f(x):
    return x + 1


def not_implemented(r1, r2, r3): # OK
    return NotImplemented

def returning_none(r1): # OK
    return

# coverage

def reassigned_f(x):
    print("hello")

global reassigned_f

def aliased_f(x): # FN
    print("hello")

g = aliased_f
g(42)


class MyFoo:
    def meth(self, x): # Noncompliant
        print("foo")
    def bar(self):
        self.meth(42)


import zope.interface

class IFoo(zope.interface.Interface):
    def bar(q, r=None):
        """bar foo bar"""


def test_using_fixture(my_fixture):
    assert do_something() == expected()


def lambda_handler(_event, _context):
    print("foo")

