"""
Scheduling helpers for the Tiny Desk tracker.
"""

from __future__ import annotations

import time
from datetime import datetime, timedelta
from threading import Thread
from typing import Callable, Iterable, Optional

import schedule
from croniter import croniter

from .config import Settings
from .tracker import TinyDeskTracker


def normalize_cron_expression(expr: str) -> Optional[str]:
    """Normalize cron expressions with 3-6 fields to the standard 5-field format."""
    parts = [p for p in expr.split() if p]
    if len(parts) < 3:
        return None
    if len(parts) == 3:
        parts += ["*", "*"]
    elif len(parts) == 4:
        parts += ["*"]
    elif len(parts) in (5, 6):
        parts = parts[:5]
    else:
        return None
    return " ".join(parts)


def compute_next_update_timestamp(settings: Settings, last_update_ts: int, now: Optional[datetime] = None) -> int:
    """Compute the next update timestamp based on cron, schedule or interval settings."""
    reference = now or datetime.now()
    cron_expr = normalize_cron_expression(settings.update_cron) if settings.update_cron else None
    if cron_expr:
        try:
            next_dt = croniter(cron_expr, reference).get_next(datetime)
            return int(next_dt.timestamp())
        except Exception:
            pass

    schedule_candidates = list(_parse_update_schedule(settings.update_schedule, reference))
    if schedule_candidates:
        next_dt = min(schedule_candidates)
        return int(next_dt.timestamp())

    base_dt = datetime.fromtimestamp(last_update_ts) if last_update_ts else reference
    next_dt = base_dt + timedelta(hours=settings.update_interval_hours)
    return int(next_dt.timestamp())


def _parse_update_schedule(schedule_str: str, now: datetime) -> Iterable[datetime]:
    times = [token.strip() for token in schedule_str.split(",") if token.strip()]
    for entry in times:
        try:
            hour, minute = entry.split(":")
            candidate = now.replace(hour=int(hour), minute=int(minute), second=0, microsecond=0)
            if candidate <= now:
                candidate = candidate + timedelta(days=1)
            yield candidate
        except Exception:
            continue


def start_cron_scheduler(cron_str: str, tracker: TinyDeskTracker) -> Thread:
    """Start background thread that triggers updates based on the cron pattern."""

    def _loop():
        try:
            base = datetime.now()
            iterator = croniter(cron_str, base)
            while True:
                next_dt = iterator.get_next(datetime)
                delay = max(0, (next_dt - datetime.now()).total_seconds())
                print(f"Next scheduled update at {next_dt.isoformat(timespec='seconds')}")
                time.sleep(delay)
                tracker.update()
                base = datetime.now()
                iterator = croniter(cron_str, base)
        except Exception as exc:  # pragma: no cover - diagnostic logging
            print(f"✗ Cron scheduler error: {exc}")

    thread = Thread(target=_loop, daemon=True)
    thread.start()
    return thread


def schedule_updates(settings: Settings, tracker: TinyDeskTracker, scheduler_module=schedule) -> None:
    """Configure updates using cron emulation, fixed schedule or interval-based scheduling."""
    cron_expr = normalize_cron_expression(settings.update_cron) if settings.update_cron else None
    if cron_expr:
        start_cron_scheduler(cron_expr, tracker)
        print(f"✓ Cron-based updates enabled: '{cron_expr}' (container local time)")
        return

    schedule_entries = [token.strip() for token in settings.update_schedule.split(",") if token.strip()]
    valid_schedule = list(_parse_update_schedule(settings.update_schedule, datetime.now()))
    if valid_schedule:
        for entry in schedule_entries:
            try:
                scheduler_module.every().day.at(entry).do(tracker.update)
            except Exception:
                print(f"✗ Skipping invalid time in UPDATE_SCHEDULE: '{entry}' (expected HH:MM)")
        if scheduler_module.jobs:
            print(f"✓ Scheduled daily updates at: {', '.join(schedule_entries)} (container local time)")
            return

    scheduler_module.every(settings.update_interval_hours).hours.do(tracker.update)
    print(f"✓ Scheduled updates every {settings.update_interval_hours} hours")
