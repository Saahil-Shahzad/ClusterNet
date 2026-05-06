@echo off
REM compile.bat — Compile all Java sources into out\
if not exist out mkdir out
echo [compile] Compiling all sources...
javac -d out src\kmeans\*.java
if %ERRORLEVEL% neq 0 (
    echo [compile] ERROR: Compilation failed
    exit /b 1
)
echo [compile] Done. Classes written to out\
