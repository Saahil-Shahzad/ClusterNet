@echo off
REM run_distributed.bat — Launch 3 workers + master for distributed K-Means
REM Usage: run_distributed.bat [N] [K] [F] [maxIter]
REM
REM INSTRUCTIONS:
REM   This script opens 3 separate CMD windows for workers, then runs the
REM   master in the current window. Close the worker windows when done.

set N=%1
set K=%2
set F=%3
set ITER=%4
if "%N%"=="" set N=90000
if "%K%"=="" set K=5
if "%F%"=="" set F=16
if "%ITER%"=="" set ITER=30

if not exist logs mkdir logs

echo === Distributed K-Means: N=%N%  K=%K%  F=%F%  maxIter=%ITER% ===

REM Start workers in separate windows
start "Worker 1" cmd /k "java -cp out kmeans.Worker localhost 9001 > logs\worker1.log 2>&1 && echo Worker 1 done"
start "Worker 2" cmd /k "java -cp out kmeans.Worker localhost 9001 > logs\worker2.log 2>&1 && echo Worker 2 done"
start "Worker 3" cmd /k "java -cp out kmeans.Worker localhost 9001 > logs\worker3.log 2>&1 && echo Worker 3 done"

REM Give workers a moment to start
timeout /t 2 /nobreak > nul

REM Run master in current window
java -cp out kmeans.Master %N% %K% %F% %ITER%

echo.
echo Distributed run complete. Check logs\ folder for worker output.
