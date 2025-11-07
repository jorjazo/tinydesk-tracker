#!/usr/bin/env python3
"""
YouTube Tiny Desk Concert Tracker for Raspberry Pi
Tracks top 100 most viewed Tiny Desk concerts with historical data
Uses SQLite database for efficient storage and querying
"""

import os
import sys
import time
import schedule  # type: ignore[import]
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional
import requests
from dotenv import load_dotenv
from flask import Flask, render_template, jsonify  # type: ignore[import]
from sqlalchemy import create_engine, text  # type: ignore[import]
from sqlalchemy.engine import Engine  # type: ignore[import]
from croniter import croniter  # type: ignore[import]
import socket
import hashlib
from datetime import timedelta

# Load environment variables
load_dotenv()

# Configuration
YOUTUBE_API_KEY = os.getenv('YOUTUBE_API_KEY')
NPR_MUSIC_CHANNEL_ID = os.getenv('NPR_MUSIC_CHANNEL_ID', 'UC4eYXhJI4-7wSWc8UNRwD4A')
DB_FILE = Path(os.getenv('DB_FILE', './data/tinydesk.db'))
DATABASE_URL = os.getenv('DATABASE_URL')  # e.g. postgresql+psycopg2://user:pass@host:5432/dbname
UPDATE_INTERVAL_HOURS = float(os.getenv('UPDATE_INTERVAL_HOURS', '6'))
MAX_RESULTS_PER_REQUEST = 50
TOTAL_VIDEOS_TO_FETCH = 1000  # Fetch many to ensure we get all Tiny Desk videos (going back to 2016)
TINY_DESK_PLAYLIST_ID = os.getenv('TINY_DESK_PLAYLIST_ID', 'PL1B627337ED6F55F0')
UPDATE_SCHEDULE = os.getenv('UPDATE_SCHEDULE', '').strip()  # e.g. "06:00,12:00,18:00"
UPDATE_CRON = os.getenv('UPDATE_CRON', '*/30 * * * *').strip()          # default: every 30 minutes
UPDATE_LOCK_TTL_SECONDS = int(os.getenv('UPDATE_LOCK_TTL_SECONDS', '7200'))

# Ensure data directory exists
DB_FILE.parent.mkdir(parents=True, exist_ok=True)

# Flask app
app = Flask(__name__)


