#!/usr/bin/env bash
# run_sequential.sh — Run the single-threaded sequential K-Means baseline
# Usage: ./run_sequential.sh [N] [K] [F] [maxIter]
# Defaults: N=90000  K=5  F=16  maxIter=30
set -e
N=${1:-90000}
K=${2:-5}
F=${3:-16}
ITER=${4:-30}

echo "=== Sequential Baseline: N=$N  K=$K  F=$F  maxIter=$ITER ==="
java -cp out kmeans.SequentialKMeans "$N" "$K" "$F" "$ITER"
