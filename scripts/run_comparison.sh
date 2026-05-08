#!/usr/bin/env bash
# run_comparison.sh — Run sequential baseline THEN distributed, print speedup summary
# Usage: ./run_comparison.sh [N] [K] [F] [maxIter]

set -e
N=${1:-90000}
K=${2:-5}
F=${3:-16}
ITER=${4:-30}

LOG_DIR="logs"
mkdir -p "$LOG_DIR"

echo "============================================================"
echo "  K-Means Speedup Comparison"
echo "  N=$N  K=$K  F=$F  maxIter=$ITER"
echo "============================================================"

# ── Run sequential ──────────────────────────────────────────────────────────
echo ""
echo "--- Running Sequential Baseline ---"
java -cp out kmeans.SequentialKMeans "$N" "$K" "$F" "$ITER" \
    | tee "$LOG_DIR/seq_run.log"

SEQ_MS=$(grep "Wall-clock time" "$LOG_DIR/seq_run.log" | ggrep -oP '\d+(?= ms)')
SEQ_WCSS=$(grep "WCSS" "$LOG_DIR/seq_run.log" | ggrep -oP '[\d.]+$')

# ── Run distributed ──────────────────────────────────────────────────────────
echo ""
echo "--- Running Distributed (3 workers) ---"
PORT=9001
fuser -k ${PORT}/tcp 2>/dev/null || true
sleep 0.3

java -cp out kmeans.Master "$N" "$K" "$F" "$ITER" \
    > "$LOG_DIR/dist_master.log" 2>&1 &
MASTER_PID=$!

sleep 0.5
java -cp out kmeans.Worker localhost "$PORT" > "$LOG_DIR/dist_w1.log" 2>&1 &
java -cp out kmeans.Worker localhost "$PORT" > "$LOG_DIR/dist_w2.log" 2>&1 &
java -cp out kmeans.Worker localhost "$PORT" > "$LOG_DIR/dist_w3.log" 2>&1 &

wait $MASTER_PID

cat "$LOG_DIR/dist_master.log"

DIST_MS=$(grep "Wall-clock time" "$LOG_DIR/dist_master.log" | ggrep -oP '\d+(?= ms)')
DIST_WCSS=$(grep "WCSS" "$LOG_DIR/dist_master.log" | ggrep -oP '[\d.]+$')

# ── Print summary ───────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "  SPEEDUP SUMMARY"
echo "============================================================"
echo "  Sequential  time  : ${SEQ_MS} ms"
echo "  Distributed time  : ${DIST_MS} ms"
if [ -n "$SEQ_MS" ] && [ -n "$DIST_MS" ] && [ "$DIST_MS" -gt 0 ]; then
    SPEEDUP=$(echo "scale=2; $SEQ_MS / $DIST_MS" | bc)
    echo "  Speedup           : ${SPEEDUP}x"
fi
echo "  Sequential  WCSS  : ${SEQ_WCSS}"
echo "  Distributed WCSS  : ${DIST_WCSS}"
echo "  (WCSS should match — same algorithm, same seeds)"
echo "============================================================"
