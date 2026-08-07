"""Tests for MongoDB database connection lifecycle (Motor + PyMongo).

Tests the async get_database(), close_database(), get_sync_database(), and
check_connection() functions with mocked Motor/PyMongo clients.
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest


class TestGetDatabase:
    """Test the async get_database() function."""

    @pytest.mark.asyncio
    async def test_creates_client_on_first_call(self, mongodb_mock):
        """First call to get_database() should create a new Motor client."""
        mock_motor_client = MagicMock()
        mock_motor_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_motor_client.close = MagicMock()
        mock_motor_cls = MagicMock(return_value=mock_motor_client)

        with patch("src.database.AsyncIOMotorClient", mock_motor_cls):
            # Reset globals
            import src.database as db_mod
            db_mod._async_client = None
            db_mod._async_db = None
            db_mod._async_client_loop_id = None

            db = await db_mod.get_database()

            assert db is not None
            assert db_mod._async_client is not None
            assert db_mod._async_db is not None
            mock_motor_cls.assert_called_once()

    @pytest.mark.asyncio
    async def test_reuses_client_on_second_call(self, mongodb_mock):
        """Second call should reuse the cached client (same event loop)."""
        mock_motor_client = MagicMock()
        mock_motor_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_motor_client.close = MagicMock()
        mock_motor_cls = MagicMock(return_value=mock_motor_client)

        with patch("src.database.AsyncIOMotorClient", mock_motor_cls):
            import src.database as db_mod
            db_mod._async_client = None
            db_mod._async_db = None
            db_mod._async_client_loop_id = None

            db1 = await db_mod.get_database()
            db2 = await db_mod.get_database()

            assert db1 is db2
            # Should only create the client once
            assert mock_motor_cls.call_count == 1

    @pytest.mark.asyncio
    async def test_recreates_client_on_loop_change(self, mongodb_mock):
        """When event loop changes, a new client should be created."""
        # This test simulates what happens in Celery workers where
        # asyncio.run() creates a new event loop per task.
        mock_motor_client = MagicMock()
        mock_motor_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_motor_client.close = MagicMock()
        mock_motor_cls = MagicMock(return_value=mock_motor_client)

        with patch("src.database.AsyncIOMotorClient", mock_motor_cls):
            import src.database as db_mod
            db_mod._async_client = None
            db_mod._async_db = None
            db_mod._async_client_loop_id = None

            _ = await db_mod.get_database()
            first_loop_id = db_mod._async_client_loop_id
            assert first_loop_id is not None

            # Simulate a different event loop
            db_mod._async_client_loop_id = 99999

            _ = await db_mod.get_database()
            second_loop_id = db_mod._async_client_loop_id

            # Should have recreated (loop ID changed)
            assert mock_motor_cls.call_count == 2
            assert second_loop_id != 99999  # Updated to real loop ID
            assert db_mod._async_client.close.call_count > 0  # Old client was closed


class TestCloseDatabase:
    """Test the async close_database() function."""

    @pytest.mark.asyncio
    async def test_close_resets_globals(self, mongodb_mock):
        """close_database() should reset all global state."""
        mock_motor_client = MagicMock()
        mock_motor_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_motor_client.close = MagicMock()
        mock_motor_cls = MagicMock(return_value=mock_motor_client)

        with patch("src.database.AsyncIOMotorClient", mock_motor_cls):
            import src.database as db_mod
            db_mod._async_client = None
            db_mod._async_db = None
            db_mod._async_client_loop_id = None

            await db_mod.get_database()
            assert db_mod._async_client is not None

            await db_mod.close_database()
            assert db_mod._async_client is None
            assert db_mod._async_db is None
            assert db_mod._async_client_loop_id is None

    @pytest.mark.asyncio
    async def test_close_when_already_closed_is_safe(self):
        """Closing when already closed should not raise."""
        import src.database as db_mod
        db_mod._async_client = None
        db_mod._async_db = None
        db_mod._async_client_loop_id = None

        # Should not raise
        await db_mod.close_database()
        assert db_mod._async_client is None


class TestGetSyncDatabase:
    """Test the sync get_sync_database() function."""

    def test_creates_client_on_first_call(self, mongodb_mock):
        mock_sync_client = MagicMock()
        mock_sync_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_sync_cls = MagicMock(return_value=mock_sync_client)

        with patch("src.database.MongoClient", mock_sync_cls):
            import src.database as db_mod
            db_mod._sync_client = None
            db_mod._sync_db = None

            db = db_mod.get_sync_database()

            assert db is not None
            assert db_mod._sync_client is not None
            mock_sync_cls.assert_called_once()

    def test_reuses_client(self, mongodb_mock):
        mock_sync_client = MagicMock()
        mock_sync_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_sync_cls = MagicMock(return_value=mock_sync_client)

        with patch("src.database.MongoClient", mock_sync_cls):
            import src.database as db_mod
            db_mod._sync_client = None
            db_mod._sync_db = None

            db1 = db_mod.get_sync_database()
            db2 = db_mod.get_sync_database()

            assert db1 is db2
            assert mock_sync_cls.call_count == 1


class TestCheckConnection:
    """Test the health check function."""

    @pytest.mark.asyncio
    async def test_check_connection_healthy(self, mongodb_mock):
        """check_connection() should return connected status on success."""
        mongodb_mock.command = AsyncMock(return_value={"ok": 1})

        mock_motor_client = MagicMock()
        mock_motor_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_motor_client.close = MagicMock()
        mock_motor_cls = MagicMock(return_value=mock_motor_client)

        with patch("src.database.AsyncIOMotorClient", mock_motor_cls):
            import src.database as db_mod
            db_mod._async_client = None
            db_mod._async_db = None
            db_mod._async_client_loop_id = None

            result = await db_mod.check_connection()

            assert result["status"] == "connected"
            assert "url" in result
            assert "database" in result

    @pytest.mark.asyncio
    async def test_check_connection_error(self, mongodb_mock):
        """check_connection() should return error status on failure."""
        mongodb_mock.command = AsyncMock(side_effect=Exception("Connection refused"))

        mock_motor_client = MagicMock()
        mock_motor_client.__getitem__ = MagicMock(return_value=mongodb_mock)
        mock_motor_client.close = MagicMock()
        mock_motor_cls = MagicMock(return_value=mock_motor_client)

        with patch("src.database.AsyncIOMotorClient", mock_motor_cls):
            import src.database as db_mod
            db_mod._async_client = None
            db_mod._async_db = None
            db_mod._async_client_loop_id = None

            result = await db_mod.check_connection()

            assert result["status"] == "error"
            assert "message" in result
