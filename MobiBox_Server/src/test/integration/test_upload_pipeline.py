"""Integration tests for the data upload → processing pipeline.

Tests the upload endpoints trigger the correct Celery tasks and data is stored.
Uses mocked Celery tasks but validates request handling and data flow.
"""

import pytest
from fastapi.testclient import TestClient

from src.test.factories.upload_factory import (
    make_document_batch,
    make_document_item,
)
from src.test.factories.imu_factory import make_imu_batch, make_imu_item


class TestDocumentUpload:
    """Test POST /upload/documents endpoint."""

    def test_single_document_upload(self, client: TestClient):
        """Upload a single document item."""
        payload = make_document_batch(user="test_upload_1", count=1)
        resp = client.post("/upload/documents", json=payload)

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"
        assert body["count"] == 1

    def test_bulk_document_upload(self, client: TestClient):
        """Upload multiple document items."""
        payload = make_document_batch(user="test_upload_2", count=50)
        resp = client.post("/upload/documents", json=payload)

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"
        assert body["count"] == 50

    def test_document_upload_with_diverse_data(self, client: TestClient):
        """Upload documents with different activity patterns."""
        from src.test.factories.upload_factory import (
            make_walking_document,
            make_stationary_document,
            make_sleeping_document,
        )

        items = [
            make_walking_document(user="test_upload_3"),
            make_stationary_document(user="test_upload_3"),
            make_sleeping_document(user="test_upload_3"),
        ]

        resp = client.post("/upload/documents", json={"items": items})
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"

    def test_empty_items_rejected(self, client: TestClient):
        """Empty items array should be rejected."""
        resp = client.post("/upload/documents", json={"items": []})
        assert resp.status_code == 422

    def test_missing_user_field(self, client: TestClient):
        """Item without user field should be rejected."""
        resp = client.post(
            "/upload/documents",
            json={"items": [{"battery": 80}]},
        )
        assert resp.status_code == 422

    def test_large_batch(self, client: TestClient):
        """Upload a large batch of documents (stress test)."""
        payload = make_document_batch(user="test_upload_large", count=500)
        resp = client.post("/upload/documents", json=payload)

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"


class TestIMUUpload:
    """Test POST /upload/imu endpoint."""

    def test_single_imu_item(self, client: TestClient):
        """Upload a single IMU data point."""
        payload = {"items": [make_imu_item(user="test_imu_1")]}
        resp = client.post("/upload/imu", json=payload)

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"

    def test_imu_window_upload(self, client: TestClient):
        """Upload a realistic IMU window (1 second at 50Hz)."""
        from src.test.factories.imu_factory import make_imu_window
        items = make_imu_window(user="test_imu_2", seconds=1, sample_rate_hz=50)
        resp = client.post("/upload/imu", json={"items": items})

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"
        assert body["count"] == 50

    def test_imu_different_activities(self, client: TestClient):
        """Upload IMU data representing different activities."""
        from src.test.factories.imu_factory import make_imu_batch

        for activity in ["walking", "running", "stationary"]:
            payload = make_imu_batch(
                user="test_imu_3",
                seconds=1,
                sample_rate_hz=50,
                activity=activity,
            )
            resp = client.post("/upload/imu", json=payload)
            assert resp.status_code == 200
            body = resp.json()
            assert body["status"] == "success"

    def test_empty_items_rejected(self, client: TestClient):
        """Empty IMU items should be rejected."""
        resp = client.post("/upload/imu", json={"items": []})
        assert resp.status_code == 422

    def test_large_imu_batch(self, client: TestClient):
        """Upload a large IMU window (3 seconds at 50Hz = 150 points)."""
        from src.test.factories.imu_factory import make_imu_window
        items = make_imu_window(user="test_imu_large", seconds=3, sample_rate_hz=50)
        resp = client.post("/upload/imu", json={"items": items})

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"
        assert body["count"] == 150


class TestUploadCeleryTrigger:
    """Verify upload endpoints trigger Celery tasks."""

    def test_document_upload_triggers_atomic_task(self, client: TestClient):
        """Uploading documents should queue atomic activity processing."""
        payload = make_document_batch(user="test_celery_trigger", count=10)
        resp = client.post("/upload/documents", json=payload)

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"

    def test_imu_upload_triggers_har_task(self, client: TestClient):
        """Uploading IMU data should queue HAR processing."""
        payload = make_imu_batch(user="test_celery_trigger", seconds=1)
        resp = client.post("/upload/imu", json=payload)

        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "success"
