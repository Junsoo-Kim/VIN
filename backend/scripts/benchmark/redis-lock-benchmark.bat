@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0redis-lock-benchmark.ps1"
