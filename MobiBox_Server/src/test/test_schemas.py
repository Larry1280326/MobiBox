"""Tests for Pydantic schema validation across all endpoints.

Covers: register, upload, query, HAR, atomic activities, and LLM output schemas.
"""

import pytest
from pydantic import ValidationError

from src.register.schemas import RegisterRequest
from src.upload.schemas import DocumentItem, DocumentUploadRequest, IMUItem, IMUUploadRequest
from src.query.schemas import (
    SummaryLogRequest,
    SummaryLogResponse,
    InterventionRequest,
    InterventionResponse,
    InterventionFeedbackRequest,
    SummaryLogFeedbackRequest,
    AtomicActivitiesRequest,
    AtomicActivitiesData,
)
from src.celery_app.schemas.har_schemas import HARLabel, IMUWindow
from src.celery_app.schemas.atomic_schemas import (
    AtomicActivity,
    AtomicActivityResult,
)
from src.celery_app.services.summary_service import SummaryOutput
from src.celery_app.services.intervention_service import InterventionOutput

from src.test.factories.upload_factory import make_document_item
from src.test.factories.imu_factory import make_imu_item
from src.test.factories.har_factory import make_har_label
from src.test.factories.atomic_factory import make_atomic_activity
from src.test.factories.summary_factory import make_summary_log_response
from src.test.factories.intervention_factory import (
    make_intervention_response,
    make_intervention_feedback,
    make_log_feedback,
)


# ── Register Schemas ────────────────────────────────────────────────────


class TestRegisterRequest:
    def test_valid_request(self):
        req = RegisterRequest(name="test_user")
        assert req.name == "test_user"

    def test_empty_name_rejected(self):
        with pytest.raises(ValidationError):
            RegisterRequest(name="")

    def test_missing_name_rejected(self):
        with pytest.raises(ValidationError):
            RegisterRequest()


# ── Upload Schemas ──────────────────────────────────────────────────────


class TestDocumentItem:
    def test_valid_item_minimal(self):
        item = DocumentItem(user="test_user")
        assert item.user == "test_user"
        assert item.battery is None

    def test_valid_item_full(self, mongodb_mock):
        data = make_document_item(user="full_user")
        item = DocumentItem(**data)
        assert item.user == "full_user"
        assert item.battery is not None
        assert item.gpsLat is not None

    def test_missing_user_rejected(self):
        with pytest.raises(ValidationError):
            DocumentItem()

    def test_extra_fields_ignored(self):
        """Extra fields should be ignored per model config."""
        item = DocumentItem(user="test", extra_unknown_field="should_be_ignored")
        assert item.user == "test"
        assert not hasattr(item, "extra_unknown_field")

    def test_optional_fields_default_to_none(self):
        item = DocumentItem(user="test")
        assert item.volume is None
        assert item.screen_on_ratio is None
        assert item.wifi_connected is None


class TestDocumentUploadRequest:
    def test_valid_single_item(self):
        req = DocumentUploadRequest(items=[DocumentItem(user="test")])
        assert len(req.items) == 1

    def test_valid_multiple_items(self):
        items = [DocumentItem(user=f"user_{i}") for i in range(5)]
        req = DocumentUploadRequest(items=items)
        assert len(req.items) == 5

    def test_empty_items_rejected(self):
        with pytest.raises(ValidationError):
            DocumentUploadRequest(items=[])

    def test_missing_items_rejected(self):
        with pytest.raises(ValidationError):
            DocumentUploadRequest()


class TestIMUItem:
    def test_valid_item_minimal(self):
        item = IMUItem(user="test_user")
        assert item.user == "test_user"
        assert item.acc_Z is None

    def test_valid_item_full(self):
        data = make_imu_item(user="imu_user")
        item = IMUItem(**data)
        assert item.user == "imu_user"
        assert item.acc_X is not None
        assert item.gyro_Z is not None

    def test_missing_user_rejected(self):
        with pytest.raises(ValidationError):
            IMUItem()


