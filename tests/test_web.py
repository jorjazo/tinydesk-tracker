from __future__ import annotations

import time

from tinydesk_tracker.web import create_app


def test_api_top_returns_tracked_videos(database, settings):
    database.save_video("vid", "Title", 100, int(time.time()), "2020-01-01T00:00:00Z")
    database.set_metadata("lastUpdate", str(int(time.time())))
    database.set_metadata("totalVideos", "1")

    from tinydesk_tracker.tracker import TinyDeskTracker

    tracker = TinyDeskTracker(database, settings=settings)
    app = create_app(settings, tracker, database)
    client = app.test_client()

    response = client.get("/api/top")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["total"] == 1
    assert payload["videos"][0]["videoId"] == "vid"


def test_api_status_returns_metadata(database, settings):
    now_ts = int(time.time())
    database.set_metadata("lastUpdate", str(now_ts))
    database.set_metadata("totalVideos", "0")

    from tinydesk_tracker.tracker import TinyDeskTracker

    tracker = TinyDeskTracker(database, settings=settings)
    app = create_app(settings, tracker, database)
    client = app.test_client()

    response = client.get("/api/status")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["status"] == "online"
    assert payload["lastUpdate"] == now_ts
