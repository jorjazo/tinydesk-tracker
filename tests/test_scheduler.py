from __future__ import annotations

from datetime import datetime, timedelta

from tinydesk_tracker.scheduler import compute_next_update_timestamp, normalize_cron_expression


def test_normalize_cron_expression():
    assert normalize_cron_expression("0 6 1") == "0 6 1 * *"
    assert normalize_cron_expression("0 6 1 1") == "0 6 1 1 *"
    assert normalize_cron_expression("0 6 1 1 * *") == "0 6 1 1 *"
    assert normalize_cron_expression("") is None
    assert normalize_cron_expression("0 6") is None


def test_compute_next_update_timestamp_with_cron(settings_factory):
    settings = settings_factory(update_cron="0 6 * * *")
    now = datetime(2024, 1, 1, 5, 30, 0)
    next_ts = compute_next_update_timestamp(settings, last_update_ts=0, now=now)
    assert datetime.fromtimestamp(next_ts) == datetime(2024, 1, 1, 6, 0, 0)


def test_compute_next_update_timestamp_with_schedule(settings_factory):
    settings = settings_factory(update_cron="", update_schedule="06:00,18:00")
    now = datetime(2024, 1, 1, 19, 0, 0)
    next_ts = compute_next_update_timestamp(settings, last_update_ts=0, now=now)
    assert datetime.fromtimestamp(next_ts) == datetime(2024, 1, 2, 6, 0, 0)


def test_compute_next_update_timestamp_with_interval(settings_factory):
    settings = settings_factory(update_cron="", update_schedule="", update_interval_hours=3)
    last_update = datetime(2024, 1, 1, 1, 0, 0)
    next_ts = compute_next_update_timestamp(
        settings,
        last_update_ts=int(last_update.timestamp()),
        now=datetime(2024, 1, 1, 1, 10, 0),
    )
    assert datetime.fromtimestamp(next_ts) == last_update + timedelta(hours=3)
