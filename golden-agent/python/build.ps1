# Runs the tests and the demo, then builds dist/golden_agent-<version>-py3-none-any.whl when possible.
# Requires Python 3.10 or newer. The library itself has no dependencies.
param([switch]$SkipDemo)

# Native tools such as unittest write progress to stderr, so errors are detected through exit codes instead.
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

Write-Host "Running tests..."
$env:PYTHONPATH = "src"
python -W error -m unittest discover -s tests
if ($LASTEXITCODE -ne 0) { throw "Tests failed" }

if (-not $SkipDemo) {
    Write-Host ""
    python examples/demo.py
    if ($LASTEXITCODE -ne 0) { throw "Demo failed" }
}

Write-Host ""
Write-Host "Building the wheel (needs setuptools, no network when it is already installed)..."
Remove-Item -Recurse -Force dist, build -ErrorAction SilentlyContinue
python -m pip wheel . --no-deps --no-build-isolation --disable-pip-version-check -w dist
if ($LASTEXITCODE -eq 0) {
    Write-Host "Wheel written to dist\"
} else {
    Write-Host "Wheel build skipped: the build backend (setuptools) is not available offline. Tests and demo passed."
}
Remove-Item -Recurse -Force build, src\golden_agent.egg-info -ErrorAction SilentlyContinue
