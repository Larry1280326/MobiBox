"""Factory for document upload test data."""

import random
from datetime import datetime, timezone


def make_document_item(user: str = "test_user", **overrides) -> dict:
    """Generate a single document item with realistic sensor data.

    Args:
        user: User identifier.
        **overrides: Any field from DocumentItem schema to override.

    Returns:
        dict matching DocumentItem Pydantic schema.
    """
    data = {
        "user": user,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "volume": 80,
        "screen_on_ratio": 0.5,
        "wifi_connected": True,
        "wifi_ssid": "HKUST-WiFi",
        "network_traffic": random.uniform(0.1, 5.0),
        "Rx_traffic": random.uniform(0.05, 2.0),
        "Tx_traffic": random.uniform(0.05, 2.0),
        "stepcount_sensor": random.randint(0, 200),
        "gpsLat": 22.3367 + random.uniform(-0.01, 0.01),
        "gpsLon": 114.2650 + random.uniform(-0.01, 0.01),
        "battery": random.randint(30, 100),
        "current_app": "com.android.chrome",
        "bluetooth_devices": ["AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"],
        "address": "Clear Water Bay, Hong Kong",
        "poi": ["HKUST Library", "Academic Building"],
        "nearbyBluetoothCount": random.randint(1, 5),
        "topBluetoothDevices": ["AA:BB:CC:DD:EE:01"],
    }
    data.update(overrides)
    return data


def make_document_batch(user: str = "test_user", count: int = 10, **overrides) -> dict:
    """Generate a batch of document items for upload.

    Args:
        user: User identifier.
        count: Number of items to generate.
        **overrides: Fields to override in each item.

    Returns:
        dict with "items" key suitable for DocumentUploadRequest.
    """
    return {"items": [make_document_item(user=user, **overrides) for _ in range(count)]}


def make_walking_document(user: str = "test_user") -> dict:
    """Generate a document item representing walking activity."""
    return make_document_item(
        user=user,
        stepcount_sensor=150,
        gpsLat=22.3367 + random.uniform(-0.001, 0.001),
        gpsLon=114.2650 + random.uniform(-0.001, 0.001),
        current_app="com.google.android.apps.fitness",
        screen_on_ratio=0.1,
        volume=40,
        battery=85,
    )


def make_stationary_document(user: str = "test_user") -> dict:
    """Generate a document item representing stationary phone usage."""
    return make_document_item(
        user=user,
        stepcount_sensor=0,
        screen_on_ratio=0.9,
        volume=100,
        current_app="com.tencent.mm",
        battery=60,
        network_traffic=random.uniform(2.0, 10.0),
    )


def make_sleeping_document(user: str = "test_user") -> dict:
    """Generate a document item representing sleeping/low activity."""
    return make_document_item(
        user=user,
        stepcount_sensor=0,
        screen_on_ratio=0.0,
        volume=0,
        current_app=None,
        battery=30,
        network_traffic=0.0,
        wifi_connected=True,
    )
