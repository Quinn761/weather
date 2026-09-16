FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

ARG INSTALL_SAM2=false
ARG DOWNLOAD_SAM2_CHECKPOINT=false
COPY requirements-sam2.txt .
RUN set -eu; \
    if [ "$INSTALL_SAM2" = "true" ]; then \
      apt-get update && apt-get install -y --no-install-recommends \
        git curl gcc g++ libgomp1 libglib2.0-0 && \
      pip install --no-cache-dir \
        torch==2.5.1 torchvision==0.20.1 \
        --index-url https://download.pytorch.org/whl/cpu && \
      pip install --no-cache-dir 'setuptools>=61' wheel && \
      SAM2_BUILD_CUDA=0 pip install --no-cache-dir --no-build-isolation -r requirements-sam2.txt; \
    fi; \
    if [ "$DOWNLOAD_SAM2_CHECKPOINT" = "true" ]; then \
      apt-get update && apt-get install -y --no-install-recommends curl && \
      mkdir -p /models && \
      curl -L --fail --retry 3 -o /models/sam2.1_hiera_tiny.pt \
        https://dl.fbaipublicfiles.com/segment_anything_2/092824/sam2.1_hiera_tiny.pt && \
      test -s /models/sam2.1_hiera_tiny.pt; \
    fi; \
    rm -rf /var/lib/apt/lists/*

COPY app ./app

ENV PYTHONUNBUFFERED=1 \
    SAM2_CHECKPOINT=/models/sam2.1_hiera_tiny.pt \
    SAM2_CONFIG=configs/sam2.1/sam2.1_hiera_t.yaml \
    SAM2_DEVICE=cpu

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
