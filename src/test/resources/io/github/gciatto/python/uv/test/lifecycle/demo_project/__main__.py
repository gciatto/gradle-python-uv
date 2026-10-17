"""Application entry point."""

import sys

from demo_project.greeting import greet


def main() -> None:
    """Prints a greeting for the first command line argument."""
    name = sys.argv[1] if len(sys.argv) > 1 else "World"
    print(greet(name))


if __name__ == "__main__":
    main()
