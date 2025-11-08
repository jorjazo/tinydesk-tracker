"""
Flask application factory exposing the HTTP API and HTML endpoints.
"""

from __future__ import annotations

import time
from pathlib import Path
from typing import Dict

from flask import Flask, jsonify, render_template
from sqlalchemy import text

from .config import Settings
from .database import Database
from .scheduler import compute_next_update_timestamp
from .tracker import TinyDeskTracker


def create_app(settings: Settings, tracker: TinyDeskTracker, database: Database) -> Flask:
    template_folder = Path(__file__).resolve().parent.parent / "templates"
    app = Flask(__name__, template_folder=str(template_folder))

    @app.route("/")
    def index():
        return render_template("index.html")

    @app.route("/dashboard")
    def dashboard():
        return render_template("dashboard.html")

    @app.route("/history")
    def history():
        return render_template("history.html")

    @app.route("/api/top")
    def api_top():
        metadata = tracker.get_metadata()
        next_update = compute_next_update_timestamp(settings, metadata.get("lastUpdate", 0))
        top_videos = tracker.get_top_videos()
        return jsonify(
            {
                "videos": top_videos,
                "lastUpdate": metadata.get("lastUpdate", 0),
                "nextUpdate": next_update,
                "total": len(top_videos),
            }
        )

    @app.route("/api/data")
    def api_data():
        top_videos = tracker.get_top_videos()
        data: Dict[str, Dict] = {}
        for video in top_videos:
            video_id = video["videoId"]
            history = database.get_video_history(video_id)
            data[video_id] = {
                "title": video["title"],
                "currentViews": video["views"],
                "history": history,
            }
        data["_metadata"] = tracker.get_metadata()
        return jsonify(data)

    @app.route("/api/status")
    def api_status():
        metadata = tracker.get_metadata()
        next_update = compute_next_update_timestamp(settings, metadata.get("lastUpdate", 0))
        stats = database.get_stats()
        return jsonify(
            {
                "status": "online",
                "lastUpdate": metadata.get("lastUpdate", 0),
                "nextUpdate": next_update,
                "totalVideos": metadata.get("totalVideos", 0),
                "dbStats": stats,
            }
        )

    @app.route("/api/history/<video_id>")
    def api_video_history(video_id: str):
        history = database.get_video_history(video_id)
        return jsonify({"videoId": video_id, "history": history})

    @app.route("/api/ranking-history")
    def api_ranking_history():
        engine = database.get_engine()
        with engine.connect() as conn:
            trows = conn.execute(
                text(
                    """
                    SELECT DISTINCT timestamp
                    FROM history
                    ORDER BY timestamp ASC
                    """
                )
            ).fetchall()
        timestamps = [row._mapping["timestamp"] for row in trows]

        ranking_evolution: Dict[str, Dict] = {}
        for ts in timestamps:
            with engine.connect() as conn:
                videos_at_timestamp = conn.execute(
                    text(
                        """
                        SELECT h.video_id, v.title, h.view_count, v.published_at
                        FROM history h
                        JOIN videos v ON h.video_id = v.video_id
                        WHERE h.timestamp = :ts
                        ORDER BY h.view_count DESC
                        """
                    ),
                    {"ts": ts},
                ).fetchall()

            for rank, video in enumerate(videos_at_timestamp, 1):
                video_id = video._mapping["video_id"]
                if video_id not in ranking_evolution:
                    ranking_evolution[video_id] = {
                        "videoId": video_id,
                        "title": video._mapping["title"],
                        "publishedAt": video._mapping["published_at"],
                        "history": [],
                    }
                ranking_evolution[video_id]["history"].append(
                    {"timestamp": ts, "rank": rank, "views": video._mapping["view_count"]}
                )

        for data in ranking_evolution.values():
            history = data["history"]
            if len(history) >= 2:
                latest_rank = history[-1]["rank"]
                previous_rank = history[-2]["rank"]
                data["rankChange"] = previous_rank - latest_rank
                data["currentRank"] = latest_rank
                data["previousRank"] = previous_rank
            elif history:
                data["rankChange"] = 0
                data["currentRank"] = history[0]["rank"]
                data["previousRank"] = history[0]["rank"]
            else:
                data["rankChange"] = 0
                data["currentRank"] = 0
                data["previousRank"] = 0

        videos_list = list(ranking_evolution.values())
        top_movers = sorted(videos_list, key=lambda x: x["rankChange"], reverse=True)[:10]
        biggest_fallers = sorted(videos_list, key=lambda x: x["rankChange"])[:10]

        return jsonify(
            {
                "timestamps": timestamps,
                "evolution": ranking_evolution,
                "topMovers": top_movers,
                "biggestFallers": biggest_fallers,
            }
        )

    @app.route("/api/update", methods=["POST"])
    def api_manual_update():
        from threading import Thread

        def update_async():
            tracker.update()

        Thread(target=update_async, daemon=True).start()
        return jsonify({"status": "Update started in background"})

    @app.route("/api/add-video/<video_id>", methods=["POST"])
    def api_add_specific_video(video_id: str):
        try:
            stats = tracker.api.get_video_statistics([video_id])
            items = stats.get("items", [])
            if not items:
                return jsonify({"error": f"Video {video_id} not found"}), 404
            item = items[0]
            title = item["snippet"]["title"]
            view_count = int(item["statistics"].get("viewCount", 0))
            published_at = item["snippet"].get("publishedAt", "")
            timestamp = int(time.time())

            database.save_video(video_id, title, view_count, timestamp, published_at)
            return jsonify(
                {
                    "status": "Video added successfully",
                    "videoId": video_id,
                    "title": title,
                    "views": view_count,
                }
            )
        except Exception as exc:
            return jsonify({"error": str(exc)}), 500

    @app.route("/api/analytics")
    def api_analytics():
        engine = database.get_engine()
        with engine.connect() as conn:
            video_rows = conn.execute(
                text(
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
                )
            ).fetchall()

        videos = []
        total_views = 0
        for rank, row in enumerate(video_rows, 1):
            mapping = row._mapping
            video_id = mapping["video_id"]
            current_views = mapping["current_views"]
            total_views += current_views

            with engine.connect() as conn:
                history_rows = conn.execute(
                    text(
                        """
                        SELECT timestamp, view_count
                        FROM history
                        WHERE video_id = :video_id
                        ORDER BY timestamp DESC
                        LIMIT 2
                        """
                    ),
                    {"video_id": video_id},
                ).fetchall()

            views_per_hour = 0
            views_change = 0
            hours_since_last = 0
            if len(history_rows) >= 2:
                latest = history_rows[0]._mapping
                previous = history_rows[1]._mapping
                time_diff = latest["timestamp"] - previous["timestamp"]
                hours_since_last = time_diff / 3600 if time_diff else 0
                if hours_since_last > 0:
                    views_change = latest["view_count"] - previous["view_count"]
                    views_per_hour = views_change / hours_since_last

            lifetime_views_per_hour = 0
            published_at = mapping["published_at"]
            if published_at:
                from datetime import datetime, timezone

                published = datetime.fromisoformat(published_at.replace("Z", "+00:00"))
                now = datetime.now(timezone.utc)
                age_hours = (now - published).total_seconds() / 3600
                if age_hours > 0:
                    lifetime_views_per_hour = current_views / age_hours

            videos.append(
                {
                    "videoId": video_id,
                    "title": mapping["title"],
                    "currentViews": current_views,
                    "currentRank": rank,
                    "publishedAt": published_at,
                    "viewsPerHour": round(views_per_hour, 2),
                    "viewsChange": views_change,
                    "hoursSinceLastUpdate": round(hours_since_last, 2),
                    "lifetimeViewsPerHour": round(lifetime_views_per_hour, 2),
                }
            )

        trending = sorted(videos, key=lambda x: x["viewsPerHour"], reverse=True)[:10]
        top_performers = sorted(videos, key=lambda x: x["lifetimeViewsPerHour"], reverse=True)[:10]

        metadata = tracker.get_metadata()
        average_views = total_views / len(videos) if videos else 0
        average_growth = sum(v["viewsPerHour"] for v in videos) / len(videos) if videos else 0

        return jsonify(
            {
                "trending": trending,
                "topPerformers": top_performers,
                "statistics": {
                    "totalVideos": len(videos),
                    "totalViews": total_views,
                    "averageViews": round(average_views, 2),
                    "averageViewsPerHour": round(average_growth, 2),
                    "lastUpdate": metadata.get("lastUpdate", 0),
                },
            }
        )

    return app