class Database:
    """SQLite database manager"""
    
    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.engine: Optional[Engine] = None
        self.initialize_db()
        self._lock_name = 'tinydesk_update_lock'
    
    def get_engine(self) -> Engine:
        """Get SQLAlchemy engine, creating on first use."""
        if self.engine is None:
            if DATABASE_URL:
                self.engine = create_engine(DATABASE_URL, future=True, pool_pre_ping=True)
            else:
                # Default to SQLite file
                sqlite_url = f"sqlite:///{self.db_path}"
                self.engine = create_engine(sqlite_url, future=True)
                # Ensure SQLite optimizations
                with self.engine.connect() as conn:
                    conn.execute(text("PRAGMA journal_mode=WAL"))
                    conn.execute(text("PRAGMA cache_size=-10000"))
                    conn.execute(text("PRAGMA temp_store=MEMORY"))
        return self.engine
    
    def initialize_db(self):
        """Create database schema if it doesn't exist"""
        engine = self.get_engine()
        dialect = engine.dialect.name
        with engine.begin() as conn:
        
        # Videos table
            conn.execute(text(
                """
                CREATE TABLE IF NOT EXISTS videos (
                    video_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    current_views INTEGER NOT NULL DEFAULT 0,
                    last_updated INTEGER NOT NULL,
                    published_at TEXT
                )
                """
            ))
        
        # History table
            # History table with portable auto-increment
            if dialect == 'postgresql':
                conn.execute(text(
                    """
                    CREATE TABLE IF NOT EXISTS history (
                        id BIGSERIAL PRIMARY KEY,
                        video_id TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        view_count INTEGER NOT NULL,
                        FOREIGN KEY (video_id) REFERENCES videos(video_id)
                    )
                    """
                ))
            else:
                conn.execute(text(
                    """
                    CREATE TABLE IF NOT EXISTS history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        video_id TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        view_count INTEGER NOT NULL,
                        FOREIGN KEY (video_id) REFERENCES videos(video_id)
                    )
                    """
                ))
        
        # Add published_at column if it doesn't exist (for existing databases)
            # Add published_at column if it doesn't exist (best-effort)
            try:
                conn.execute(text("SELECT published_at FROM videos LIMIT 1"))
            except Exception:
                conn.execute(text("ALTER TABLE videos ADD COLUMN published_at TEXT"))
        
        # Create index for faster queries
            conn.execute(text(
                """
                CREATE INDEX IF NOT EXISTS idx_history_video_id 
                ON history(video_id)
                """
            ))
            conn.execute(text(
                """
                CREATE INDEX IF NOT EXISTS idx_history_timestamp 
                ON history(timestamp)
                """
            ))
            # Metadata table
            conn.execute(text(
                """
                CREATE TABLE IF NOT EXISTS metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """
            ))
        print(f"✓ Database initialized: {DATABASE_URL or self.db_path}")

    def _dialect(self) -> str:
        return self.get_engine().dialect.name

    def _advisory_lock_key(self) -> int:
        """Generate a stable 64-bit signed integer key for advisory locks."""
        digest = hashlib.sha256(self._lock_name.encode('utf-8')).digest()[:8]
        return int.from_bytes(digest, byteorder='big', signed=True)

    def acquire_update_lock(self, ttl_seconds: int = UPDATE_LOCK_TTL_SECONDS):
        """Try to acquire a distributed update lock.
        Returns a handle to be passed to release_update_lock on success; otherwise None."""
        engine = self.get_engine()
        dialect = engine.dialect.name
        if dialect == 'postgresql':
            conn = engine.connect()
            try:
                acquired = conn.execute(text("SELECT pg_try_advisory_lock(:k)"), {'k': self._advisory_lock_key()}).scalar()
                if acquired:
                    print("✓ Acquired Postgres advisory lock for update")
                    return conn  # keep connection open to hold the lock
                else:
                    conn.close()
                    print("↷ Another instance holds the Postgres advisory lock; skipping")
                    return None
            except Exception:
                conn.close()
                raise
        else:
            # SQLite or other: best-effort lock table with TTL
            owner = socket.gethostname() or 'unknown'
            now = int(time.time())
            expires = now + ttl_seconds
            with engine.begin() as conn:
                conn.execute(text(
                    """
                    CREATE TABLE IF NOT EXISTS locks (
                        key TEXT PRIMARY KEY,
                        owner TEXT NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """
                ))
                # Try insert new lock
                ins = conn.execute(text(
                    """
                    INSERT INTO locks (key, owner, expires_at)
                    VALUES (:k, :o, :e)
                    ON CONFLICT(key) DO NOTHING
                    """
                ), {'k': self._lock_name, 'o': owner, 'e': expires})
                if getattr(ins, 'rowcount', 0):
                    print("✓ Acquired SQLite lock for update")
                    return {'owner': owner}
                # Try to take over if expired
                upd = conn.execute(text(
                    """
                    UPDATE locks
                    SET owner = :o, expires_at = :e
                    WHERE key = :k AND expires_at < :now
                    """
                ), {'o': owner, 'e': expires, 'k': self._lock_name, 'now': now})
                if getattr(upd, 'rowcount', 0):
                    print("✓ Reacquired expired SQLite lock for update")
                    return {'owner': owner}
            print("↷ Another instance holds the SQLite lock; skipping")
            return None

    def release_update_lock(self, handle) -> None:
        """Release the previously acquired update lock."""
        engine = self.get_engine()
        dialect = engine.dialect.name
        if dialect == 'postgresql':
            conn = handle
            try:
                conn.execute(text("SELECT pg_advisory_unlock(:k)"), {'k': self._advisory_lock_key()})
                print("✓ Released Postgres advisory lock")
            except Exception as e:
                print(f"✗ Error releasing Postgres advisory lock: {e}")
            finally:
                try:
                    conn.close()
                except Exception:
                    pass
        else:
            owner = handle.get('owner') if isinstance(handle, dict) else None
            with engine.begin() as conn:
                if owner:
                    conn.execute(text("DELETE FROM locks WHERE key = :k AND owner = :o"), {'k': self._lock_name, 'o': owner})
                else:
                    conn.execute(text("DELETE FROM locks WHERE key = :k"), {'k': self._lock_name})
            print("✓ Released SQLite lock")
    
    def save_video(self, video_id: str, title: str, view_count: int, timestamp: int, published_at: Optional[str] = None):
        """Save or update video and add history entry"""
        engine = self.get_engine()
        with engine.begin() as conn:
            # Insert or update video
            conn.execute(text(
                """
                INSERT INTO videos (video_id, title, current_views, last_updated, published_at)
                VALUES (:video_id, :title, :current_views, :last_updated, :published_at)
                ON CONFLICT(video_id) DO UPDATE SET
                    title = EXCLUDED.title,
                    current_views = EXCLUDED.current_views,
                    last_updated = EXCLUDED.last_updated,
                    published_at = COALESCE(EXCLUDED.published_at, videos.published_at)
                """
            ), {
                'video_id': video_id,
                'title': title,
                'current_views': view_count,
                'last_updated': timestamp,
                'published_at': published_at
            })
            # Add history entry
            conn.execute(text(
                """
                INSERT INTO history (video_id, timestamp, view_count)
                VALUES (:video_id, :timestamp, :view_count)
                """
            ), {
                'video_id': video_id,
                'timestamp': timestamp,
                'view_count': view_count
            })
            # Limit history to last 100 entries per video (portable CTE)
            conn.execute(text(
                """
                WITH ranked AS (
                    SELECT id, ROW_NUMBER() OVER (
                        PARTITION BY video_id ORDER BY timestamp DESC
                    ) AS rn
                    FROM history
                    WHERE video_id = :video_id
                )
                DELETE FROM history
                WHERE id IN (
                    SELECT id FROM ranked WHERE rn > 100
                )
                """
            ), {
                'video_id': video_id
            })
    
    def get_top_videos(self, limit: int = 100) -> List[Dict]:
        """Get top videos sorted by view count"""
        engine = self.get_engine()
        videos = []
        with engine.connect() as conn:
            result = conn.execute(text(
                """
                SELECT video_id, title, current_views, published_at
                FROM videos
                ORDER BY current_views DESC
                LIMIT :limit
                """
            ), {'limit': limit})
            rows = result.fetchall()
        for i, row in enumerate(rows, 1):
            video_id_val = row._mapping['video_id']
            videos.append({
                'rank': i,
                'videoId': video_id_val,
                'title': row._mapping['title'],
                'views': row._mapping['current_views'],
                'publishedAt': row._mapping['published_at'],
                'url': f'https://www.youtube.com/watch?v={video_id_val}'
            })
        
        return videos
    
    def get_video_history(self, video_id: str) -> List[Dict]:
        """Get historical view counts for a video"""
        engine = self.get_engine()
        with engine.connect() as conn:
            result = conn.execute(text(
                """
                SELECT timestamp, view_count
                FROM history
                WHERE video_id = :video_id
                ORDER BY timestamp ASC
                """
            ), {'video_id': video_id})
            rows = result.fetchall()
        return [{'timestamp': row._mapping['timestamp'], 'viewCount': row._mapping['view_count']} 
                for row in rows]
    
    def set_metadata(self, key: str, value: str):
        """Set metadata value"""
        engine = self.get_engine()
        with engine.begin() as conn:
            conn.execute(text(
                """
                INSERT INTO metadata (key, value)
                VALUES (:key, :value)
                ON CONFLICT(key) DO UPDATE SET value = EXCLUDED.value
                """
            ), {'key': key, 'value': value})
    
    def get_metadata(self, key: str) -> Optional[str]:
        """Get metadata value"""
        engine = self.get_engine()
        with engine.connect() as conn:
            result = conn.execute(text("SELECT value FROM metadata WHERE key = :key"), {'key': key})
            row = result.fetchone()
        return row._mapping['value'] if row else None
    
    def get_all_metadata(self) -> Dict:
        """Get all metadata as dictionary"""
        engine = self.get_engine()
        with engine.connect() as conn:
            result = conn.execute(text("SELECT key, value FROM metadata"))
            rows = result.fetchall()
        return {row._mapping['key']: row._mapping['value'] for row in rows}
    
    def get_stats(self) -> Dict:
        """Get database statistics"""
        engine = self.get_engine()
        with engine.connect() as conn:
            vc = conn.execute(text("SELECT COUNT(*) as count FROM videos")).scalar_one()
            hc = conn.execute(text("SELECT COUNT(*) as count FROM history")).scalar_one()
        return {
            'total_videos': vc,
            'total_history_entries': hc
        }
    
    def close(self):
        """Close database connection"""
        if self.engine:
            self.engine.dispose()
            self.engine = None


