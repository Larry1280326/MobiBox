"""Factory for Intervention test data."""

from datetime import datetime, timezone


def make_intervention(
    user: str = "test_user",
    intervention_type: str = "movement_reminder",
    message: str = "You've been sitting for 45 minutes. Time to stand up and stretch!",
    priority: str = "medium",
    category: str = "physical",
    **overrides,
) -> dict:
    """Generate an intervention entry matching the interventions collection schema.

    Args:
        user: User identifier.
        intervention_type: Type of intervention.
        message: Intervention message content.
        priority: Priority level ("low", "medium", "high").
        category: Intervention category ("physical", "mental", "social", "digital_wellbeing").
        **overrides: Additional field overrides.

    Returns:
        dict matching the interventions MongoDB document schema.
    """
    data = {
        "user": user,
        "intervention_type": intervention_type,
        "message": message,
        "priority": priority,
        "category": category,
        "start_timestamp": datetime.now(timezone.utc),
        "end_timestamp": datetime.now(timezone.utc),
        "timestamp": datetime.now(timezone.utc),
        "intervention_content": message,  # Some code paths use this field
    }
    data.update(overrides)
    return data


def make_intervention_response(
    id: str = "507f1f77bcf86cd799439011",
    message: str = None,
    **overrides,
) -> dict:
    """Generate an intervention API response matching InterventionResponse schema.

    Args:
        id: MongoDB ObjectId hex string.
        message: Intervention text content.
        **overrides: Additional field overrides.

    Returns:
        dict matching InterventionResponse schema.
    """
    if message is None:
        message = (
            "You've been sitting for 45 minutes. "
            "Time to stand up and stretch! "
            "A short walk can boost your energy and focus."
        )

    from datetime import datetime, timezone

    now = datetime.now(timezone.utc)

    data = {
        "status": "success",
        "data": {
            "id": id,
            "intervention_content": message,
            "start_timestamp": now,
            "end_timestamp": now,
            "generation_timestamp": now,
        },
    }
    data.update(overrides)
    return data


# Common intervention types for testing
VALID_INTERVENTION_TYPES = [
    "movement_reminder",
    "hydration_reminder",
    "screen_break",
    "posture_reminder",
    "social_encouragement",
    "sleep_reminder",
    "exercise_suggestion",
]

VALID_PRIORITIES = ["low", "medium", "high"]
VALID_CATEGORIES = ["physical", "mental", "social", "digital_wellbeing"]


def make_intervention_feedback(
    user: str = "test_user",
    intervention_id: str = "507f1f77bcf86cd799439011",
    feedback: str = "This was helpful, thanks!",
    mc1: str = "yes",
    mc2: str = "4",
    mc3: str = "3",
    mc4: str = "5",
    mc5: str = "4",
    mc6: str = "3",
    **overrides,
) -> dict:
    """Generate intervention feedback matching InterventionFeedbackRequest schema.

    Args:
        user: User identifier.
        intervention_id: MongoDB ObjectId of the intervention.
        feedback: Feedback text.
        mc1-mc6: Multiple choice responses.
        **overrides: Additional field overrides.

    Returns:
        dict matching InterventionFeedbackRequest schema.
    """
    data = {
        "user": user,
        "intervention_id": intervention_id,
        "feedback": feedback,
        "mc1": mc1,
        "mc2": mc2,
        "mc3": mc3,
        "mc4": mc4,
        "mc5": mc5,
        "mc6": mc6,
    }
    data.update(overrides)
    return data


def make_log_feedback(
    user: str = "test_user",
    summary_logs_id: str = "507f1f77bcf86cd799439011",
    q1: str = "4",
    q2: str = "yes",
    q2_preference: str = None,
    ground_truth: str = "I was walking around campus and using WeChat.",
    suggestions: str = "Could include more specific location info.",
    **overrides,
) -> dict:
    """Generate summary log feedback matching SummaryLogFeedbackRequest schema.

    Args:
        user: User identifier.
        summary_logs_id: MongoDB ObjectId of the summary log.
        q1: Accuracy score (0-5).
        q2: Content match preference ("yes"/"no").
        q2_preference: Comma-separated preference categories when q2 is "no".
        ground_truth: User's actual activity.
        suggestions: Optimization suggestions.
        **overrides: Additional field overrides.

    Returns:
        dict matching SummaryLogFeedbackRequest schema.
    """
    data = {
        "user": user,
        "summary_logs_id": summary_logs_id,
        "q1": q1,
        "q2": q2,
        "q2_preference": q2_preference,
        "ground_truth": ground_truth,
        "suggestions": suggestions,
    }
    data.update(overrides)
    return data
