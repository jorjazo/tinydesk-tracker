from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Optional


@dataclass(frozen=True)
class Settings:
    """Application configuration loaded from the environment."""

    youtube_api_key: str
    npr_music_channel_id: str
    db_file: Path
    database_url: Optional[str]
    update_interval_hours: float
    max_results_per_request: int
    total_videos_to_fetch: int
    tiny_desk_playlist_id: str
    update_schedule: str
    update_cron: str
    update_lock_ttl_seconds: int

    @property
    def has_external_database(self) -> bool:
        return bool(self.database_url)


def load_settings(env: Optional[Mapping[str, str]] = None) -> Settings:
    """Load configuration from the provided environment mapping (defaults to os.environ)."""
    import os

    source = env if env is not None else os.environ

    db_file = Path(source.get("DB_FILE", "./data/tinydesk.db")).expanduser().resolve()
    database_url = source.get("DATABASE_URL") or None

    return Settings(
        youtube_api_key=source.get("YOUTUBE_API_KEY", ""),
        npr_music_channel_id=source.get("NPR_MUSIC_CHANNEL_ID", "UC4eYXhJI4-7wSWc8UNRwD4A"),
        db_file=db_file,
        database_url=database_url,
        update_interval_hours=float(source.get("UPDATE_INTERVAL_HOURS", "6")),
        max_results_per_request=int(source.get("MAX_RESULTS_PER_REQUEST", "50")),
        total_videos_to_fetch=int(source.get("TOTAL_VIDEOS_TO_FETCH", "1000")),
        tiny_desk_playlist_id=source.get("TINY_DESK_PLAYLIST_ID", "PL1B627337ED6F55F0"),
        update_schedule=source.get("UPDATE_SCHEDULE", "").strip(),
        update_cron=source.get("UPDATE_CRON", "*/30 * * * *").strip(),
        update_lock_ttl_seconds=int(source.get("UPDATE_LOCK_TTL_SECONDS", "7200")),
    )
