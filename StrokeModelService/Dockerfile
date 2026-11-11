# syntax=docker/dockerfile:1

FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_NO_CACHE_DIR=1

WORKDIR /app

# Cài dependency trước để tận dụng Docker build cache.
COPY requirements.txt ./

RUN python -m pip install --upgrade pip \
    && python -m pip install -r requirements.txt

# Copy source code và mô hình đã huấn luyện.
COPY app/ ./app/
COPY artifacts/ ./artifacts/

# Không chạy container bằng root.
RUN groupadd --system careconnect \
    && useradd \
       --system \
       --gid careconnect \
       --no-create-home \
       careconnect \
    && chown -R careconnect:careconnect /app

USER careconnect

EXPOSE 8090

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=20s \
    --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8090/health', timeout=3)"

ENTRYPOINT [
    "python",
    "-m",
    "uvicorn",
    "app.main:app",
    "--host",
    "0.0.0.0",
    "--port",
    "8090"
]