class TestIMUUploadRequest:
    def test_valid_batch(self):
        req = IMUUploadRequest(items=[IMUItem(user="test") for _ in range(50)])
        assert len(req.items) == 50

    def test_empty_items_rejected(self):
        with pytest.raises(ValidationError):
            IMUUploadRequest(items=[])


# ── Query Schemas ───────────────────────────────────────────────────────


class TestSummaryLogRequest:
    def test_valid_hourly_request(self):
        req = SummaryLogRequest(user="test", log_type="hourly")
        assert req.log_type == "hourly"

    def test_valid_daily_request(self):
        req = SummaryLogRequest(user="test", log_type="daily")
        assert req.log_type == "daily"

    def test_valid_request_with_last_log_id(self):
        req = SummaryLogRequest(user="test", log_type="hourly", last_log_id="507f1f77bcf86cd799439011")
        assert req.last_log_id == "507f1f77bcf86cd799439011"

    def test_invalid_log_type_rejected(self):
        with pytest.raises(ValidationError):
            SummaryLogRequest(user="test", log_type="weekly")

    def test_empty_log_type_rejected(self):
        with pytest.raises(ValidationError):
            SummaryLogRequest(user="test", log_type="")

    def test_empty_user_rejected(self):
        with pytest.raises(ValidationError):
            SummaryLogRequest(user="", log_type="hourly")


class TestSummaryLogResponse:
    def test_success_response_with_data(self):
        resp_data = make_summary_log_response()
        resp = SummaryLogResponse(**resp_data)
        assert resp.status == "success"
        assert resp.data is not None
        assert resp.has_new_log is True

    def test_polling_no_new_log(self):
        resp = SummaryLogResponse(status="success", data=None, has_new_log=False)
        assert resp.has_new_log is False
        assert resp.data is None

    def test_daily_log_includes_date(self):
        resp = SummaryLogResponse(
            status="success",
            data=None,
            has_new_log=True,
            date="2026-08-07",
        )
        assert resp.date == "2026-08-07"


class TestInterventionRequest:
    def test_valid_request(self):
        req = InterventionRequest(user="test")
        assert req.user == "test"

    def test_empty_user_rejected(self):
        with pytest.raises(ValidationError):
            InterventionRequest(user="")


class TestInterventionResponse:
    def test_success_response_with_data(self):
        resp_data = make_intervention_response()
        resp = InterventionResponse(**resp_data)
        assert resp.status == "success"
        assert resp.data is not None

    def test_null_data_ok(self):
        resp = InterventionResponse(status="success", data=None)
        assert resp.data is None


class TestInterventionFeedbackRequest:
    def test_minimal_valid(self):
        req = InterventionFeedbackRequest(
            user="test",
            intervention_id="507f1f77bcf86cd799439011",
            feedback="Great suggestion!",
        )
        assert req.feedback == "Great suggestion!"
        assert req.mc1 is None

    def test_full_valid(self):
        data = make_intervention_feedback()
        req = InterventionFeedbackRequest(**data)
        assert req.mc1 == "yes"
        assert req.mc6 == "3"

    def test_missing_required_fields_rejected(self):
        with pytest.raises(ValidationError):
            InterventionFeedbackRequest(user="test")


class TestSummaryLogFeedbackRequest:
    def test_minimal_valid(self):
        req = SummaryLogFeedbackRequest(
            user="test",
            summary_logs_id="507f1f77bcf86cd799439011",
        )
        assert req.user == "test"

    def test_full_valid(self):
        data = make_log_feedback()
        req = SummaryLogFeedbackRequest(**data)
        assert req.q1 == "4"
        assert req.q2 == "yes"
        assert req.ground_truth is not None


class TestAtomicActivitiesRequest:
    def test_valid(self):
        req = AtomicActivitiesRequest(user="test", duration=3600)
        assert req.duration == 3600

    def test_default_duration(self):
        req = AtomicActivitiesRequest(user="test")
        assert req.duration == 0

    def test_negative_duration_rejected(self):
        with pytest.raises(ValidationError):
            AtomicActivitiesRequest(user="test", duration=-1)


