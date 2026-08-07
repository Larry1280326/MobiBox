"""Factory for HAR (Human Activity Recognition) label test data."""

from datetime import datetime, timezone


def make_har_label(
    user: str = "test_user",
    label: str = "walking",
    confidence: float = 0.85,
    source: str = "mock_har",
    **overrides,
) -> dict:
    """Generate a HAR label matching the HARLabel Pydantic schema.

    Args:
        user: User identifier.
        label: HAR activity label (e.g., "walking", "running", "stationary").
        confidence: Confidence score (0.0 to 1.0).
        source: Model source ("mock_har", "ml_model", "selfsup_model").
        **overrides: Additional field overrides.

    Returns:
        dict matching HARLabel schema.
    """
    data = {
        "user": user,
        "label": label,
        "confidence": confidence,
        "timestamp": datetime.now(timezone.utc),
        "source": source,
    }
    data.update(overrides)
    return data


# Valid HAR activity labels
VALID_HAR_LABELS = [
    "walking",
    "running",
    "stationary",
    "climbing_up",
    "climbing_down",
    "cycling",
    "driving",
]


def make_har_label_batch(
    user: str = "test_user",
    count: int = 10,
    labels: list[str] = None,
) -> list[dict]:
    """Generate a batch of HAR labels with varied activities.

    Args:
        user: User identifier.
        count: Number of labels to generate.
        labels: List of activity labels to randomly pick from.

    Returns:
        List of HAR label dicts.
    """
    import random

    if labels is None:
        labels = VALID_HAR_LABELS

    return [
        make_har_label(
            user=user,
            label=random.choice(labels),
            confidence=random.uniform(0.6, 0.99),
        )
        for _ in range(count)
    ]
