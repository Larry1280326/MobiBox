"""Factory for Atomic Activity test data."""

from datetime import datetime, timezone


def make_atomic_activity(
    user: str = "test_user",
    har_label: str = "walking",
    app_category: str = "social_media",
    app_name: str = "com.tencent.mm",
    step_label: str = "moderate_walking",
    phone_usage: str = "active_use",
    social_label: str = "social",
    movement_label: str = "walking",
    location_label: str = "HKUST Campus",
    **overrides,
) -> dict:
    """Generate an atomic activity record matching the AtomicActivity Pydantic schema.

    An atomic activity combines all 7 dimensions of user behavior for a single
    point in time.

    Args:
        user: User identifier.
        har_label: HAR activity label.
        app_category: App category classification.
        app_name: Specific app package name.
        step_label: Step count activity label.
        phone_usage: Phone usage category.
        social_label: Social context label.
        movement_label: Movement pattern label.
        location_label: Location context label.
        **overrides: Additional field overrides.

    Returns:
        dict matching AtomicActivity schema.
    """
    data = {
        "user": user,
        "timestamp": datetime.now(timezone.utc),
        "har_label": har_label,
        "app_category": app_category,
        "app_name": app_name,
        "step_label": step_label,
        "phone_usage": phone_usage,
        "social_label": social_label,
        "movement_label": movement_label,
        "location_label": location_label,
    }
    data.update(overrides)
    return data


def make_atomic_activity_result(
    user: str = "test_user",
    success: bool = True,
    activity: dict = None,
    error: str = None,
    **overrides,
) -> dict:
    """Generate an atomic activity result matching AtomicActivityResult schema.

    Args:
        user: User identifier.
        success: Whether processing succeeded.
        activity: Optional atomic activity dict (auto-generated if success is True).
        error: Optional error message.
        **overrides: Additional field overrides.

    Returns:
        dict matching AtomicActivityResult schema.
    """
    if activity is None and success:
        activity = make_atomic_activity(user=user)

    data = {
        "user": user,
        "success": success,
        "activity": activity,
        "error": error,
    }
    data.update(overrides)
    return data


# Common dimension values for testing
DIMENSION_VALUES = {
    "har_label": ["walking", "running", "stationary", "cycling", "driving"],
    "app_category": [
        "social_media",
        "productivity",
        "entertainment",
        "communication",
        "navigation",
        "health_fitness",
        "education",
    ],
    "step_label": ["no_walking", "light_walking", "moderate_walking", "intense_walking"],
    "phone_usage": ["no_use", "passive_use", "active_use", "intensive_use"],
    "social_label": ["alone", "social", "crowd", "unknown"],
    "movement_label": ["stationary", "walking", "running", "in_vehicle"],
    "location_label": ["HKUST Campus", "Home", "Shopping Mall", "Restaurant"],
}