class TestAtomicActivitiesData:
    def test_default_empty_lists(self):
        data = AtomicActivitiesData()
        assert data.sport == []
        assert data.appCategory == []
        assert data.location == []

    def test_with_data(self):
        data = AtomicActivitiesData(
            sport=["walking", "running"],
            appCategory=["social_media"],
            location=["HKUST Campus"],
            movement=["walking"],
            stepCategory=["moderate_walking"],
            phoneCategory=["active_use"],
        )
        assert len(data.sport) == 2
        assert data.location == ["HKUST Campus"]


# ── HAR Schemas ─────────────────────────────────────────────────────────


class TestHARLabel:
    def test_valid_label(self):
        data = make_har_label()
        label = HARLabel(**data)
        assert label.user == "test_user"
        assert label.label == "walking"
        assert label.source == "mock_har"

    def test_missing_user_rejected(self):
        with pytest.raises(ValidationError):
            HARLabel(label="walking", source="mock_har")

    def test_default_source(self):
        label = HARLabel(user="test_user", label="stationary")
        assert label.source == "mock_har"


class TestIMUWindow:
    def test_valid_window(self):
        from datetime import datetime, timezone
        now = datetime.now(timezone.utc)
        window = IMUWindow(
            user="test",
            data=[{"acc_X": 0.1, "acc_Y": 0.2, "acc_Z": 9.8}],
            start_time=now,
            end_time=now,
        )
        assert window.user == "test"
        assert len(window.data) == 1


# ── Atomic Activity Schemas ────────────────────────────────────────────


class TestAtomicActivity:
    def test_valid_minimal(self):
        activity = AtomicActivity(user="test", timestamp="2026-08-07T00:00:00Z")
        assert activity.user == "test"
        assert activity.har_label is None

    def test_valid_full(self):
        data = make_atomic_activity()
        activity = AtomicActivity(**data)
        assert activity.har_label == "walking"
        assert activity.app_category == "social_media"
        assert activity.movement_label == "walking"

    def test_all_dimensions_optional(self):
        """All 7 dimensions should be optional."""
        activity = AtomicActivity(user="test", timestamp="2026-08-07T00:00:00Z")
        assert activity.har_label is None
        assert activity.app_category is None
        assert activity.app_name is None
        assert activity.step_label is None
        assert activity.phone_usage is None
        assert activity.social_label is None
        assert activity.movement_label is None
        assert activity.location_label is None


class TestAtomicActivityResult:
    def test_success_result(self):
        result = AtomicActivityResult(
            user="test",
            success=True,
            activity=AtomicActivity(user="test", timestamp="2026-08-07T00:00:00Z"),
        )
        assert result.success is True
        assert result.error is None

    def test_failure_result(self):
        result = AtomicActivityResult(
            user="test",
            success=False,
            error="Failed to generate activity",
        )
        assert result.success is False
        assert result.error == "Failed to generate activity"


# ── LLM Output Schemas ─────────────────────────────────────────────────


class TestSummaryOutput:
    def test_valid_output(self):
        output = SummaryOutput(
            title="Test Hour",
            summary="User was walking.",
            highlights=["Walked 30 min"],
            recommendations=["Keep it up!"],
        )
        assert output.title == "Test Hour"
        assert len(output.highlights) == 1

    def test_missing_required_fields_rejected(self):
        with pytest.raises(ValidationError):
            SummaryOutput(title="Test")

    def test_empty_highlights_ok(self):
        output = SummaryOutput(
            title="Test",
            summary="Summary text.",
            highlights=[],
            recommendations=[],
        )
        assert output.highlights == []


class TestInterventionOutput:
    def test_valid_output(self):
        output = InterventionOutput(
            intervention_type="movement_reminder",
            message="Stand up and stretch!",
            priority="medium",
            category="physical",
        )
        assert output.priority == "medium"
        assert output.category == "physical"

    def test_missing_required_rejected(self):
        with pytest.raises(ValidationError):
            InterventionOutput(message="Test")
