"""Factory for LLM response test data.

Provides deterministic, realistic LLM outputs for testing summary generation,
intervention generation, and app category classification. These are used
by patching generate_structured_output and query_llm.
"""

from src.celery_app.services.summary_service import SummaryOutput
from src.celery_app.services.intervention_service import InterventionOutput


def make_summary_output(
    title: str = "Active Hour - Mixed Activities",
    summary: str = None,
    highlights: list[str] = None,
    recommendations: list[str] = None,
    activity: str = "walking",
    app: str = "social media",
) -> SummaryOutput:
    """Generate a SummaryOutput Pydantic model for LLM mock responses.

    Args:
        title: Summary title.
        summary: Detailed summary text.
        highlights: Key activity highlights.
        recommendations: Health recommendations.
        activity: Primary activity to describe.
        app: Primary app category to describe.

    Returns:
        SummaryOutput Pydantic model instance.
    """
    if summary is None:
        summary = (
            f"During this period, the user spent most of their time {activity} "
            f"while primarily using {app}. Physical activity level was moderate "
            f"with regular walking intervals. Phone usage patterns indicate "
            f"active engagement with communication and social applications."
        )
    if highlights is None:
        highlights = [
            f"15 minutes of {activity}",
            f"Primary app category: {app}",
            "Regular step count intervals detected",
            "Location: HKUST Campus area",
        ]
    if recommendations is None:
        recommendations = [
            "Consider taking posture breaks during extended {app} sessions",
            "Maintain current walking activity level",
            "Try to reduce screen brightness during evening hours",
        ]

    return SummaryOutput(
        title=title,
        summary=summary,
        highlights=highlights,
        recommendations=recommendations,
    )


def make_intervention_output(
    intervention_type: str = "movement_reminder",
    message: str = None,
    priority: str = "medium",
    category: str = "physical",
) -> InterventionOutput:
    """Generate an InterventionOutput Pydantic model for LLM mock responses.

    Args:
        intervention_type: Type of intervention.
        message: Intervention message.
        priority: Priority level.
        category: Intervention category.

    Returns:
        InterventionOutput Pydantic model instance.
    """
    if message is None:
        messages = {
            "movement_reminder": (
                "You've been sitting for 45 minutes. "
                "Time to stand up and stretch! A short walk can boost your energy."
            ),
            "screen_break": (
                "You've been staring at your screen for an hour. "
                "Look away for 20 seconds to reduce eye strain."
            ),
            "hydration_reminder": (
                "It's been a while since your last water break. "
                "Stay hydrated for better focus and health!"
            ),
            "sleep_reminder": (
                "It's getting late. Consider winding down your screen time "
                "to prepare for a good night's sleep."
            ),
            "exercise_suggestion": (
                "You've been mostly stationary. "
                "How about a quick 5-minute walk to get your blood flowing?"
            ),
        }
        message = messages.get(intervention_type, "Stay active and healthy!")

    return InterventionOutput(
        intervention_type=intervention_type,
        message=message,
        priority=priority,
        category=category,
    )


def make_app_category_response(
    app_name: str = "com.tencent.mm",
    category: str = "social_media",
    source: str = "memory",
    **overrides,
) -> dict:
    """Generate an app category lookup response.

    Args:
        app_name: App package name or display name.
        category: Category classification.
        source: Lookup source ("memory", "database", "llm").
        **overrides: Additional overrides.

    Returns:
        dict with app_name, category, and source.
    """
    data = {
        "app_name": app_name,
        "category": category,
        "source": source,
    }
    data.update(overrides)
    return data


# Common app → category mappings for testing
COMMON_APP_MAPPINGS = {
    "com.tencent.mm": "social_media",
    "com.tencent.mobileqq": "social_media",
    "com.instagram.android": "social_media",
    "com.google.android.apps.docs": "productivity",
    "com.microsoft.office.word": "productivity",
    "com.android.chrome": "browser",
    "com.google.android.youtube": "entertainment",
    "com.netflix.mediaclient": "entertainment",
    "com.google.android.apps.maps": "navigation",
    "com.google.android.apps.fitness": "health_fitness",
    "com.android.phone": "communication",
}


def make_llm_activity_label(activities: list[str], counts: dict = None) -> str:
    """Generate a mock LLM response for HAR activity labeling.

    Args:
        activities: List of predicted activities.
        counts: Optional dict of activity → count.

    Returns:
        Formatted string simulating an LLM activity analysis response.
    """
    from collections import Counter

    if counts is None:
        counts = dict(Counter(activities))

    most_common = max(counts, key=counts.get)
    return f"Based on the recent HAR data, user was mostly {most_common}. " + ", ".join(
        f"{k}: {v}" for k, v in counts.items()
    )