class YouTubeAPIClient:
    """YouTube Data API v3 client"""
    
    BASE_URL = "https://www.googleapis.com/youtube/v3"
    UPLOADS_PLAYLIST_ID = "UU4eYXhJI4-7wSWc8UNRwD4A"  # NPR Music uploads (UC -> UU)
    
    def __init__(self, api_key: str):
        self.api_key = api_key
        self.session = requests.Session()
    
    def get_playlist_videos(self, page_token: Optional[str] = None) -> Dict:
        """Get videos from channel's uploads playlist"""
        return self.get_playlist_videos_by_id(self.UPLOADS_PLAYLIST_ID, page_token)

    def get_playlist_videos_by_id(self, playlist_id: str, page_token: Optional[str] = None) -> Dict:
        """Get videos from a specific playlist by ID (paginated)"""
        params = {
            'part': 'snippet',
            'playlistId': str(playlist_id),
            'maxResults': str(MAX_RESULTS_PER_REQUEST),
            'key': str(self.api_key)
        }
        if page_token:
            params['pageToken'] = str(page_token)
        response = self.session.get(f"{self.BASE_URL}/playlistItems", params=params)
        response.raise_for_status()
        return response.json()
    
    def get_video_statistics(self, video_ids: List[str]) -> Dict:
        """Get statistics for multiple videos"""
        params = {
            'part': 'snippet,statistics',
            'id': ','.join(video_ids),
            'key': str(self.api_key)
        }
        
        response = self.session.get(f"{self.BASE_URL}/videos", params=params)
        response.raise_for_status()
        return response.json()


