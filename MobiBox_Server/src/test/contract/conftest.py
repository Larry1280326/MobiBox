"""Fixtures for contract tests with real MongoDB/RabbitMQ.

Unlike the root conftest.py which mocks MongoDB and Celery, this conftest
uses real service connections for integration verification.

Usage:
    Install pytest-asyncio and ensure MongoDB + RabbitMQ are running:
        docker run -d -p 27017:27017 mongo:7
        docker run -d -p 5672:5672 rabbitmq:3-management

    Then run:
        CELERY_TASK_ALWAYS_EAGER=True pytest src/test/contract/ -v
"""

import os
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from src.main import app


def _reset_database_globals():
    """Reset Motor client globals so tests connect fresh."""
    import src.database as db_mod

    db_mod._async_client = None
    db_mod._async_db = None
    db_mod._async_client_loop_id = None
    db_mod._sync_client = None
    db_mod._sync_db = None


@pytest.fixture
def client():
    """FastAPI TestClient with real MongoDB (no mock) and mocked Celery.

    MongoDB connects to the URL in MONGODB_URL env var (default localhost:27017).
    Celery task.delay is mocked to avoid RabbitMQ complexity in contract tests.
    LLM calls are mocked to avoid external API calls.
    """
    # Mock Celery tasks
    mock_delay = MagicMock()

    # Mock LLM services to avoid real API calls
    from src.test.factories.llm_response_factory import (
        make_summary_output,
        make_intervention_output,
    )

    with patch(
        "src.celery_app.tasks.har_tasks.process_har_batch.delay", mock_delay
    ):
        with patch(
            "src.celery_app.tasks.atomic_tasks.process_atomic_activities_batch.delay",
            mock_delay,
        ):
            with patch(
                "src.celery_app.services.summary_service.generate_structured_output",
                AsyncMock(return_value=make_summary_output()),
            ):
                with patch(
                    "src.celery_app.services.intervention_service.generate_structured_output",
                    AsyncMock(return_value=make_intervention_output()),
                ):
                    _reset_database_globals()
                    yield TestClient(app)


@pytest.fixture(autouse=True)
async def cleanup_database():
    """Clean up test data after each contract test.

    Drops all test data from MongoDB collections to ensure test isolation.
    """
    yield  # Let the test run

    # After test: clean up test users
    try:
        import src.database as db_mod

        db = await db_mod.get_database()
        collections = await db.list_collection_names()
        for coll_name in collections:
            if coll_name != "app_categories":
                # Only delete test user data, not seed data
                await db[coll_name].delete_many({"user": {"$regex": "^(test_|e2e_|contract_)"}})
    except Exception:
        pass  # Cleanup is best-effort


# Fixtures to check if services are available
@pytest.fixture(scope="session")
def mongodb_available():
    """Check if MongoDB is available for contract tests."""
    try:
        from pymongo import MongoClient

        url = os.getenv("MONGODB_URL", "mongodb://localhost:27017")
        client = MongoClient(url, serverSelectionTimeoutMS=2000)
        client.admin.command("ping")
        client.close()
        return True
    except Exception:
        return False


@pytest.fixture(scope="session")
def rabbitmq_available():
    """Check if RabbitMQ is available."""
    try:
        import pika

        url = os.getenv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672//")
        params = pika.URLParameters(url)
        conn = pika.BlockingConnection(params)
        conn.close()
        return True
    except Exception:
        return False
