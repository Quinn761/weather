FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

ARG INSTALL_SAM2=false
COPY requirements-sam2.txt .
RUN if [ "$INSTALL_SAM2" = "true" ]; then \
      apt-get update && apt-get install -y --no-install-recommends git && \
      pip install --no-cache-dir -r requirements-sam2.txt && \
      rm -rf /var/lib/apt/lists/*; \
    fi

COPY app ./app

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