class TinyDeskTracker:
    """Main tracker class"""
    
    def __init__(self, db: Database):
        if not YOUTUBE_API_KEY:
            raise ValueError("YOUTUBE_API_KEY not set in .env file")
        
        self.db = db
        self.api = YouTubeAPIClient(YOUTUBE_API_KEY)
    
    def fetch_all_videos(self) -> List[Dict]:
        """Fetch all Tiny Desk videos from the Tiny Desk playlist with statistics"""
        all_videos = []
        page_token = None
        page_num = 1
        
        print(f"\n{'='*50}")
        print("Fetching Tiny Desk concerts from official playlist...")
        print(f"Playlist ID: {TINY_DESK_PLAYLIST_ID}")
        print(f"{'='*50}")
        
        while True:
            print(f"\nFetching page {page_num}...")
            
            # Get videos from Tiny Desk playlist
            playlist_results = self.api.get_playlist_videos_by_id(TINY_DESK_PLAYLIST_ID, page_token)
            items = playlist_results.get('items', [])
            
            if not items:
                print("No more results available")
                break
            
            # Extract video IDs (skip entries without resourceId/videoId)
            video_ids = [
                item['snippet']['resourceId']['videoId']
                for item in items
                if item.get('snippet', {}).get('resourceId', {}).get('videoId')
            ]
            print(f"Found {len(video_ids)} videos on this page")
            
            if not video_ids:
                # Proceed to next page if available
                page_token = playlist_results.get('nextPageToken')
                if not page_token:
                    print("No more pages available")
                    break
                page_num += 1
                time.sleep(0.5)
                continue
            
            # Get detailed statistics
            stats_result = self.api.get_video_statistics(video_ids)
            stats_items = stats_result.get('items', [])
            
            # Process videos - playlist is already scoped to Tiny Desk, include all
            for item in stats_items:
                title = item['snippet']['title']
                video_data = {
                    'videoId': item['id'],
                    'title': title,
                    'viewCount': int(item['statistics'].get('viewCount', 0)),
                    'publishedAt': item['snippet'].get('publishedAt', '')
                }
                all_videos.append(video_data)
            
            print(f"✓ Fetched stats for {len(stats_items)} videos")
            print(f"  Total Tiny Desk videos collected so far: {len(all_videos)}")
            sys.stdout.flush()  # Force flush for Docker logs
            
            # Check for next page
            page_token = playlist_results.get('nextPageToken')
            if not page_token:
                print("No more pages available")
                break
            
            page_num += 1
            time.sleep(0.5)  # Rate limiting
        
        print(f"\n{'='*50}")
        print(f"Total Tiny Desk playlist videos fetched: {len(all_videos)}")
        print(f"{'='*50}\n")
        
        return all_videos
    
    def update_historical_data(self, videos: List[Dict]):
        """Update database with new fetch"""
        timestamp = int(time.time())
        
        print("Saving to database...")
        for i, video in enumerate(videos, 1):
            video_id = video['videoId']
            title = video['title']
            view_count = video['viewCount']
            published_at = video.get('publishedAt', '')
            
            self.db.save_video(video_id, title, view_count, timestamp, published_at)
            
            if i % 10 == 0:
                print(f"  Saved {i}/{len(videos)} videos...")
        
        # Update metadata
        self.db.set_metadata('lastUpdate', str(timestamp))
        self.db.set_metadata('totalVideos', str(len(videos)))
        
        print(f"✓ All {len(videos)} videos saved to database")
    
    def update(self):
        """Perform update cycle"""
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
            
        except Exception as e:
            print(f"✗ Error during update: {e}")
            import traceback
            traceback.print_exc()
    
    def get_top_videos(self, limit: int = 100) -> List[Dict]:
        """Get top videos sorted by current view count"""
        return self.db.get_top_videos(limit)
    
    def get_metadata(self) -> Dict:
        """Get metadata as dictionary with proper types"""
        metadata = self.db.get_all_metadata()
        return {
            'lastUpdate': int(metadata.get('lastUpdate', 0)),
            'totalVideos': int(metadata.get('totalVideos', 0))
        }


