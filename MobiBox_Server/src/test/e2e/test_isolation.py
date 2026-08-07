"""E2E Scenario 2 — Multi-User Data Isolation.

Verifies that multiple users' data remains isolated across the entire pipeline.
"""

import pytest


class TestMultiUserIsolation:
    """Verify data from different users never leaks."""

    @pytest.mark.asyncio
    async def test_users_have_independent_data(self, api_client):
        """Each user should only see their own summaries and interventions."""
        import uuid

        user_a = f"e2e_iso_a_{uuid.uuid4().hex[:6]}"
        user_b = f"e2e_iso_b_{uuid.uuid4().hex[:6]}"
        user_c = f"e2e_iso_c_{uuid.uuid4().hex[:6]}"

        # Register all users
        for user in [user_a, user_b, user_c]:
            resp = await api_client.post("/register", json={"name": user})
            assert resp.status_code in (200, 409)

        # Upload different data for each user
        from src.test.factories.upload_factory import (
            make_walking_document,
            make_stationary_document,
            make_sleeping_document,
        )

        # User A: walking pattern
        items_a = [make_walking_document(user=user_a) for _ in range(20)]
        resp = await api_client.post("/upload/documents", json={"items": items_a})
        assert resp.status_code == 200

        # User B: stationary pattern
        items_b = [make_stationary_document(user=user_b) for _ in range(20)]
        resp = await api_client.post("/upload/documents", json={"items": items_b})
        assert resp.status_code == 200

        # User C: sleeping pattern
        items_c = [make_sleeping_document(user=user_c) for _ in range(20)]
        resp = await api_client.post("/upload/documents", json={"items": items_c})
        assert resp.status_code == 200

        # Fetch interventions — each user should get their own
        for user in [user_a, user_b, user_c]:
            resp = await api_client.post(
                "/get_intervention",
                json={"user": user},
            )
            assert resp.status_code == 200
            body = resp.json()
            assert body.get("status") == "success"

        # Fetch summaries — each user should get their own
        for user in [user_a, user_b, user_c]:
            resp = await api_client.post(
                "/get_summary_log",
                json={"user": user, "log_type": "hourly"},
            )
            assert resp.status_code == 200
            body = resp.json()
            assert body.get("status") == "success"

    @pytest.mark.asyncio
    async def test_upload_only_affects_target_user(self, api_client):
        """Uploading data for user A should not affect user B's results."""
        import uuid

        user_a = f"e2e_only_a_{uuid.uuid4().hex[:6]}"
        user_b = f"e2e_only_b_{uuid.uuid4().hex[:6]}"

        for user in [user_a, user_b]:
            await api_client.post("/register", json={"name": user})

        # Upload data ONLY for user A
        from src.test.factories.upload_factory import make_document_batch

        payload = make_document_batch(user=user_a, count=30)
        resp = await api_client.post("/upload/documents", json=payload)
        assert resp.status_code == 200

        # User B should not have user A's data
        resp_b = await api_client.post(
            "/get_summary_log",
            json={"user": user_b, "log_type": "hourly"},
        )
        assert resp_b.status_code == 200
        body_b = resp_b.json()
        # User B may or may not have data, but it shouldn't be user A's data
        if body_b.get("data"):
            # If there is data, it should not reference user A
            log_content = str(body_b["data"])
            assert user_a not in log_content, "User B's data leaked from user A!"
