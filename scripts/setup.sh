#!/usr/bin/env bash
# One-time setup: download Gradle 8.9 into .tools/ and warm the dependency cache.
set -e
cd "$(dirname "$0")/.."

if [ ! -x ".tools/gradle-8.9/bin/gradle" ]; then
  echo "== Downloading Gradle 8.9 =="
  mkdir -p .tools
  curl -L --fail --retry 3 -o .tools/gradle-8.9-bin.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip
  echo "== Unzipping =="
  unzip -q -o .tools/gradle-8.9-bin.zip -d .tools
  rm .tools/gradle-8.9-bin.zip
fi

echo "== Warming build cache (compile only) =="
.tools/gradle-8.9/bin/gradle :app:compileDebugKotlin --no-daemon
echo "== Setup complete =="