# Global tracker instance
tracker = None
db = None


def initialize_tracker():
    """Initialize the tracker"""
    global tracker, db
    db = Database(DB_FILE)
    tracker = TinyDeskTracker(db)
    
    # Show database stats
    stats = db.get_stats()
    print(f"✓ Tracker initialized")
    print(f"  Videos in database: {stats['total_videos']}")
    print(f"  History entries: {stats['total_history_entries']}")


def scheduled_update():
    """Scheduled update function"""
    if tracker:
        tracker.update()


def schedule_updates():
    """Configure scheduled updates using either fixed times or interval hours."""
    # If UPDATE_CRON is provided, use cron-like scheduling via croniter
    if UPDATE_CRON:
        cron_str = normalize_cron_expression(UPDATE_CRON)
        if cron_str:
            start_cron_scheduler(cron_str)
            print(f"✓ Cron-based updates enabled: '{cron_str}' (container local time)")
            return
        else:
            print(f"✗ Invalid UPDATE_CRON='{UPDATE_CRON}'. Falling back to time/interval scheduling")

    # If UPDATE_SCHEDULE is provided, expect comma-separated HH:MM times (24h)
    if UPDATE_SCHEDULE:
        times = [t.strip() for t in UPDATE_SCHEDULE.split(',') if t.strip()]
        valid_times = []
        for t in times:
            try:
                # Validate format HH:MM
                from datetime import datetime as _dt
                _dt.strptime(t, "%H:%M")
                schedule.every().day.at(t).do(scheduled_update)
                valid_times.append(t)
            except Exception:
                print(f"✗ Skipping invalid time in UPDATE_SCHEDULE: '{t}' (expected HH:MM)")
        if valid_times:
            print(f"✓ Scheduled daily updates at: {', '.join(valid_times)} (container local time)")
        else:
            print("✗ No valid times in UPDATE_SCHEDULE; falling back to interval scheduling")
            schedule.every(UPDATE_INTERVAL_HOURS).hours.do(scheduled_update)
            print(f"✓ Scheduled updates every {UPDATE_INTERVAL_HOURS} hours")
    else:
        schedule.every(UPDATE_INTERVAL_HOURS).hours.do(scheduled_update)
        print(f"✓ Scheduled updates every {UPDATE_INTERVAL_HOURS} hours")


def normalize_cron_expression(expr: str) -> Optional[str]:
    """Normalize cron expression to 5 fields. Accepts 3-6 fields; pads to 5.
    Returns normalized string or None if invalid."""
    parts = [p for p in expr.split() if p]
    if len(parts) < 3:
        return None
    if len(parts) == 3:
        # Assume minute hour day-of-month -> add month, day-of-week as '*'
        parts += ['*', '*']
    elif len(parts) == 4:
        parts += ['*']
    elif len(parts) in (5, 6):
        parts = parts[:5]  # ignore year if provided
    else:
        return None
    return ' '.join(parts)


