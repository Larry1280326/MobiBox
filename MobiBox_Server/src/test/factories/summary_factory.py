"""Factory for Summary Log test data."""

from datetime import datetime, timezone


def make_summary_log(
    user: str = "test_user",
    log_type: str = "hourly",
    title: str = "Active Hour - Mixed Activities",
    summary: str = "User showed moderate physical activity with frequent phone use. "
    "Spent 30 minutes walking and 15 minutes on social media.",
    highlights: list[str] = None,
    recommendations: list[str] = None,
    **overrides,
) -> dict:
    """Generate a summary log entry matching the summary_logs collection schema.

    Args:
        user: User identifier.
        log_type: Type of summary ("hourly" or "daily").
        title: Summary title.
        summary: Summary text content.
        highlights: List of key highlights.
        recommendations: List of health recommendations.
        **overrides: Additional field overrides.

    Returns:
        dict matching the summary_logs MongoDB document schema.
    """
    if highlights is None:
        highlights = [
            "30 minutes of walking detected",
            "High social media usage (WeChat, Instagram)",
            "Phone usage decreased in the last hour",
        ]
    if recommendations is None:
        recommendations = [
            "Consider taking a short walk every hour",
            "Try reducing screen time before bed",
            "Maintain good posture during extended phone use",
        ]

    data = {
        "user": user,
        "log_type": log_type,
        "summary": {
            "title": title,
            "summary": summary,
            "highlights": highlights,
            "recommendations": recommendations,
        },
        "start_timestamp": datetime.now(timezone.utc),
        "end_timestamp": datetime.now(timezone.utc),
        "timestamp": datetime.now(timezone.utc),
    }
    data.update(overrides)
    return data


def make_summary_log_response(
    id: str = "507f1f77bcf86cd799439011",
    log_content: str = None,
    has_new_log: bool = True,
    log_type: str = "hourly",
    **overrides,
) -> dict:
    """Generate a summary log API response matching SummaryLogResponse schema.

    Args:
        id: MongoDB ObjectId hex string.
        log_content: Summary log text content.
        has_new_log: Whether there's new content.
        log_type: "hourly" or "daily".
        **overrides: Additional field overrides.

    Returns:
        dict matching SummaryLogResponse schema.
    """
    if log_content is None:
        log_content = (
            "Active Hour Summary:\n"
            "- 30 min walking\n"
            "- 15 min social media\n"
            "- 10 min productivity apps\n"
            "Overall activity level: moderate"
        )

    from datetime import datetime, timezone

    now = datetime.now(timezone.utc)

    data = {
        "status": "success",
        "data": {
            "id": id,
            "log_content": log_content,
            "start_timestamp": now,
            "end_timestamp": now,
            "generation_timestamp": now,
        },
        "has_new_log": has_new_log,
        "date": "2026-08-07" if log_type == "daily" else None,
    }
    data.update(overrides)
    return data


def make_no_new_log_response(log_type: str = "hourly") -> dict:
    """Generate a response indicating no new log is available (polling).

    Args:
        log_type: Type of log ("hourly" or "daily").

    Returns:
        dict matching SummaryLogResponse with has_new_log=False and data=None.
    """
    return {
        "status": "success",
        "data": None,
        "has_new_log": False,
        "date": None,
    }
