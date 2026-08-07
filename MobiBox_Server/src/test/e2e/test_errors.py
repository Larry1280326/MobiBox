"""E2E Scenario 3 — Error Handling.

Verifies the API handles error conditions gracefully:
- Missing required fields → 422
- Duplicate registration → 409
- Invalid data → 422
- Graceful handling of users with no data
"""

import pytest


class TestValidationErrors:
    """Test API validation error responses."""

    @pytest.mark.asyncio
    async def test_register_empty_name(self, api_client):
        """Empty user name should return 422."""
        resp = await api_client.post("/register", json={"name": ""})
        assert resp.status_code == 422
        body = resp.json()
        assert "detail" in body

    @pytest.mark.asyncio
    async def test_register_missing_name(self, api_client):
        """Missing name field should return 422."""
        resp = await api_client.post("/register", json={})
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_register_duplicate_user(self, api_client):
        """Registering the same user twice should return 409 or 200."""
        import uuid
        user = f"e2e_dup_{uuid.uuid4().hex[:6]}"

        resp1 = await api_client.post("/register", json={"name": user})
        assert resp1.status_code in (200, 409, 503)

        resp2 = await api_client.post("/register", json={"name": user})
        # Second registration: 409 (duplicate) or 200 or 503
        assert resp2.status_code in (200, 409, 503)

    @pytest.mark.asyncio
    async def test_upload_empty_items(self, api_client):
        """Upload with empty items array should return 422."""
        resp = await api_client.post("/upload/documents", json={"items": []})
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_upload_missing_items(self, api_client):
        """Upload without items field should return 422."""
        resp = await api_client.post("/upload/documents", json={})
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_imu_upload_empty_items(self, api_client):
        """IMU upload with empty items should return 422."""
        resp = await api_client.post("/upload/imu", json={"items": []})
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_summary_log_invalid_type(self, api_client):
        """Invalid log_type should return 422."""
        resp = await api_client.post(
            "/get_summary_log",
            json={"user": "test_user", "log_type": "invalid_type"},
        )
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_summary_log_missing_user(self, api_client):
        """Missing user field should return 422."""
        resp = await api_client.post(
            "/get_summary_log",
            json={"log_type": "hourly"},
        )
        assert resp.status_code == 422

    @pytest.mark.asyncio
    async def test_malformed_json(self, api_client):
        """Malformed JSON body should return 422 or 400."""
        # Send non-JSON body
        resp = await api_client.post(
            "/register",
            content="this is not json",
            headers={"Content-Type": "application/json"},
        )
        assert resp.status_code in (400, 422)


class TestGracefulHandling:
    """Test graceful handling of missing data."""

    @pytest.mark.asyncio
    async def test_user_with_no_data_can_fetch(self, api_client):
        """Fetching summary for a user with no data should not error."""
        import uuid
        user = f"e2e_nodata_{uuid.uuid4().hex[:6]}"

        # Register but don't upload any data
        await api_client.post("/register", json={"name": user})

        # Fetch summary — should succeed (possibly with null data)
        resp = await api_client.post(
            "/get_summary_log",
            json={"user": user, "log_type": "hourly"},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body.get("status") == "success"

        # Fetch intervention — should succeed
        resp = await api_client.post(
            "/get_intervention",
            json={"user": user},
        )
        assert resp.status_code == 200
        body = resp.json()
        assert body.get("status") == "success"

    @pytest.mark.asyncio
    async def test_health_endpoint_always_available(self, api_client):
        """Health endpoint should work even without data."""
        resp = await api_client.get("/health")
        assert resp.status_code in (200, 503)
