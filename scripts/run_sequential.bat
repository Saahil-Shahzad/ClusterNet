@echo off
REM run_sequential.bat — Run the single-threaded sequential K-Means baseline
REM Usage: run_sequential.bat [N] [K] [F] [maxIter]
REM Defaults: N=90000  K=5  F=16  maxIter=30

set N=%1
set K=%2
set F=%3
set ITER=%4
if "%N%"=="" set N=90000
if "%K%"=="" set K=5
if "%F%"=="" set F=16
if "%ITER%"=="" set ITER=30

echo === Sequential Baseline: N=%N%  K=%K%  F=%F%  maxIter=%ITER% ===
java -cp out kmeans.SequentialKMeans %N% %K% %F% %ITER%
