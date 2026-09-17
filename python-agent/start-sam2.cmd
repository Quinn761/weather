@echo off
setlocal
cd /d "%~dp0"
set "SAM2_CHECKPOINT=%~dp0checkpoints\sam2.1_hiera_tiny.pt"
set "SAM2_CONFIG=configs/sam2.1/sam2.1_hiera_t.yaml"
set "SAM2_DEVICE=cpu"
if "%SAM2_PORT%"=="" set "SAM2_PORT=8001"
"%~dp0..\.venv-sam2\Scripts\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port %SAM2_PORT%
