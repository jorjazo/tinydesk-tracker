from __future__ import annotations

import itertools
from pathlib import Path
from typing import Callable, Dict

import pytest

from tinydesk_tracker.config import Settings
from tinydesk_tracker.database import Database


@pytest.fixture
def settings_factory(tmp_path: Path) -> Callable[..., Settings]:
    counter = itertools.count()

    def _factory(**overrides) -> Settings:
        db_file = tmp_path / f"tinydesk_{next(counter)}.db"
        base: Dict[str, object] = {
            "youtube_api_key": "fake-key",
            "npr_music_channel_id": "channel",
            "db_file": db_file,
            "database_url": None,
            "update_interval_hours": 6.0,
            "max_results_per_request": 50,
            "total_videos_to_fetch": 100,
            "tiny_desk_playlist_id": "PLAYLIST",
            "update_schedule": "",
            "update_cron": "",
            "update_lock_ttl_seconds": 60,
        }
        base.update(overrides)
        return Settings(**base)

    return _factory


@pytest.fixture
def settings(settings_factory: Callable[..., Settings]) -> Settings:
    return settings_factory()


@pytest.fixture
def database(settings: Settings) -> Database:
    db = Database(settings)
    try:
        yield db
    finally:
        db.close()
