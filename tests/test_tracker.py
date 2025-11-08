from __future__ import annotations

from tinydesk_tracker.tracker import TinyDeskTracker


def test_update_historical_data(database, settings):
    tracker = TinyDeskTracker(database, settings=settings)
    videos = [
        {"videoId": "v1", "title": "Video 1", "viewCount": 100, "publishedAt": "2020-01-01T00:00:00Z"},
        {"videoId": "v2", "title": "Video 2", "viewCount": 200, "publishedAt": "2020-01-02T00:00:00Z"},
    ]

    tracker.update_historical_data(videos)

    top = tracker.get_top_videos()
    assert len(top) == 2
    assert top[0]["videoId"] == "v2"

    metadata = tracker.get_metadata()
    assert metadata["totalVideos"] == 2
    assert metadata["lastUpdate"] > 0


def test_fetch_all_videos(monkeypatch, database, settings):
    class StubAPI:
        def get_playlist_videos_by_id(self, playlist_id, page_token=None):
            assert playlist_id == settings.tiny_desk_playlist_id
            if page_token is None:
                return {
                    "items": [
                        {"snippet": {"resourceId": {"videoId": "abc"}, "title": "ignored"}},
                        {"snippet": {"resourceId": {"videoId": "def"}, "title": "ignored"}},
                    ],
                    "nextPageToken": None,
                }
            return {"items": []}

        def get_video_statistics(self, video_ids):
            return {
                "items": [
                    {
                        "id": video_id,
                        "snippet": {"title": f"Title {video_id}", "publishedAt": "2020-01-01T00:00:00Z"},
                        "statistics": {"viewCount": "123"},
                    }
                    for video_id in video_ids
                ]
            }

    monkeypatch.setattr("tinydesk_tracker.tracker.time.sleep", lambda *_: None)
    tracker = TinyDeskTracker(database, settings=settings, api_client=StubAPI())

    videos = tracker.fetch_all_videos()
    assert len(videos) == 2
    assert {video["videoId"] for video in videos} == {"abc", "def"}
    assert videos[0]["title"].startswith("Title")
