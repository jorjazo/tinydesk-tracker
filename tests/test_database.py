from __future__ import annotations

import time


def test_save_video_and_get_top(database):
    timestamp = int(time.time())
    database.save_video("v1", "Video 1", 100, timestamp, "2020-01-01T00:00:00Z")
    database.save_video("v2", "Video 2", 200, timestamp, "2020-01-02T00:00:00Z")

    top = database.get_top_videos()
    assert top[0]["videoId"] == "v2"
    assert top[1]["videoId"] == "v1"

    history = database.get_video_history("v1")
    assert history[0]["viewCount"] == 100


def test_history_is_capped_to_latest_100_entries(database):
    base_ts = int(time.time())
    for offset in range(120):
        database.save_video("vid", "Video", offset, base_ts + offset, "2020-01-01T00:00:00Z")

    history = database.get_video_history("vid")
    assert len(history) == 100
    # Oldest entry retained should correspond to offset 20 (120 - 100)
    assert history[0]["viewCount"] == 20
    assert history[-1]["viewCount"] == 119


def test_metadata_roundtrip(database):
    database.set_metadata("lastUpdate", "123")
    database.set_metadata("totalVideos", "10")

    assert database.get_metadata("lastUpdate") == "123"
    assert database.get_metadata("totalVideos") == "10"
    metadata = database.get_all_metadata()
    assert metadata["lastUpdate"] == "123"
    assert metadata["totalVideos"] == "10"
