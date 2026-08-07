"""Tests for application configuration loading via pydantic-settings."""

import os
from unittest.mock import patch

from src.config import Settings, LLMSettings, get_settings, get_llm_settings


class TestSettings:
    """Test the main Settings class."""

    def test_defaults(self):
        """Settings should have sensible defaults."""
        settings = Settings()
        assert settings.app_name == "MobiBox API"
        assert settings.app_version == "1.0.0"
        assert settings.debug is False
        assert settings.mongodb_url == "mongodb://localhost:27017"
        assert settings.mongodb_db_name == "mobibox"
        assert settings.mongodb_max_pool_size == 20
        assert settings.mongodb_min_pool_size == 0

    def test_mongodb_timeout_defaults(self):
        settings = Settings()
        assert settings.mongodb_server_selection_timeout_ms == 5000
        assert settings.mongodb_connect_timeout_ms == 5000

    def test_rabbitmq_defaults(self):
        settings = Settings()
        assert settings.rabbitmq_url == "amqp://guest:guest@localhost:5672//"
        assert settings.celery_broker_url == "amqp://guest:guest@localhost:5672//"
        assert settings.celery_result_backend == "rpc://"

    def test_baidu_maps_defaults(self):
        settings = Settings()
        assert settings.baidu_maps_api_key == ""
        assert settings.baidu_maps_enabled is False

    def test_override_via_init(self):
        """Settings can be overridden via constructor kwargs."""
        settings = Settings(
            mongodb_url="mongodb://custom:27017",
            mongodb_db_name="custom_db",
            debug=True,
        )
        assert settings.mongodb_url == "mongodb://custom:27017"
        assert settings.mongodb_db_name == "custom_db"
        assert settings.debug is True

    def test_extra_fields_ignored(self):
        """Extra env vars should be ignored (extra='ignore')."""
        settings = Settings(
            unknown_field="should_be_ignored",
            another_random="value",
        )
        # Should not raise; should just ignore unknown fields
        assert settings.app_name == "MobiBox API"

    def test_loads_from_env(self):
        """Settings should load from environment variables."""
        with patch.dict(os.environ, {
            "MONGODB_URL": "mongodb://env-test:27017",
            "MONGODB_DB_NAME": "env_test_db",
            "APP_NAME": "Test App",
        }, clear=False):
            settings = Settings()
            assert settings.mongodb_url == "mongodb://env-test:27017"
            assert settings.mongodb_db_name == "env_test_db"
            assert settings.app_name == "Test App"


class TestLLMSettings:
    """Test the LLMSettings class for OpenRouter integration."""

    def test_defaults(self):
        settings = LLMSettings()
        assert settings.openrouter_api_key == ""
        assert settings.openrouter_base_url == "https://openrouter.ai/api/v1"
        assert "qwen" in settings.openrouter_model.lower()
        assert settings.openrouter_site_url == "http://localhost:8001"
        assert settings.openrouter_app_name == "MobiBox"
        assert settings.default_temperature == 0.1

    def test_empty_api_key_defaults(self):
        """LLM settings should work with empty API key (no crash)."""
        settings = LLMSettings()
        assert settings.openrouter_api_key == ""

    def test_override_via_init(self):
        settings = LLMSettings(
            openrouter_api_key="sk-test-key",
            default_temperature=0.5,
        )
        assert settings.openrouter_api_key == "sk-test-key"
        assert settings.default_temperature == 0.5

    def test_azure_legacy_defaults(self):
        settings = LLMSettings()
        assert settings.azure_openai_api_key == ""
        assert "hkust" in settings.azure_openai_endpoint

    def test_loads_from_env(self):
        with patch.dict(os.environ, {
            "OPENROUTER_API_KEY": "sk-env-key",
            "DEFAULT_TEMPERATURE": "0.3",
        }, clear=False):
            settings = LLMSettings()
            assert settings.openrouter_api_key == "sk-env-key"
            assert settings.default_temperature == 0.3


class TestCachedGetters:
    """Test the @lru_cache-decorated getter functions."""

    def test_get_settings_returns_instance(self):
        settings = get_settings()
        assert isinstance(settings, Settings)

    def test_get_settings_cached(self):
        """Repeated calls should return the same instance (lru_cache)."""
        s1 = get_settings()
        s2 = get_settings()
        assert s1 is s2

    def test_get_llm_settings_returns_instance(self):
        settings = get_llm_settings()
        assert isinstance(settings, LLMSettings)

    def test_get_llm_settings_cached(self):
        s1 = get_llm_settings()
        s2 = get_llm_settings()
        assert s1 is s2

    def test_settings_and_llm_settings_are_different_classes(self):
        """Settings and LLMSettings should be separate singletons."""
        s = get_settings()
        llm_s = get_llm_settings()
        assert type(s) is not type(llm_s)
