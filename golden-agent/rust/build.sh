#!/usr/bin/env sh
# Checks formatting and lints, runs the tests and the demo, and packages the crate.
# Requires a Rust toolchain (1.85 or newer). No network is needed because the crate has no dependencies.
set -eu
cd "$(dirname "$0")"

has() { cargo "$1" --version >/dev/null 2>&1; }

if has fmt; then echo "== cargo fmt --check"; cargo fmt --check; else echo "== rustfmt not installed, skipping"; fi
if has clippy; then echo "== cargo clippy"; cargo clippy --all-targets -- -D warnings; else echo "== clippy not installed, skipping"; fi

echo "== cargo test"
cargo test

echo
echo "== cargo run --example demo"
cargo run --quiet --example demo

echo
echo "== cargo package"
cargo package --allow-dirty --no-verify
echo "Done. The packaged crate is in target/package."
