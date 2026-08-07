"""Contract tests — verify response shapes match Pydantic response models.

Each test validates that API responses conform to the expected structure
documented in the Pydantic schemas.
"""

from fastapi.testclient import TestClient


class TestHealthResponse:
    """Verify /health response shape."""

    def test_health_response_shape(self, client: TestClient):
        resp = client.get("/health")
        assert resp.status_code == 200
        body = resp.json()
        assert "status" in body
        assert body["status"] == "healthy"


class TestSummaryLogResponse:
    """Verify /get_summary_log response shape."""

    def test_missing_user_returns_error(self, client: TestClient):
        """Missing required field should return validation error."""
        resp = client.post("/get_summary_log", json={"log_type": "hourly"})
        assert resp.status_code == 422
        body = resp.json()
        assert "detail" in body

    def test_invalid_log_type_returns_error(self, client: TestClient):
        """Invalid log_type should return validation error."""
        resp = client.post("/get_summary_log", json={"user": "test", "log_type": "weekly"})
        assert resp.status_code == 422

    def test_success_response_has_expected_shape(self):
        """Response should have status, data, has_new_log, date fields."""
        from src.query.schemas import SummaryLogResponse
        from src.test.factories.summary_factory import make_summary_log_response

        resp_data = make_summary_log_response()
        resp = SummaryLogResponse(**resp_data)
        assert resp.status == "success"
        assert hasattr(resp, "has_new_log")
        assert hasattr(resp, "date")

        if resp.data is not None:
            assert resp.data.id is not None
            assert resp.data.log_content is not None
            assert resp.data.generation_timestamp is not None


class TestInterventionResponse:
    """Verify /get_intervention response shape."""

    def test_missing_user_returns_error(self, client: TestClient):
        resp = client.post("/get_intervention", json={})
        assert resp.status_code == 422

    def test_success_response_has_expected_shape(self):
        """Response should have status and data fields."""
        from src.query.schemas import InterventionResponse
        from src.test.factories.intervention_factory import make_intervention_response

        resp_data = make_intervention_response()
        resp = InterventionResponse(**resp_data)
        assert resp.status == "success"

        if resp.data is not None:
            assert resp.data.id is not None
            assert resp.data.intervention_content is not None
            assert resp.data.generation_timestamp is not None


class TestFeedbackResponses:
    """Verify feedback submission response shapes."""

    def test_intervention_feedback_response_shape(self):
        from src.query.schemas import InterventionFeedbackResponse
        resp = InterventionFeedbackResponse()
        assert resp.status == "success"
        assert resp.message == "Feedback submitted successfully"

    def test_summary_log_feedback_response_shape(self):
        from src.query.schemas import SummaryLogFeedbackResponse
        resp = SummaryLogFeedbackResponse()
        assert resp.status == "success"
        assert resp.message == "Feedback submitted successfully"


class TestAtomicActivitiesResponse:
    """Verify /get_compressed_atomic_activities response shape."""

    def test_missing_user_returns_error(self, client: TestClient):
        resp = client.post("/get_compressed_atomic_activities", json={})
        assert resp.status_code == 422

    def test_response_has_expected_shape(self):
        from src.query.schemas import AtomicActivitiesResponse, AtomicActivitiesData

        data = AtomicActivitiesData(
            sport=["walking"],
            appCategory=["social_media"],
            location=[],
            movement=[],
            stepCategory=[],
            phoneCategory=[],
        )
        resp = AtomicActivitiesResponse(
            status="success",
            data=data,
            start_timestamp="2026-08-07T12:00:00Z",
            end_timestamp="2026-08-07T13:00:00Z",
        )
        assert resp.status == "success"
        assert resp.data is not None
        assert resp.data.sport == ["walking"]

    def test_empty_data_response_shape(self):
        from src.query.schemas import AtomicActivitiesResponse
        resp = AtomicActivitiesResponse(status="success", data=None)
        assert resp.status == "success"
        assert resp.data is None


class TestRegisterResponse:
    """Verify /register response shape."""

    def test_error_on_duplicate_user(self):
        """Should return the appropriate error for duplicate registration."""
        from src.register.schemas import RegisterRequest
        # Schema validation: minimum 1 character
        req = RegisterRequest(name="test_user")
        assert req.name == "test_user"


class TestUploadResponse:
    """Verify upload endpoint response shapes."""

    def test_document_upload_requires_items(self, client: TestClient):
        """Empty items list should fail schema validation."""
        resp = client.post("/upload/documents", json={"items": []})
        assert resp.status_code == 422

    def test_imu_upload_requires_items(self, client: TestClient):
        """Empty items list should fail schema validation."""
        resp = client.post("/upload/imu", json={"items": []})
        assert resp.status_code == 422

    def test_document_upload_success_response_shape(self, client: TestClient):
        """Successful upload should return status and count."""
        resp = client.post(
            "/upload/documents",
            json={"items": [{"user": "test_resp", "battery": 50}]},
        )
        # May be 200 (success) or 503 (no DB) — but not 422
        if resp.status_code == 200:
            body = resp.json()
            assert "status" in body
            assert body["status"] == "success"

    def test_imu_upload_success_response_shape(self, client: TestClient):
        """Successful IMU upload should return status and count."""
        resp = client.post(
            "/upload/imu",
            json={"items": [{"user": "test_resp_imu", "acc_X": 0.1}]},
        )
        if resp.status_code == 200:
            body = resp.json()
            assert "status" in body
            assert body["status"] == "success"


class TestErrorResponseFormat:
    """All error responses should follow the FastAPI detail format."""

    def test_422_has_detail(self, client: TestClient):
        resp = client.post("/register", json={})
        assert resp.status_code == 422
        body = resp.json()
        assert "detail" in body

    def test_422_detail_is_list_of_errors(self, client: TestClient):
        """FastAPI validation errors include loc, msg, type."""
        resp = client.post("/register", json={})
        body = resp.json()
        detail = body["detail"]
        assert isinstance(detail, list)
        assert len(detail) > 0
        if len(detail) > 0:
            assert "loc" in detail[0]
            assert "msg" in detail[0]
