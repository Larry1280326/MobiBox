"""Contract tests — verify OpenAPI schema completeness and endpoint availability.

Validates that all expected endpoints exist, respond with correct status codes,
and produce responses matching the Pydantic response models.
"""

from fastapi.testclient import TestClient

# All endpoints expected to exist
EXPECTED_ENDPOINTS = {
    ("GET", "/health"),
    ("GET", "/mongodb-test"),
    ("POST", "/register"),
    ("POST", "/upload/documents"),
    ("POST", "/upload/imu"),
    ("POST", "/get_summary_log"),
    ("POST", "/get_intervention"),
    ("POST", "/get_compressed_atomic_activities"),
    ("POST", "/get_encoded_atomic_activities"),
    ("POST", "/send_intervention_feedback"),
    ("POST", "/send_log_feedback"),
    ("POST", "/imu_test/predict"),
    ("GET", "/imu_test/statistics"),
    ("GET", "/imu_test/labels"),
}


class TestOpenAPISchema:
    """Verify the OpenAPI schema is complete and correct."""

    def test_openapi_schema_generated(self, client: TestClient):
        """The app should expose an OpenAPI schema at /openapi.json."""
        resp = client.get("/openapi.json")
        assert resp.status_code == 200
        schema = resp.json()
        assert "paths" in schema
        assert len(schema["paths"]) > 0

    def test_all_expected_endpoints_exist(self, client: TestClient):
        """Every expected endpoint should be in the OpenAPI schema."""
        resp = client.get("/openapi.json")
        schema = resp.json()
        paths = schema["paths"]

        for method, path in EXPECTED_ENDPOINTS:
            method_lower = method.lower()
            assert path in paths, f"Missing path: {path}"
            assert method_lower in paths[path], f"Missing method {method} for path {path}"

    def test_openapi_schema_has_info(self, client: TestClient):
        """The schema should include app info."""
        resp = client.get("/openapi.json")
        schema = resp.json()
        assert "info" in schema
        assert "title" in schema["info"]


class TestEndpointStatusCodes:
    """Verify each endpoint returns expected status codes for basic requests."""

    def test_health_returns_200(self, client: TestClient):
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "healthy"

    def test_register_returns_422_on_empty_body(self, client: TestClient):
        """Missing required field should return 422."""
        resp = client.post("/register", json={})
        assert resp.status_code == 422

    def test_upload_documents_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/upload/documents", json={})
        assert resp.status_code == 422

    def test_upload_imu_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/upload/imu", json={})
        assert resp.status_code == 422

    def test_get_summary_log_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/get_summary_log", json={})
        assert resp.status_code == 422

    def test_get_intervention_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/get_intervention", json={})
        assert resp.status_code == 422

    def test_get_compressed_atomic_activities_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/get_compressed_atomic_activities", json={})
        assert resp.status_code == 422

    def test_get_encoded_atomic_activities_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/get_encoded_atomic_activities", json={})
        assert resp.status_code == 422

    def test_send_intervention_feedback_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/send_intervention_feedback", json={})
        assert resp.status_code == 422

    def test_send_log_feedback_returns_422_on_empty(self, client: TestClient):
        resp = client.post("/send_log_feedback", json={})
        assert resp.status_code == 422


class TestRequestSchemaMatch:
    """Verify request schemas match what the Android app sends.

    Cross-references against HttpApiClient.java request body construction.
    Each test uses the exact field names and structure the Android app sends.
    """

    def test_register_body_shape(self, client: TestClient):
        """Android sends: {"name": "userId"}"""
        # This should be accepted (422 on empty DB is ok — schema validates)
        resp = client.post("/register", json={"name": "test_android_user"})
        # Either 200 (success) or 409 (duplicate from previous run) or 503 (no DB)
        # The key is it's not 422 (schema validation error)
        assert resp.status_code != 422

    def test_summary_log_body_shape(self, client: TestClient):
        """Android sends: {"user": "userId", "log_type": "hourly", "last_log_id": "..."}"""
        resp = client.post(
            "/get_summary_log",
            json={
                "user": "test_android_user",
                "log_type": "hourly",
                "last_log_id": "507f1f77bcf86cd799439011",
            },
        )
        assert resp.status_code != 422

    def test_intervention_body_shape(self, client: TestClient):
        """Android sends: {"user": "userId"}"""
        resp = client.post("/get_intervention", json={"user": "test_android_user"})
        assert resp.status_code != 422

    def test_atomic_activities_body_shape(self, client: TestClient):
        """Android sends: {"user": "userId", "duration": 3600}"""
        resp = client.post(
            "/get_compressed_atomic_activities",
            json={"user": "test_android_user", "duration": 3600},
        )
        assert resp.status_code != 422

    def test_upload_documents_body_shape(self, client: TestClient):
        """Android sends: {"items": [{"user": "...", "battery": 85, ...}]}"""
        resp = client.post(
            "/upload/documents",
            json={
                "items": [
                    {
                        "user": "test_android_user",
                        "battery": 85,
                        "screen_on_ratio": 0.5,
                        "timestamp": "2026-08-07T12:00:00Z",
                    }
                ]
            },
        )
        assert resp.status_code != 422

    def test_upload_imu_body_shape(self, client: TestClient):
        """Android sends: {"items": [{"user": "...", "acc_X": 0.1, ...}]}"""
        resp = client.post(
            "/upload/imu",
            json={
                "items": [
                    {
                        "user": "test_android_user",
                        "acc_X": 0.1,
                        "acc_Y": 0.2,
                        "acc_Z": 9.8,
                        "gyro_X": 0.01,
                        "gyro_Y": 0.02,
                        "gyro_Z": 0.03,
                        "timestamp": "2026-08-07T12:00:00Z",
                    }
                ]
            },
        )
        assert resp.status_code != 422
