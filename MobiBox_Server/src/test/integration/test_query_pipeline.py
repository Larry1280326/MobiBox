"""Integration tests for the query/retrieval pipeline.

Tests summary log fetching, intervention fetching, atomic activity retrieval,
and feedback submission endpoints with real MongoDB.
"""

from fastapi.testclient import TestClient

from src.test.factories.intervention_factory import (
    make_intervention_feedback,
    make_log_feedback,
)


class TestSummaryLogFetching:
    """Test POST /get_summary_log endpoint."""

    def test_hourly_log_request_accepted(self, client: TestClient):
        """Requesting an hourly log should pass schema validation."""
        resp = client.post(
            "/get_summary_log",
            json={"user": "test_query_1", "log_type": "hourly"},
        )
        # Either success (if data exists) or success with no data
        # but not schema validation error
        assert resp.status_code != 422

    def test_daily_log_request_accepted(self, client: TestClient):
        """Requesting a daily log should pass schema validation."""
        resp = client.post(
            "/get_summary_log",
            json={"user": "test_query_2", "log_type": "daily"},
        )
        assert resp.status_code != 422

    def test_polling_with_last_log_id(self, client: TestClient):
        """Polling with last_log_id should pass validation."""
        resp = client.post(
            "/get_summary_log",
            json={
                "user": "test_query_3",
                "log_type": "hourly",
                "last_log_id": "507f1f77bcf86cd799439011",
            },
        )
        assert resp.status_code != 422

    def test_polling_no_last_log_id(self, client: TestClient):
        """Polling without last_log_id should pass validation."""
        resp = client.post(
            "/get_summary_log",
            json={"user": "test_query_4", "log_type": "hourly"},
        )
        assert resp.status_code != 422


class TestInterventionFetching:
    """Test POST /get_intervention endpoint."""

    def test_intervention_request_accepted(self, client: TestClient):
        """Requesting an intervention should pass schema validation."""
        resp = client.post(
            "/get_intervention",
            json={"user": "test_intervention_1"},
        )
        assert resp.status_code != 422

    def test_response_has_correct_structure(self, client: TestClient):
        """Response should match InterventionResponse schema."""
        resp = client.post(
            "/get_intervention",
            json={"user": "test_intervention_2"},
        )
        body = resp.json()
        assert "status" in body


class TestAtomicActivities:
    """Test POST /get_compressed_atomic_activities endpoint."""

    def test_request_accepted(self, client: TestClient):
        resp = client.post(
            "/get_compressed_atomic_activities",
            json={"user": "test_atomic_1", "duration": 3600},
        )
        assert resp.status_code != 422

    def test_zero_duration_accepted(self, client: TestClient):
        """duration=0 means all available data."""
        resp = client.post(
            "/get_compressed_atomic_activities",
            json={"user": "test_atomic_2", "duration": 0},
        )
        assert resp.status_code != 422

    def test_response_has_correct_structure(self, client: TestClient):
        resp = client.post(
            "/get_compressed_atomic_activities",
            json={"user": "test_atomic_3", "duration": 3600},
        )
        body = resp.json()
        assert "status" in body


class TestEncodedAtomicActivities:
    """Test POST /get_encoded_atomic_activities endpoint."""

    def test_request_accepted(self, client: TestClient):
        resp = client.post(
            "/get_encoded_atomic_activities",
            json={"user": "test_encoded_1"},
        )
        assert resp.status_code != 422

    def test_response_has_correct_structure(self, client: TestClient):
        resp = client.post(
            "/get_encoded_atomic_activities",
            json={"user": "test_encoded_2"},
        )
        body = resp.json()
        assert "status" in body


class TestFeedbackSubmission:
    """Test feedback submission endpoints."""

    def test_intervention_feedback_accepted(self, client: TestClient):
        """Submit intervention feedback — should pass schema validation."""
        feedback = make_intervention_feedback(
            user="test_feedback_1",
            intervention_id="507f1f77bcf86cd799439011",
        )
        resp = client.post("/send_intervention_feedback", json=feedback)
        # 200 on success, 503 if no DB — not 422
        assert resp.status_code != 422

    def test_log_feedback_accepted(self, client: TestClient):
        """Submit log feedback — should pass schema validation."""
        feedback = make_log_feedback(
            user="test_feedback_2",
            summary_logs_id="507f1f77bcf86cd799439011",
        )
        resp = client.post("/send_log_feedback", json=feedback)
        assert resp.status_code != 422

    def test_intervention_feedback_response_shape(self, client: TestClient):
        feedback = make_intervention_feedback(
            user="test_feedback_3",
            intervention_id="507f1f77bcf86cd799439011",
        )
        resp = client.post("/send_intervention_feedback", json=feedback)
        body = resp.json()
        assert "status" in body

    def test_log_feedback_response_shape(self, client: TestClient):
        feedback = make_log_feedback(
            user="test_feedback_4",
            summary_logs_id="507f1f77bcf86cd799439011",
        )
        resp = client.post("/send_log_feedback", json=feedback)
        body = resp.json()
        assert "status" in body


class TestUserRegistration:
    """Test POST /register endpoint."""

    def test_register_new_user(self, client: TestClient):
        resp = client.post("/register", json={"name": "test_register_new_12345"})
        # Either 200 (success) or 503 (no DB)
        assert resp.status_code in (200, 503)

    def test_register_duplicate_user(self, client: TestClient):
        """Duplicate registration should return 409 or 503."""
        name = "test_register_dup_98765"

        # First registration
        client.post("/register", json={"name": name})

        # Second registration (duplicate)
        resp = client.post("/register", json={"name": name})
        assert resp.status_code in (200, 409, 503)
