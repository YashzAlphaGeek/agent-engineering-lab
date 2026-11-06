# Checks formatting and lints, runs the tests and the demo, and packages the crate.
# Requires a Rust toolchain (1.85 or newer). No network is needed because the crate has no dependencies.
param(
    [switch]$SkipDemo
)

# cargo writes progress to stderr, so rely on exit codes instead of treating stderr as an error.
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

function Run($label, [scriptblock]$command) {
    Write-Host "== $label"
    & $command
    if ($LASTEXITCODE -ne 0) { throw "$label failed" }
}

function Has($subcommand) {
    & cargo $subcommand --version *> $null
    return ($LASTEXITCODE -eq 0)
}

if (Has "fmt") { Run "cargo fmt --check" { cargo fmt --check } } else { Write-Host "== rustfmt not installed, skipping" }
if (Has "clippy") { Run "cargo clippy" { cargo clippy --all-targets -- -D warnings } } else { Write-Host "== clippy not installed, skipping" }

Run "cargo test" { cargo test }

if (-not $SkipDemo) {
    Write-Host ""
    Run "cargo run --example demo" { cargo run --quiet --example demo }
}

Write-Host ""
Run "cargo package" { cargo package --allow-dirty --no-verify }
Write-Host "Done. The packaged crate is in target\package."
