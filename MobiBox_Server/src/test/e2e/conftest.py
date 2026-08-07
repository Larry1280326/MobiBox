"""Fixtures for E2E pipeline tests.

Requires a running backend (see module docstring).
If CELERY_TASK_ALWAYS_EAGER is set, Celery tasks execute synchronously,
which simplifies testing but doesn't exercise the real queue.
"""

import os

import pytest
import httpx


@pytest.fixture(scope="session")
def backend_url():
    """URL of the running backend.

    Defaults to localhost:8001. Override with BACKEND_URL env var.
    """
    return os.getenv("BACKEND_URL", "http://localhost:8001")


@pytest.fixture
async def api_client(backend_url):
    """Async HTTP client for E2E tests."""
    async with httpx.AsyncClient(base_url=backend_url, timeout=30.0) as client:
        yield client


@pytest.fixture
def unique_user():
    """Generate a unique test user name for isolation."""
    import uuid

    return f"e2e_{uuid.uuid4().hex[:8]}"


@pytest.fixture(autouse=True)
def mock_llm_services():
    """Mock LLM services for all E2E tests to avoid real API calls.

    Even in E2E tests, we mock the LLM to avoid costs and flakiness.
    The actual data pipeline (upload → store → process → retrieve) is
    tested with real MongoDB, RabbitMQ, and Celery.

    Note: In E2E tests, the backend runs as a separate process, so
    patches in the test process don't affect it. The backend should be
    started with mock LLM configuration.
    """
    pass  # No patches here — E2E tests call the real running server
