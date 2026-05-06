#!/usr/bin/env bash
# compile.sh — Compile all Java sources into out/
set -e
mkdir -p out
echo "[compile] Compiling all sources..."
javac -d out src/kmeans/*.java
echo "[compile] Done. Classes written to out/"
