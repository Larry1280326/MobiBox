"""Factory for IMU upload test data."""

import math
import random
from datetime import datetime, timedelta, timezone


def make_imu_item(user: str = "test_user", timestamp: datetime = None, **overrides) -> dict:
    """Generate a single IMU data point with realistic accelerometer readings.

    Default values simulate a phone resting on a desk (gravity ≈ 9.8 on Z axis).

    Args:
        user: User identifier.
        timestamp: Optional timestamp (auto-generated if None).
        **overrides: Any field from IMUItem schema to override.

    Returns:
        dict matching IMUItem Pydantic schema.
    """
    if timestamp is None:
        timestamp = datetime.now(timezone.utc)

    data = {
        "user": user,
        "timestamp": timestamp.isoformat(),
        "acc_X": random.uniform(-0.5, 0.5),
        "acc_Y": random.uniform(-0.5, 0.5),
        "acc_Z": 9.8 + random.uniform(-0.5, 0.5),
        "gyro_X": random.uniform(-0.1, 0.1),
        "gyro_Y": random.uniform(-0.1, 0.1),
        "gyro_Z": random.uniform(-0.1, 0.1),
        "mag_X": random.uniform(-40, 40),
        "mag_Y": random.uniform(-40, 40),
        "mag_Z": random.uniform(-40, 40),
    }
    data.update(overrides)
    return data


def make_imu_window(
    user: str = "test_user",
    seconds: int = 1,
    sample_rate_hz: int = 50,
    activity: str = "walking",
) -> list[dict]:
    """Generate a window of IMU data at the specified sample rate.

    Args:
        user: User identifier.
        seconds: Duration of the window in seconds.
        sample_rate_hz: Sample rate in Hz (default 50).
        activity: Activity pattern to simulate ("walking", "running", "stationary").

    Returns:
        List of dicts matching IMUItem schema.
    """
    items = []
    base_time = datetime.now(timezone.utc)
    interval_ms = 1000 / sample_rate_hz
    total_samples = seconds * sample_rate_hz

    for i in range(total_samples):
        ts = base_time + timedelta(milliseconds=i * interval_ms)

        if activity == "walking":
            # Walking: periodic acceleration pattern at ~2 Hz
            phase = (i / sample_rate_hz) * 2.0 * math.pi * 2.0  # 2 Hz
            acc_x = 0.3 * math.sin(phase) + random.uniform(-0.2, 0.2)
            acc_y = 0.2 * math.cos(phase) + random.uniform(-0.2, 0.2)
            acc_z = 9.8 + 0.5 * abs(math.sin(phase)) + random.uniform(-0.3, 0.3)
            gyro_z = 0.5 * math.sin(phase) + random.uniform(-0.2, 0.2)
            gyro_x = random.uniform(-0.4, 0.4)
            gyro_y = random.uniform(-0.4, 0.4)
        elif activity == "running":
            # Running: larger amplitude, higher frequency (~3 Hz)
            phase = (i / sample_rate_hz) * 2.0 * math.pi * 3.0
            acc_x = 0.8 * math.sin(phase) + random.uniform(-0.3, 0.3)
            acc_y = 0.5 * math.cos(phase) + random.uniform(-0.3, 0.3)
            acc_z = 9.8 + 1.5 * abs(math.sin(phase)) + random.uniform(-0.5, 0.5)
            gyro_z = 1.0 * math.sin(phase) + random.uniform(-0.3, 0.3)
            gyro_x = random.uniform(-0.8, 0.8)
            gyro_y = random.uniform(-0.8, 0.8)
        else:
            # Stationary: minimal movement, just noise
            acc_x = random.uniform(-0.1, 0.1)
            acc_y = random.uniform(-0.1, 0.1)
            acc_z = 9.8 + random.uniform(-0.1, 0.1)
            gyro_x = random.uniform(-0.05, 0.05)
            gyro_y = random.uniform(-0.05, 0.05)
            gyro_z = random.uniform(-0.05, 0.05)

        items.append(
            make_imu_item(
                user=user,
                timestamp=ts,
                acc_X=round(acc_x, 6),
                acc_Y=round(acc_y, 6),
                acc_Z=round(acc_z, 6),
                gyro_X=round(gyro_x, 6),
                gyro_Y=round(gyro_y, 6),
                gyro_Z=round(gyro_z, 6),
            )
        )

    return items


def make_imu_batch(
    user: str = "test_user",
    seconds: int = 3,
    sample_rate_hz: int = 50,
    activity: str = "walking",
) -> dict:
    """Generate a batch of IMU items ready for upload.

    Args:
        user: User identifier.
        seconds: Duration of data in seconds.
        sample_rate_hz: Sample rate in Hz.
        activity: Activity pattern to simulate.

    Returns:
        dict with "items" key suitable for IMUUploadRequest.
    """
    return {"items": make_imu_window(user, seconds, sample_rate_hz, activity)}
