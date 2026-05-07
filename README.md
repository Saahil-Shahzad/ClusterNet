# Distributed K-Means Clustering

**High-performance distributed K-Means algorithm implementation using Java sockets and multi-threaded coordination.**

CS-347 Parallel & Distributed Computing Semester Project  
SEECS, NUST

## Group Members

- Saahil Shahzad (469370)
- Muhammad Ahmad (461348)
- Abdur Rafey (481971)

Instructor: Dr. Fahad Ahmed Satti

---

# Requirements

- Java JDK 11 or higher

Check installation:

```bash
java -version
javac -version
```

---

# Project Structure

```text
distributed-kmeans/
├── src/kmeans/
├── scripts/
├── logs/
├── results/
├── compile.sh / compile.bat
└── README.md
```

---

# Compilation

## Linux / macOS

```bash
chmod +x compile.sh scripts/*.sh
./compile.sh
```

## Windows

```cmd
compile.bat
```

---

# Run Sequential Baseline

## Linux / macOS

```bash
./scripts/run_sequential.sh
```

## Windows

```cmd
scripts\run_sequential.bat
```

## Custom Parameters

```bash
./scripts/run_sequential.sh [N] [K] [F] [maxIter]
```

Example:

```bash
./scripts/run_sequential.sh 1000000 20 64 50
```

---

# Run Distributed System

## Linux / macOS

```bash
./scripts/run_distributed.sh
```

## Windows

```cmd
scripts\run_distributed.bat
```

## Custom Parameters

```bash
./scripts/run_distributed.sh [N] [K] [F] [maxIter]
```

Example:

```bash
./scripts/run_distributed.sh 1000000 20 64 50
```

The distributed implementation launches:
- 1 master process
- 3 worker JVM processes
- ForkJoinPool threads inside each worker

---

# Configure Worker Count

Worker count can be changed inside:

```text
scripts/run_distributed.sh
scripts/run_distributed.bat
```

or directly in:

```text
Master.java
```

---

# Run Speedup Experiments

```bash
./scripts/run_comparison.sh
```

Example:

```bash
./scripts/run_comparison.sh 1000000 20 64 50
```

The script reports:
- sequential runtime,
- distributed runtime,
- speedup,
- and WCSS comparison.

---

# Experimental Configuration

| Parameter | Value |
|---|---|
| Dataset Size | 1,000,000 |
| Clusters | 20 |
| Dimensions | 64 |
| Workers | 3 |
| Threads per Worker | 8 |

---

# Output Files

Logs:
```text
logs/
```

CSV results and graphs:
```text
results/
```

---

# Troubleshooting

| Problem | Solution |
|---|---|
| Address already in use | Kill existing process using port 9001 |
| Workers cannot connect | Start master before workers |
| ClassNotFoundException | Run compile script first |
| Low speedup | Use larger dataset sizes |
