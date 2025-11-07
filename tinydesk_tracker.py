#!/usr/bin/env python3
"""
Command-line entry point used to run the Tiny Desk tracker service.
"""

from __future__ import annotations

import time
from datetime import datetime
from threading import Thread

import schedule
from dotenv import load_dotenv

from tinydesk_tracker.config import load_settings
from tinydesk_tracker.database import Database
from tinydesk_tracker.scheduler import schedule_updates
from tinydesk_tracker.tracker import TinyDeskTracker
from tinydesk_tracker.web import create_app


def _banner() -> None:
    print("\n" + "=" * 50)
    print("  YouTube Tiny Desk Concert Tracker")
    print("  Raspberry Pi Edition - SQLite")
    print("=" * 50 + "\n")


def _start_web_server(app) -> Thread:
    thread = Thread(target=lambda: app.run(host="0.0.0.0", port=5000, debug=False), daemon=True)
    thread.start()
    return thread


def main() -> None:
    load_dotenv()
    settings = load_settings()

    if not settings.youtube_api_key:
        raise ValueError("YOUTUBE_API_KEY not set in the environment")

    database = Database(settings)
    tracker = TinyDeskTracker(database, settings=settings)
    app = create_app(settings, tracker, database)

    _banner()
    stats = database.get_stats()
    print("✓ Tracker initialized")
    print(f"  Videos in database: {stats['total_videos']}")
    print(f"  History entries: {stats['total_history_entries']}")

    metadata = tracker.get_metadata()
    last_update = metadata.get("lastUpdate", 0)
    if last_update == 0:
        print("No previous data found. Performing initial update...")
        tracker.update()
    else:
        last_update_time = datetime.fromtimestamp(last_update)
        print(f"Last update: {last_update_time.strftime('%Y-%m-%d %H:%M:%S')}")

    schedule_updates(settings, tracker, schedule)
    print("✓ Starting web server on http://0.0.0.0:5000\n")
    _start_web_server(app)

    print("=" * 50)
    print("  System Ready!")
    print("=" * 50)
    print("Web Interface: http://localhost:5000/")
    print(f"Database: {settings.db_file}")
    print("Press Ctrl+C to exit")
    print("=" * 50 + "\n")

    try:
        while True:
            schedule.run_pending()
            time.sleep(60)
    except KeyboardInterrupt:
        print("\n\nShutting down gracefully...")
    finally:
        database.close()
        print("✓ Goodbye!\n")


if __name__ == "__main__":
    main()
