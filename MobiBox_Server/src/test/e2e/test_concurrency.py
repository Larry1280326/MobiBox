"""E2E Scenario 4 — Concurrent Uploads.

Verifies the backend handles multiple simultaneous clients without data corruption.
"""

import asyncio

import pytest


class TestConcurrentUploads:
    """Test concurrent upload behavior."""

    @pytest.mark.asyncio
    async def test_concurrent_document_uploads(self, api_client):
        """Multiple clients uploading documents simultaneously."""
        import uuid

        async def upload_client(client_name: str):
            from src.test.factories.upload_factory import make_document_batch

            user = f"e2e_conc_{client_name}_{uuid.uuid4().hex[:4]}"
            await api_client.post("/register", json={"name": user})

            payload = make_document_batch(user=user, count=20)
            resp = await api_client.post("/upload/documents", json=payload)
            return resp.status_code, user

        # Launch 5 concurrent uploads
        tasks = [upload_client(f"client_{i}") for i in range(5)]
        results = await asyncio.gather(*tasks)

        # All should succeed
        for status, user in results:
            assert status == 200, f"Concurrent upload failed for {user}: status={status}"

    @pytest.mark.asyncio
    async def test_concurrent_imu_uploads(self, api_client):
        """Multiple clients uploading IMU data simultaneously."""
        import uuid

        async def upload_imu(client_name: str):
            from src.test.factories.imu_factory import make_imu_batch

            user = f"e2e_imu_conc_{client_name}_{uuid.uuid4().hex[:4]}"
            await api_client.post("/register", json={"name": user})

            payload = make_imu_batch(user=user, seconds=1, activity="walking")
            resp = await api_client.post("/upload/imu", json=payload)
            return resp.status_code, user

        tasks = [upload_imu(f"client_{i}") for i in range(3)]
        results = await asyncio.gather(*tasks)

        for status, user in results:
            assert status == 200, f"Concurrent IMU upload failed for {user}: status={status}"

    @pytest.mark.asyncio
    async def test_mixed_concurrent_uploads(self, api_client):
        """Mix of document and IMU uploads happening concurrently."""
        import uuid

        async def upload_mixed(client_name: str):
            from src.test.factories.upload_factory import make_document_batch
            from src.test.factories.imu_factory import make_imu_batch

            user = f"e2e_mixed_{client_name}_{uuid.uuid4().hex[:4]}"
            await api_client.post("/register", json={"name": user})

            doc_resp = await api_client.post(
                "/upload/documents",
                json=make_document_batch(user=user, count=10),
            )
            imu_resp = await api_client.post(
                "/upload/imu",
                json=make_imu_batch(user=user, seconds=1),
            )

            return doc_resp.status_code, imu_resp.status_code, user

        tasks = [upload_mixed(f"client_{i}") for i in range(4)]
        results = await asyncio.gather(*tasks)

        for doc_status, imu_status, user in results:
            assert doc_status == 200, f"Document upload failed for {user}"
            assert imu_status == 200, f"IMU upload failed for {user}"

    @pytest.mark.asyncio
    async def test_concurrent_reads_after_writes(self, api_client):
        """Concurrent reads after uploading data."""
        import uuid

        user = f"e2e_read_{uuid.uuid4().hex[:6]}"
        await api_client.post("/register", json={"name": user})

        # Upload data
        from src.test.factories.upload_factory import make_document_batch
        payload = make_document_batch(user=user, count=30)
        resp = await api_client.post("/upload/documents", json=payload)
        assert resp.status_code == 200

        # Concurrent reads
        async def read_endpoint(endpoint, body):
            resp = await api_client.post(endpoint, json=body)
            return resp.status_code

        reads = [
            read_endpoint("/get_summary_log", {"user": user, "log_type": "hourly"}),
            read_endpoint("/get_intervention", {"user": user}),
            read_endpoint("/get_compressed_atomic_activities", {"user": user, "duration": 3600}),
            read_endpoint("/get_summary_log", {"user": user, "log_type": "daily"}),
        ]

        statuses = await asyncio.gather(*reads)
        for status in statuses:
            assert status == 200, f"Concurrent read failed: status={status}"
