"""
HTTP client for interacting with the YouTube Data API v3.
"""

from __future__ import annotations

from typing import Dict, Iterable, Optional

import requests


class YouTubeAPIClient:
    """Simple YouTube Data API client tailored for the tracker."""

    BASE_URL = "https://www.googleapis.com/youtube/v3"
    UPLOADS_PLAYLIST_ID = "UU4eYXhJI4-7wSWc8UNRwD4A"

    def __init__(
        self,
        api_key: str,
        *,
        max_results_per_request: int = 50,
        session: Optional[requests.Session] = None,
    ):
        if not api_key:
            raise ValueError("YouTube API key must not be empty")
        if max_results_per_request <= 0:
            raise ValueError("max_results_per_request must be positive")
        self.api_key = api_key
        self.session = session or requests.Session()
        self.max_results_per_request = max_results_per_request

    def get_playlist_videos(self, page_token: Optional[str] = None) -> Dict:
        return self.get_playlist_videos_by_id(self.UPLOADS_PLAYLIST_ID, page_token)

    def get_playlist_videos_by_id(self, playlist_id: str, page_token: Optional[str] = None) -> Dict:
        params = {
            "part": "snippet",
            "playlistId": str(playlist_id),
            "maxResults": str(self.max_results_per_request),
            "key": str(self.api_key),
        }
        if page_token:
            params["pageToken"] = str(page_token)
        response = self.session.get(f"{self.BASE_URL}/playlistItems", params=params)
        response.raise_for_status()
        return response.json()

    def get_video_statistics(self, video_ids: Iterable[str]) -> Dict:
        ids = list(video_ids)
        if not ids:
            return {"items": []}
        params = {
            "part": "snippet,statistics",
            "id": ",".join(ids),
            "key": str(self.api_key),
        }
        response = self.session.get(f"{self.BASE_URL}/videos", params=params)
        response.raise_for_status()
        return response.json()
