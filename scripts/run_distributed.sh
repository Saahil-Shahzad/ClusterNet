#!/usr/bin/env bash
# run_distributed.sh — Launch 3 workers + master for distributed K-Means
# Usage: ./run_distributed.sh [N] [K] [F] [maxIter]
# Defaults: N=90000  K=5  F=16  maxIter=30
#
# What this script does:
#   1. Frees port 9001 if occupied
#   2. Starts the Master (waits on port 9001)
#   3. Starts 3 Workers (each connects to master)
#   4. Waits for master to finish; logs each process to separate files

set -e
N=${1:-90000}
K=${2:-5}
F=${3:-16}
ITER=${4:-30}

PORT=9001
LOG_DIR="logs"
mkdir -p "$LOG_DIR"

echo "=== Distributed K-Means: N=$N  K=$K  F=$F  maxIter=$ITER ==="
echo "Logs will be written to $LOG_DIR/"

# Free port if occupied
fuser -k ${PORT}/tcp 2>/dev/null || true
sleep 0.3

# Start master in background
java -cp out kmeans.Master "$N" "$K" "$F" "$ITER" \
    > "$LOG_DIR/master.log" 2>&1 &
MASTER_PID=$!
echo "Master started (PID=$MASTER_PID)"

sleep 0.5   # give master time to open socket

# Start 3 workers in background
java -cp out kmeans.Worker localhost "$PORT" \
    > "$LOG_DIR/worker1.log" 2>&1 &
echo "Worker 1 started (PID=$!)"

java -cp out kmeans.Worker localhost "$PORT" \
    > "$LOG_DIR/worker2.log" 2>&1 &
echo "Worker 2 started (PID=$!)"

java -cp out kmeans.Worker localhost "$PORT" \
    > "$LOG_DIR/worker3.log" 2>&1 &
echo "Worker 3 started (PID=$!)"

# Wait for master to finish
echo "Waiting for master to complete..."
wait $MASTER_PID
EXIT_CODE=$?

echo ""
echo "=== Master output ==="
cat "$LOG_DIR/master.log"

echo ""
echo "=== Worker 1 output ==="
cat "$LOG_DIR/worker1.log"

echo ""
echo "=== Worker 2 output ==="
cat "$LOG_DIR/worker2.log"

echo ""
echo "=== Worker 3 output ==="
cat "$LOG_DIR/worker3.log"

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "[SUCCESS] Distributed run completed. Check logs/ for per-process output."
else
    echo "[FAILED] Master exited with code $EXIT_CODE"
fi
exit $EXIT_CODE
