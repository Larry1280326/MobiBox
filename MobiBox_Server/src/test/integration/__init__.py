"""Integration tests — verify services work with real MongoDB.

These tests use real MongoDB connections but mock external services (LLM, Baidu Maps).
Celery tasks are executed eagerly (CELERY_TASK_ALWAYS_EAGER=True) or mocked.
"""
