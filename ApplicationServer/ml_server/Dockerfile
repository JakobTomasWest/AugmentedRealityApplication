# ---- Runtime image with Python + deps
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/root/.cache/huggingface \
    TORCH_NUM_THREADS=2

# system libs for Pillow/OpenCV backends used by HF vision models
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# install python deps
COPY ml_server/requirements.txt /app/requirements.txt
RUN pip install --upgrade pip && pip install -r /app/requirements.txt

# app code
COPY ml_server/ /app/

EXPOSE 8000
# default command; compose can override env MODEL_TASK/MODEL_ID
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]