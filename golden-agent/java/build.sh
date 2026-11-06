#!/usr/bin/env sh
# Builds the library, runs the self-checks and the demo, and packages dist/golden-agent-<version>.jar.
# Requires a JDK 21 or newer on the PATH. No other tools or downloads are needed.
set -eu
cd "$(dirname "$0")"
VERSION="${1:-1.0.0}"

rm -rf out dist
mkdir -p out/main out/test out/demo dist

compile() { # $1 = source dir, $2 = output dir, $3 = classpath (optional)
  find "$1" -name '*.java' > out/sources.txt
  if [ -n "${3:-}" ]; then
    javac --release 21 -Xlint:all -d "$2" -cp "$3" @out/sources.txt
  else
    javac --release 21 -Xlint:all -d "$2" @out/sources.txt
  fi
}

echo "Compiling library..."
compile src/main/java out/main
echo "Compiling self-checks and demo..."
compile src/test/java out/test out/main
compile src/demo/java out/demo out/main

echo "Running self-checks..."
java -cp out/main:out/test golden.agent.check.SelfCheck
echo
java -cp out/main:out/demo golden.agent.demo.Demo

echo
echo "Packaging dist/golden-agent-$VERSION.jar..."
printf 'Automatic-Module-Name: golden.agent\nImplementation-Title: golden-agent\nImplementation-Version: %s\n' "$VERSION" > out/manifest.txt
jar --create --file "dist/golden-agent-$VERSION.jar" --manifest out/manifest.txt -C out/main .
jar --create --file "dist/golden-agent-$VERSION-sources.jar" -C src/main/java .
echo "Done."
