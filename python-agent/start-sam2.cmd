@echo off
setlocal
cd /d "%~dp0"
set "SAM2_CHECKPOINT=%~dp0checkpoints\sam2.1_hiera_tiny.pt"
set "SAM2_CONFIG=configs/sam2.1/sam2.1_hiera_t.yaml"
set "SAM2_DEVICE=cpu"
"%~dp0..\.venv-sam2\Scripts\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 8000
