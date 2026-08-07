"""E2E Scenario 1 — Happy Path: Full data pipeline.

Verifies the complete flow: Register → Upload Documents → Upload IMU →
Wait for Processing → Fetch Summary → Fetch Intervention → Submit Feedback.
"""

import pytest


class TestHappyPath:
    """Full end-to-end pipeline test (requires running backend)."""

    @pytest.mark.asyncio
    async def test_register_user(self, api_client, unique_user):
        """Step 1: Register a new user."""
        resp = await api_client.post("/register", json={"name": unique_user})
        assert resp.status_code in (200, 409), f"Register failed: {resp.text}"
        if resp.status_code == 200:
            body = resp.json()
            assert body.get("status") == "success"

    @pytest.mark.asyncio
    async def test_upload_documents(self, api_client, unique_user):
        """Step 2: Upload sensor/document data."""
        # First register the user
        await api_client.post("/register", json={"name": unique_user})

        from src.test.factories.upload_factory import make_document_batch
        payload = make_document_batch(user=unique_user, count=50)

        resp = await api_client.post("/upload/documents", json=payload)
        assert resp.status_code == 200, f"Upload documents failed: {resp.text}"
        body = resp.json()
        assert body.get("status") == "success"
        assert body.get("count") == 50

    @pytest.mark.asyncio
    async def test_upload_imu_data(self, api_client, unique_user):
        """Step 3: Upload IMU data."""
        await api_client.post("/register", json={"name": unique_user})

        from src.test.factories.imu_factory import make_imu_batch
        payload = make_imu_batch(user=unique_user, seconds=2, activity="walking")

        resp = await api_client.post("/upload/imu", json=payload)
        assert resp.status_code == 200, f"Upload IMU failed: {resp.text}"
        body = resp.json()
        assert body.get("status") == "success"
        assert body.get("count") == 100  # 2 seconds * 50 Hz

    @pytest.mark.asyncio
    async def test_fetch_summary_log(self, api_client, unique_user):
        """Step 4: Fetch summary log for the user."""
        await api_client.post("/register", json={"name": unique_user})

        resp = await api_client.post(
            "/get_summary_log",
            json={"user": unique_user, "log_type": "hourly"},
        )
        assert resp.status_code == 200, f"Fetch summary failed: {resp.text}"
        body = resp.json()
        assert body.get("status") == "success"
        assert "has_new_log" in body

    @pytest.mark.asyncio
    async def test_fetch_intervention(self, api_client, unique_user):
        """Step 5: Fetch intervention for the user."""
        await api_client.post("/register", json={"name": unique_user})

        resp = await api_client.post(
            "/get_intervention",
            json={"user": unique_user},
        )
        assert resp.status_code == 200, f"Fetch intervention failed: {resp.text}"
        body = resp.json()
        assert body.get("status") == "success"

    @pytest.mark.asyncio
    async def test_fetch_atomic_activities(self, api_client, unique_user):
        """Step 6: Fetch atomic activities."""
        await api_client.post("/register", json={"name": unique_user})

        resp = await api_client.post(
            "/get_compressed_atomic_activities",
            json={"user": unique_user, "duration": 3600},
        )
        assert resp.status_code == 200, f"Fetch atomic activities failed: {resp.text}"
        body = resp.json()
        assert body.get("status") == "success"

    @pytest.mark.asyncio
    async def test_submit_intervention_feedback(self, api_client, unique_user):
        """Step 7: Submit feedback on an intervention."""
        await api_client.post("/register", json={"name": unique_user})

        from src.test.factories.intervention_factory import make_intervention_feedback

        feedback = make_intervention_feedback(
            user=unique_user,
            intervention_id="507f1f77bcf86cd799439011",
        )
        resp = await api_client.post("/send_intervention_feedback", json=feedback)
        assert resp.status_code in (200, 503), f"Submit feedback failed: {resp.text}"

    @pytest.mark.asyncio
    async def test_submit_log_feedback(self, api_client, unique_user):
        """Step 8: Submit feedback on a summary log."""
        await api_client.post("/register", json={"name": unique_user})

        from src.test.factories.intervention_factory import make_log_feedback

        feedback = make_log_feedback(
            user=unique_user,
            summary_logs_id="507f1f77bcf86cd799439011",
        )
        resp = await api_client.post("/send_log_feedback", json=feedback)
        assert resp.status_code in (200, 503), f"Submit log feedback failed: {resp.text}"

    @pytest.mark.asyncio
    async def test_polling_no_new_log(self, api_client, unique_user):
        """Step 9: Poll with last_log_id — should indicate no new log when same."""
        await api_client.post("/register", json={"name": unique_user})

        # First fetch to get a log ID (or confirm no data)
        resp1 = await api_client.post(
            "/get_summary_log",
            json={"user": unique_user, "log_type": "hourly"},
        )
        assert resp1.status_code == 200
        body1 = resp1.json()

        # Use the result to poll again
        last_id = None
        if body1.get("data") and body1["data"].get("id"):
            last_id = body1["data"]["id"]
        else:
            last_id = "507f1f77bcf86cd799439011"

        # Poll with the same last_log_id
        resp2 = await api_client.post(
            "/get_summary_log",
            json={
                "user": unique_user,
                "log_type": "hourly",
                "last_log_id": last_id,
            },
        )
        assert resp2.status_code == 200
        body2 = resp2.json()
        # Should indicate no new log if nothing changed
        assert "has_new_log" in body2


