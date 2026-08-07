"""End-to-end tests — full pipeline verification.

These tests simulate the Android client (via httpx) against a running backend
with real MongoDB, RabbitMQ, and Celery. They verify the complete data pipeline:

  Upload → HAR Processing → Atomic Activities → Summary → Intervention → Fetch

Run with:
  docker compose up -d  # MongoDB + RabbitMQ
  uvicorn src.main:app &
  celery -A src.celery_app.celery_app worker &
  pytest src/test/e2e/ -v
"""
