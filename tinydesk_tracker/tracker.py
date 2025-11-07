"""
Core domain logic for fetching Tiny Desk video data and persisting it.
"""

from __future__ import annotations

import sys
import time
from datetime import datetime
from typing import Dict, List, Optional

from .config import Settings
from .database import Database
from .youtube import YouTubeAPIClient


class TinyDeskTracker:
    """Main coordination class that orchestrates fetching, persistence and metadata."""

    def __init__(
        self,
        database: Database,
        settings: Settings,
        api_client: Optional[YouTubeAPIClient] = None,
    ):
        if not settings.youtube_api_key:
            raise ValueError("YouTube API key is required")

        self.db = database
        self.settings = settings
        self.api = api_client or YouTubeAPIClient(
            settings.youtube_api_key, max_results_per_request=settings.max_results_per_request
        )

    def fetch_all_videos(self) -> List[Dict]:
        """Fetch all Tiny Desk videos from the configured playlist."""
        all_videos: List[Dict] = []
        page_token: Optional[str] = None
        page_num = 1

        print("\n" + "=" * 50)
        print("Fetching Tiny Desk concerts from official playlist...")
        print(f"Playlist ID: {self.settings.tiny_desk_playlist_id}")
        print("=" * 50)

        while True:
            print(f"\nFetching page {page_num}...")
            playlist_results = self.api.get_playlist_videos_by_id(self.settings.tiny_desk_playlist_id, page_token)
            items = playlist_results.get("items", [])

            if not items:
                print("No more results available")
                break

            video_ids = [
                item["snippet"]["resourceId"]["videoId"]
                for item in items
                if item.get("snippet", {}).get("resourceId", {}).get("videoId")
            ]
            print(f"Found {len(video_ids)} videos on this page")

            if not video_ids:
                page_token = playlist_results.get("nextPageToken")
                if not page_token:
                    print("No more pages available")
                    break
                page_num += 1
                time.sleep(0.5)
                continue

            stats_result = self.api.get_video_statistics(video_ids)
            stats_items = stats_result.get("items", [])

            for item in stats_items:
                snippet = item.get("snippet", {})
                statistics = item.get("statistics", {})
                title = snippet.get("title", "")
                all_videos.append(
                    {
                        "videoId": item["id"],
                        "title": title,
                        "viewCount": int(statistics.get("viewCount", 0)),
                        "publishedAt": snippet.get("publishedAt", ""),
                    }
                )

            print(f"✓ Fetched stats for {len(stats_items)} videos")
            print(f"  Total Tiny Desk videos collected so far: {len(all_videos)}")
            sys.stdout.flush()

            page_token = playlist_results.get("nextPageToken")
            if not page_token:
                print("No more pages available")
                break

            page_num += 1
            time.sleep(0.5)

        print("\n" + "=" * 50)
        print(f"Total Tiny Desk playlist videos fetched: {len(all_videos)}")
        print("=" * 50 + "\n")

        return all_videos

    def update_historical_data(self, videos: List[Dict]) -> None:
        """Persist fetched videos and update metadata."""
        timestamp = int(time.time())

        print("Saving to database...")
        for idx, video in enumerate(videos, 1):
            video_id = video["videoId"]
            title = video["title"]
            view_count = video["viewCount"]
            published_at = video.get("publishedAt")

            self.db.save_video(video_id, title, view_count, timestamp, published_at)

            if idx % 10 == 0:
                print(f"  Saved {idx}/{len(videos)} videos...")

        self.db.set_metadata("lastUpdate", str(timestamp))
        self.db.set_metadata("totalVideos", str(len(videos)))

        print(f"✓ All {len(videos)} videos saved to database")

    def update(self) -> None:
        """Perform a complete fetch & save cycle."""
        try:
            print(f"\n[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Starting update...")
            lock_handle = self.db.acquire_update_lock()
            if not lock_handle:
                print("↷ Skipping update: lock not acquired (another instance is updating)")
                return
            try:
                videos = self.fetch_all_videos()
                self.update_historical_data(videos)
            finally:
                self.db.release_update_lock(lock_handle)

            print(f"✓ Update completed successfully at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

        except Exception as exc:  # pragma: no cover - logged for diagnostics
            print(f"✗ Error during update: {exc}")
            import traceback

            traceback.print_exc()

    def get_top_videos(self, limit: int = 100) -> List[Dict]:
        return self.db.get_top_videos(limit)

    def get_metadata(self) -> Dict[str, int]:
        metadata = self.db.get_all_metadata()
        return {
            "lastUpdate": int(metadata.get("lastUpdate", 0)),
            "totalVideos": int(metadata.get("totalVideos", 0)),
        }
