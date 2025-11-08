"""
Database utilities and persistence logic for the Tiny Desk tracker.
"""

from __future__ import annotations

import hashlib
import socket
import time
from typing import Dict, List, Optional

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

from .config import Settings


class Database:
    """SQLAlchemy powered persistence layer."""

    def __init__(self, settings: Settings, engine: Optional[Engine] = None):
        self.settings = settings
        self._engine: Optional[Engine] = engine
        self._lock_name = "tinydesk_update_lock"

        if not settings.has_external_database:
            settings.db_file.parent.mkdir(parents=True, exist_ok=True)

        self.initialize_db()

    def get_engine(self) -> Engine:
        """Return (and lazily create) the underlying engine."""
        if self._engine is None:
            if self.settings.database_url:
                self._engine = create_engine(
                    self.settings.database_url,
                    future=True,
                    pool_pre_ping=True,
                )
            else:
                sqlite_url = f"sqlite:///{self.settings.db_file}"
                self._engine = create_engine(sqlite_url, future=True)
                with self._engine.connect() as conn:
                    conn.execute(text("PRAGMA journal_mode=WAL"))
                    conn.execute(text("PRAGMA cache_size=-10000"))
                    conn.execute(text("PRAGMA temp_store=MEMORY"))
        return self._engine

    def initialize_db(self) -> None:
        """Create database schema if missing."""
        engine = self.get_engine()
        dialect = engine.dialect.name
        with engine.begin() as conn:
            conn.execute(
                text(
                    """
                    CREATE TABLE IF NOT EXISTS videos (
                        video_id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        current_views INTEGER NOT NULL DEFAULT 0,
                        last_updated INTEGER NOT NULL,
                        published_at TEXT
                    )
                    """
                )
            )
            if dialect == "postgresql":
                conn.execute(
                    text(
                        """
                        CREATE TABLE IF NOT EXISTS history (
                            id BIGSERIAL PRIMARY KEY,
                            video_id TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            view_count INTEGER NOT NULL,
                            FOREIGN KEY (video_id) REFERENCES videos(video_id)
                        )
                        """
                    )
                )
            else:
                conn.execute(
                    text(
                        """
                        CREATE TABLE IF NOT EXISTS history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            video_id TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            view_count INTEGER NOT NULL,
                            FOREIGN KEY (video_id) REFERENCES videos(video_id)
                        )
                        """
                    )
                )
            try:
                conn.execute(text("SELECT published_at FROM videos LIMIT 1"))
            except Exception:
                conn.execute(text("ALTER TABLE videos ADD COLUMN published_at TEXT"))

            conn.execute(
                text(
                    """
                    CREATE TABLE IF NOT EXISTS metadata (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """
                )
            )
            conn.execute(
                text(
                    """
                    CREATE TABLE IF NOT EXISTS locks (
                        key TEXT PRIMARY KEY,
                        owner TEXT NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """
                )
            )
            conn.execute(
                text(
                    """
                    CREATE INDEX IF NOT EXISTS idx_history_video_id ON history(video_id)
                    """
                )
            )
            conn.execute(
                text(
                    """
                    CREATE INDEX IF NOT EXISTS idx_history_timestamp ON history(timestamp)
                    """
                )
            )

    def _dialect(self) -> str:
        return self.get_engine().dialect.name

    def _advisory_lock_key(self) -> int:
        digest = hashlib.sha256(self._lock_name.encode("utf-8")).digest()[:8]
        return int.from_bytes(digest, byteorder="big", signed=True)

    def acquire_update_lock(self, ttl_seconds: Optional[int] = None):
        """Try to acquire a distributed lock. Returns handle on success."""
        ttl = ttl_seconds if ttl_seconds is not None else self.settings.update_lock_ttl_seconds
        engine = self.get_engine()
        dialect = engine.dialect.name
        if dialect == "postgresql":
            conn = engine.connect()
            try:
                acquired = conn.execute(
                    text("SELECT pg_try_advisory_lock(:k)"), {"k": self._advisory_lock_key()}
                ).scalar()
                if acquired:
                    return conn
                conn.close()
                return None
            except Exception:
                conn.close()
                raise

        owner = socket.gethostname() or "unknown"
        now = int(time.time())
        expires = now + ttl
        with engine.begin() as conn:
            inserted = conn.execute(
                text(
                    """
                    INSERT INTO locks (key, owner, expires_at)
                    VALUES (:k, :o, :e)
                    ON CONFLICT(key) DO NOTHING
                    """
                ),
                {"k": self._lock_name, "o": owner, "e": expires},
            )
            if getattr(inserted, "rowcount", 0):
                return {"owner": owner}

            updated = conn.execute(
                text(
                    """
                    UPDATE locks
                    SET owner = :o, expires_at = :e
                    WHERE key = :k AND expires_at < :now
                    """
                ),
                {"o": owner, "e": expires, "k": self._lock_name, "now": now},
            )
            if getattr(updated, "rowcount", 0):
                return {"owner": owner}
        return None

    def release_update_lock(self, handle) -> None:
        engine = self.get_engine()
        dialect = engine.dialect.name
        if dialect == "postgresql":
            conn = handle
            try:
                conn.execute(text("SELECT pg_advisory_unlock(:k)"), {"k": self._advisory_lock_key()})
            finally:
                try:
                    conn.close()
                except Exception:
                    pass
            return

        owner = handle.get("owner") if isinstance(handle, dict) else None
        with engine.begin() as conn:
            if owner:
                conn.execute(
                    text("DELETE FROM locks WHERE key = :k AND owner = :o"),
                    {"k": self._lock_name, "o": owner},
                )
            else:
                conn.execute(text("DELETE FROM locks WHERE key = :k"), {"k": self._lock_name})

    def save_video(
        self,
        video_id: str,
        title: str,
        view_count: int,
        timestamp: int,
        published_at: Optional[str] = None,
    ) -> None:
        """Upsert the latest video metrics and append to history."""
        engine = self.get_engine()
        with engine.begin() as conn:
            conn.execute(
                text(
                    """
                    INSERT INTO videos (video_id, title, current_views, last_updated, published_at)
                    VALUES (:video_id, :title, :current_views, :last_updated, :published_at)
                    ON CONFLICT(video_id) DO UPDATE SET
                        title = EXCLUDED.title,
                        current_views = EXCLUDED.current_views,
                        last_updated = EXCLUDED.last_updated,
                        published_at = COALESCE(EXCLUDED.published_at, videos.published_at)
                    """
                ),
                {
                    "video_id": video_id,
                    "title": title,
                    "current_views": view_count,
                    "last_updated": timestamp,
                    "published_at": published_at,
                },
            )
            conn.execute(
                text(
                    """
                    INSERT INTO history (video_id, timestamp, view_count)
                    VALUES (:video_id, :timestamp, :view_count)
                    """
                ),
                {"video_id": video_id, "timestamp": timestamp, "view_count": view_count},
            )
            conn.execute(
                text(
                    """
                    WITH ranked AS (
                        SELECT id, ROW_NUMBER() OVER (
                            PARTITION BY video_id ORDER BY timestamp DESC
                        ) AS rn
                        FROM history
                        WHERE video_id = :video_id
                    )
                    DELETE FROM history
                    WHERE id IN (SELECT id FROM ranked WHERE rn > 100)
                    """
                ),
                {"video_id": video_id},
            )

    def get_top_videos(self, limit: int = 100) -> List[Dict]:
        engine = self.get_engine()
        with engine.connect() as conn:
            result = conn.execute(
                text(
                    """
                    SELECT video_id, title, current_views, published_at
                    FROM videos
                    ORDER BY current_views DESC
                    LIMIT :limit
                    """
                ),
                {"limit": limit},
            )
            rows = result.fetchall()

        videos: List[Dict] = []
        for idx, row in enumerate(rows, start=1):
            mapping = row._mapping
            video_id = mapping["video_id"]
            videos.append(
                {
                    "rank": idx,
                    "videoId": video_id,
                    "title": mapping["title"],
                    "views": mapping["current_views"],
                    "publishedAt": mapping["published_at"],
                    "url": f"https://www.youtube.com/watch?v={video_id}",
                }
            )
        return videos

    def get_video_history(self, video_id: str) -> List[Dict]:
        engine = self.get_engine()
        with engine.connect() as conn:
            result = conn.execute(
                text(
                    """
                    SELECT timestamp, view_count
                    FROM history
                    WHERE video_id = :video_id
                    ORDER BY timestamp ASC
                    """
                ),
                {"video_id": video_id},
            )
            rows = result.fetchall()
        return [{"timestamp": row._mapping["timestamp"], "viewCount": row._mapping["view_count"]} for row in rows]

    def set_metadata(self, key: str, value: str) -> None:
        engine = self.get_engine()
        with engine.begin() as conn:
            conn.execute(
                text(
                    """
                    INSERT INTO metadata (key, value)
                    VALUES (:key, :value)
                    ON CONFLICT(key) DO UPDATE SET value = EXCLUDED.value
                    """
                ),
                {"key": key, "value": value},
            )

    def get_metadata(self, key: str) -> Optional[str]:
        engine = self.get_engine()
        with engine.connect() as conn:
            result = conn.execute(text("SELECT value FROM metadata WHERE key = :key"), {"key": key})
            row = result.fetchone()
        return row._mapping["value"] if row else None

    def get_all_metadata(self) -> Dict[str, str]:
        engine = self.get_engine()
        with engine.connect() as conn:
            result = conn.execute(text("SELECT key, value FROM metadata"))
            rows = result.fetchall()
        return {row._mapping["key"]: row._mapping["value"] for row in rows}

    def get_stats(self) -> Dict[str, int]:
        engine = self.get_engine()
        with engine.connect() as conn:
            videos_count = conn.execute(text("SELECT COUNT(*) as count FROM videos")).scalar_one()
            history_count = conn.execute(text("SELECT COUNT(*) as count FROM history")).scalar_one()
        return {"total_videos": videos_count, "total_history_entries": history_count}

    def close(self) -> None:
        if self._engine is not None:
            self._engine.dispose()
            self._engine = None