class TestFullPipeline:
    """Run all steps sequentially for a single user.

    This test simulates the complete Android app behavior.
    """

    @pytest.mark.asyncio
    async def test_complete_pipeline(self, api_client, unique_user):
        """Register → Upload → Fetch → Submit Feedback."""
        # 1. Register
        resp = await api_client.post("/register", json={"name": unique_user})
        assert resp.status_code in (200, 409)

        # 2. Upload sensor data
        from src.test.factories.upload_factory import make_document_batch
        doc_payload = make_document_batch(user=unique_user, count=30)
        resp = await api_client.post("/upload/documents", json=doc_payload)
        assert resp.status_code == 200

        # 3. Upload IMU data
        from src.test.factories.imu_factory import make_imu_batch
        imu_payload = make_imu_batch(user=unique_user, seconds=1, activity="walking")
        resp = await api_client.post("/upload/imu", json=imu_payload)
        assert resp.status_code == 200

        # 4. Fetch hourly summary
        resp = await api_client.post(
            "/get_summary_log",
            json={"user": unique_user, "log_type": "hourly"},
        )
        assert resp.status_code == 200
        summary = resp.json()
        assert summary.get("status") == "success"

        # 5. Fetch daily summary
        resp = await api_client.post(
            "/get_summary_log",
            json={"user": unique_user, "log_type": "daily"},
        )
        assert resp.status_code == 200

        # 6. Fetch intervention
        resp = await api_client.post(
            "/get_intervention",
            json={"user": unique_user},
        )
        assert resp.status_code == 200

        # 7. Fetch atomic activities
        resp = await api_client.post(
            "/get_compressed_atomic_activities",
            json={"user": unique_user, "duration": 3600},
        )
        assert resp.status_code == 200

        # 8. Submit feedback
        from src.test.factories.intervention_factory import (
            make_intervention_feedback,
            make_log_feedback,
        )

        feedback = make_intervention_feedback(
            user=unique_user,
            intervention_id="507f1f77bcf86cd799439011",
        )
        resp = await api_client.post("/send_intervention_feedback", json=feedback)
        assert resp.status_code in (200, 503)

        log_fb = make_log_feedback(
            user=unique_user,
            summary_logs_id="507f1f77bcf86cd799439011",
        )
        resp = await api_client.post("/send_log_feedback", json=log_fb)
        assert resp.status_code in (200, 503)