def start_cron_scheduler(cron_str: str):
    """Start a background thread that triggers updates based on a cron pattern."""
    from threading import Thread
    from datetime import datetime as _dt

    def _loop():
        try:
            base = _dt.now()
            itr = croniter(cron_str, base)
            while True:
                next_dt = itr.get_next(_dt)
                delay = max(0, (next_dt - _dt.now()).total_seconds())
                print(f"Next scheduled update at {next_dt.isoformat(timespec='seconds')}")
                sys.stdout.flush()
                time.sleep(delay)
                if tracker:
                    tracker.update()
                # Reset base for next iteration
                base = _dt.now()
                itr = croniter(cron_str, base)
        except Exception as e:
            print(f"✗ Cron scheduler error: {e}")

    t = Thread(target=_loop, daemon=True)
    t.start()


def compute_next_update_timestamp(last_update_ts: int) -> int:
    """Compute the next update timestamp (epoch seconds) based on config.
    Priority: UPDATE_CRON > UPDATE_SCHEDULE > UPDATE_INTERVAL_HOURS."""
    now_dt = datetime.now()
    # Cron-based scheduling
    if UPDATE_CRON:
        cron_str = normalize_cron_expression(UPDATE_CRON)
        if cron_str:
            try:
                next_dt = croniter(cron_str, now_dt).get_next(datetime)
                return int(next_dt.timestamp())
            except Exception:
                pass
    # Fixed times daily
    if UPDATE_SCHEDULE:
        times = [t.strip() for t in UPDATE_SCHEDULE.split(',') if t.strip()]
        candidates = []
        for t in times:
            try:
                hh, mm = t.split(':')
                cand = now_dt.replace(hour=int(hh), minute=int(mm), second=0, microsecond=0)
                if cand <= now_dt:
                    cand = cand + timedelta(days=1)
                candidates.append(cand)
            except Exception:
                continue
        if candidates:
            next_dt = min(candidates)
            return int(next_dt.timestamp())
    # Interval fallback
    base_dt = datetime.fromtimestamp(last_update_ts) if last_update_ts else now_dt
    next_dt = base_dt + timedelta(hours=UPDATE_INTERVAL_HOURS)
    return int(next_dt.timestamp())


# Flask routes
@app.route('/')
def index():
    """Serve main page"""
    return render_template('index.html')


@app.route('/dashboard')
def dashboard():
    """Serve analytics dashboard"""
    return render_template('dashboard.html')


@app.route('/history')
def history():
    """Serve ranking history page"""
    return render_template('history.html')


@app.route('/api/top')
def api_top():
    """API endpoint for top videos"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    top_videos = tracker.get_top_videos()
    metadata = tracker.get_metadata()
    next_update = compute_next_update_timestamp(metadata.get('lastUpdate', 0))
    
    return jsonify({
        'videos': top_videos,
        'lastUpdate': metadata.get('lastUpdate', 0),
        'nextUpdate': next_update,
        'total': len(top_videos)
    })


@app.route('/api/data')
def api_data():
    """API endpoint for raw data (backward compatible)"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    # Build JSON structure similar to old format for compatibility
    top_videos = tracker.get_top_videos()
    data = {}
    
    for video in top_videos:
        video_id = video['videoId']
        history = db.get_video_history(video_id)
        data[video_id] = {
            'title': video['title'],
            'currentViews': video['views'],
            'history': history
        }
    
    # Add metadata
    data['_metadata'] = tracker.get_metadata()
    
    return jsonify(data)


@app.route('/api/status')
def api_status():
    """API endpoint for status"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    metadata = tracker.get_metadata()
    next_update = compute_next_update_timestamp(metadata.get('lastUpdate', 0))
    stats = db.get_stats()
    
    return jsonify({
        'status': 'online',
        'lastUpdate': metadata.get('lastUpdate', 0),
        'nextUpdate': next_update,
        'totalVideos': metadata.get('totalVideos', 0),
        'dbStats': stats
    })


@app.route('/api/history/<video_id>')
def api_video_history(video_id):
    """API endpoint for video history"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    history = db.get_video_history(video_id)
    return jsonify({
        'videoId': video_id,
        'history': history
    })


