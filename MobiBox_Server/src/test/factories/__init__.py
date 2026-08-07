"""Test data factories for MobiBox Server.

Each factory generates dicts matching the Pydantic schemas with sensible defaults.
All factories accept `**overrides` for test-specific customization.

Usage:
    from src.test.factories.upload_factory import make_document_item, make_document_batch
    doc = make_document_item(user="test_user", battery=50)
    batch = make_document_batch(user="test_user", count=10)
"""
