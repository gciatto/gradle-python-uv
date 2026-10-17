from demo_project.greeting import greet


def test_greet() -> None:
    assert greet("Gradle") == "Hello, Gradle!"