@app.route('/api/ranking-history')
def api_ranking_history():
    """API endpoint for ranking evolution over time"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    engine = db.get_engine()
    # Get all unique timestamps
    with engine.connect() as conn:
        result = conn.execute(text(
            """
            SELECT DISTINCT timestamp 
            FROM history 
            ORDER BY timestamp ASC
            """
        ))
        trows = result.fetchall()
    timestamps = [row._mapping['timestamp'] for row in trows]
    
    # For each timestamp, calculate rankings
    ranking_evolution = {}
    
    for ts in timestamps:
        # Get all videos and their view counts at this timestamp
        with engine.connect() as conn:
            result = conn.execute(text(
                """
                SELECT h.video_id, v.title, h.view_count, v.published_at
                FROM history h
                JOIN videos v ON h.video_id = v.video_id
                WHERE h.timestamp = :ts
                ORDER BY h.view_count DESC
                """
            ), {'ts': ts})
            videos_at_timestamp = result.fetchall()
        
        for rank, video in enumerate(videos_at_timestamp, 1):
            video_id = video._mapping['video_id']
            
            if video_id not in ranking_evolution:
                ranking_evolution[video_id] = {
                    'videoId': video_id,
                    'title': video._mapping['title'],
                    'publishedAt': video._mapping['published_at'],
                    'history': []
                }
            
            ranking_evolution[video_id]['history'].append({
                'timestamp': ts,
                'rank': rank,
                'views': video._mapping['view_count']
            })
    
    # Calculate rank changes
    for video_id, data in ranking_evolution.items():
        history = data['history']
        if len(history) >= 2:
            latest_rank = history[-1]['rank']
            previous_rank = history[-2]['rank']
            data['rankChange'] = previous_rank - latest_rank  # Positive = moved up
            data['currentRank'] = latest_rank
            data['previousRank'] = previous_rank
        else:
            data['rankChange'] = 0
            data['currentRank'] = history[0]['rank'] if history else 0
            data['previousRank'] = history[0]['rank'] if history else 0
    
    # Get top movers (biggest positive rank changes)
    videos_list = list(ranking_evolution.values())
    top_movers = sorted(videos_list, key=lambda x: x['rankChange'], reverse=True)[:10]
    
    # Get biggest fallers (biggest negative rank changes)
    biggest_fallers = sorted(videos_list, key=lambda x: x['rankChange'])[:10]
    
    return jsonify({
        'timestamps': timestamps,
        'evolution': ranking_evolution,
        'topMovers': top_movers,
        'biggestFallers': biggest_fallers
    })


@app.route('/api/update', methods=['POST'])
def api_manual_update():
    """API endpoint to manually trigger an update"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    from threading import Thread
    
    def update_async():
        tracker.update()
    
    # Run update in background thread
    Thread(target=update_async, daemon=True).start()
    
    return jsonify({'status': 'Update started in background'})


