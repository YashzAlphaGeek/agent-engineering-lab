#!/usr/bin/env sh
# Runs the tests and the demo, then builds dist/golden_agent-<version>-py3-none-any.whl when possible.
# Requires Python 3.10 or newer. The library itself has no dependencies.
set -eu
cd "$(dirname "$0")"

echo "Running tests..."
PYTHONPATH=src python3 -W error -m unittest discover -s tests

if [ "${1:-}" != "--skip-demo" ]; then
  echo
  python3 examples/demo.py
fi

echo
echo "Building the wheel (needs setuptools, no network when it is already installed)..."
rm -rf dist build
if python3 -m pip wheel . --no-deps --no-build-isolation --disable-pip-version-check -w dist; then
  echo "Wheel written to dist/"
else
  echo "Wheel build skipped: the build backend (setuptools) is not available offline. Tests and demo passed."
fi
rm -rf build src/golden_agent.egg-info
