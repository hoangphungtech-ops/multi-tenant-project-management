#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf build-tv3
mkdir -p build-tv3
javac -encoding UTF-8 -d build-tv3 \
  src/main/java/com/cloudsaas/tv3/ExportWorker.java \
  src/main/java/com/cloudsaas/tv3/CsvBuilder.java \
  src/test/java/com/cloudsaas/tv3/ExportWorkerSelfTest.java
java -cp build-tv3 com.cloudsaas.tv3.ExportWorkerSelfTest