@app.route('/api/add-video/<video_id>', methods=['POST'])
def api_add_specific_video(video_id):
    """API endpoint to manually add a specific video by ID"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    try:
        # Fetch video details from YouTube
        stats_result = tracker.api.get_video_statistics([video_id])
        items = stats_result.get('items', [])
        
        if not items:
            return jsonify({'error': f'Video {video_id} not found'}), 404
        
        item = items[0]
        title = item['snippet']['title']
        view_count = int(item['statistics'].get('viewCount', 0))
        published_at = item['snippet'].get('publishedAt', '')
        timestamp = int(time.time())
        
        # Save to database
        db.save_video(video_id, title, view_count, timestamp, published_at)
        
        return jsonify({
            'status': 'Video added successfully',
            'videoId': video_id,
            'title': title,
            'views': view_count
        })
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/analytics')
def api_analytics():
    """API endpoint for analytics and growth metrics"""
    if not tracker:
        return jsonify({'error': 'Tracker not initialized'}), 500
    
    engine = db.get_engine()
    # Get all videos with their latest two data points for growth calculation
    with engine.connect() as conn:
        result = conn.execute(text(
            """
            SELECT 
                v.video_id,
                v.title,
                v.current_views,
                v.last_updated,
                v.published_at
            FROM videos v
            ORDER BY v.current_views DESC
            """
        ))
        video_rows = result.fetchall()
    
    videos = []
    total_views = 0
    
    for rank, row in enumerate(video_rows, 1):
        video_id = row._mapping['video_id']
        current_views = row._mapping['current_views']
        total_views += current_views
        
        # Get last two history entries to calculate growth
        with engine.connect() as conn:
            result = conn.execute(text(
                """
                SELECT timestamp, view_count
                FROM history
                WHERE video_id = :video_id
                ORDER BY timestamp DESC
                LIMIT 2
                """
            ), {'video_id': video_id})
            history = result.fetchall()
        
        views_per_hour = 0
        views_change = 0
        hours_since_last = 0
        
        if len(history) >= 2:
            latest = history[0]
            previous = history[1]
            
            time_diff = latest._mapping['timestamp'] - previous._mapping['timestamp']
            hours_since_last = time_diff / 3600
            
            if hours_since_last > 0:
                views_change = latest._mapping['view_count'] - previous._mapping['view_count']
                views_per_hour = views_change / hours_since_last
        
        # Calculate lifetime views per hour (total views / age in hours)
        lifetime_views_per_hour = 0
        if row._mapping['published_at']:
            import datetime
            published = datetime.datetime.fromisoformat(row._mapping['published_at'].replace('Z', '+00:00'))
            now = datetime.datetime.now(datetime.timezone.utc)
            age_hours = (now - published).total_seconds() / 3600
            if age_hours > 0:
                lifetime_views_per_hour = current_views / age_hours
        
        videos.append({
            'videoId': video_id,
            'title': row._mapping['title'],
            'currentViews': current_views,
            'currentRank': rank,
            'publishedAt': row._mapping['published_at'],
            'viewsPerHour': round(views_per_hour, 2),
            'viewsChange': views_change,
            'hoursSinceLastUpdate': round(hours_since_last, 2),
            'lifetimeViewsPerHour': round(lifetime_views_per_hour, 2)
        })
    
    # Sort by views per hour for trending
    trending = sorted(videos, key=lambda x: x['viewsPerHour'], reverse=True)[:10]
    
    # Top performers by lifetime average views per hour
    top_performers = sorted(videos, key=lambda x: x['lifetimeViewsPerHour'], reverse=True)[:10]
    
    # Calculate statistics
    if videos:
        avg_views = total_views / len(videos)
        avg_growth = sum(v['viewsPerHour'] for v in videos) / len(videos)
    else:
        avg_views = 0
        avg_growth = 0
    
    metadata = tracker.get_metadata()
    
    return jsonify({
        'trending': trending,
        'topPerformers': top_performers,
        'statistics': {
            'totalVideos': len(videos),
            'totalViews': total_views,
            'averageViews': round(avg_views, 2),
            'averageViewsPerHour': round(avg_growth, 2),
            'lastUpdate': metadata.get('lastUpdate', 0)
        }
    })


def main():
    """Main entry point"""
    print("\n" + "="*50)
    print("  YouTube Tiny Desk Concert Tracker")
    print("  Raspberry Pi Edition - SQLite")
    print("="*50 + "\n")
    
    # Initialize tracker
    initialize_tracker()
    
    # Initial update only if no prior data
    metadata = tracker.get_metadata()
    last_update = metadata.get('lastUpdate', 0)
    if last_update == 0:
        print("No previous data found. Performing initial update...")
        tracker.update()
    else:
        last_update_time = datetime.fromtimestamp(last_update)
        print(f"Last update: {last_update_time.strftime('%Y-%m-%d %H:%M:%S')}")
    
    # Schedule updates
    # Configure schedule (fixed times if provided; else interval)
    schedule_updates()
    
    # Start web server in background thread
    print(f"✓ Starting web server on http://0.0.0.0:5000\n")
    
    from threading import Thread
    server_thread = Thread(target=lambda: app.run(host='0.0.0.0', port=5000, debug=False))
    server_thread.daemon = True
    server_thread.start()
    
    print("="*50)
    print("  System Ready!")
    print("="*50)
    print(f"Web Interface: http://localhost:5000/")
    print(f"Database: {DB_FILE}")
    stats = db.get_stats()
    print(f"Videos tracked: {stats['total_videos']}")
    print("Press Ctrl+C to exit")
    print("="*50 + "\n")
    
    # Keep running and check for scheduled tasks
    try:
        while True:
            schedule.run_pending()
            time.sleep(60)  # Check every minute
    except KeyboardInterrupt:
        print("\n\nShutting down gracefully...")
        if db:
            db.close()
        print("✓ Goodbye!\n")


if __name__ == '__main__':
    main()
