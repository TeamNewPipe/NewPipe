#!/usr/bin/env python3
"""Compare full history hydration with the initial 100-row snapshot on host SQLite.

Uses the exported Room tables/indexes and 67,000 distinct watched streams by default.
This is a reproducible SQL/materialization experiment, not an Android UI benchmark.
Run HistoryInitialLoadingTest on a device for Room and first-layout measurements.
"""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from statistics import median
import sqlite3
from time import perf_counter


ROOT = Path(__file__).resolve().parent.parent
PROFILE = "history-benchmark"
INITIAL_SIZE = 100


def create_fixture(count):
    connection = sqlite3.connect(":memory:")
    schemas = ROOT / "app/schemas/org.schabi.newpipe.database.AppDatabase"
    schema = max(schemas.glob("*.json"), key=lambda path: int(path.stem))
    for entity in json.loads(schema.read_text())["database"]["entities"]:
        table = entity["tableName"]
        if table not in {"streams", "stream_history", "stream_state"}:
            continue
        for statement in [entity["createSql"], *(i["createSql"] for i in entity["indices"])]:
            connection.execute(statement.replace("${TABLE_NAME}", table))
    with connection:
        connection.executemany(
            """INSERT INTO streams
            (uid, service_id, url, title, stream_type, duration, uploader,
             uploader_url, thumbnail_url, view_count, upload_date, uploader_avatar_url)
            VALUES (?, 0, ?, ?, 'VIDEO_STREAM', 600, ?, ?, ?, ?, ?, ?)""",
            ((i, f"https://example.com/watch?v={i}", f"History video {i}",
              f"Channel {i % 1000}", f"https://example.com/channel/{i % 1000}",
              f"https://example.com/thumbnail/{i}.jpg", i * 20, 1_700_000_000_000 + i,
              f"https://example.com/avatar/{i % 1000}.jpg") for i in range(1, count + 1)),
        )
        connection.executemany(
            "INSERT INTO stream_history (stream_id, access_date, repeat_count, profile_id) "
            "VALUES (?, ?, ?, ?)",
            ((i, 1_750_000_000_000 + (i // 3) * 1000, 1 + i % 5, PROFILE)
             for i in range(1, count + 1)),
        )
        connection.executemany(
            "INSERT INTO stream_state (stream_id, progress_time, profile_id) VALUES (?, ?, ?)",
            ((i, i % 600 * 1000, PROFILE) for i in range(1, count + 1)),
        )
    return connection


def query(initial):
    bound = f"ORDER BY latestAccess DESC, stream_id ASC LIMIT {INITIAL_SIZE}" if initial else ""
    return f"""SELECT streams.*, history.stream_id, history.latestAccess, history.watchCount,
        COALESCE(state.progress_time, 0) AS progress_time
        FROM (
            SELECT stream_id, MAX(access_date) AS latestAccess, SUM(repeat_count) AS watchCount
            FROM stream_history WHERE profile_id = :profile GROUP BY stream_id {bound}
        ) AS history
        INNER JOIN streams ON streams.uid = history.stream_id
        LEFT JOIN stream_state AS state
            ON state.stream_id = streams.uid AND state.profile_id = :profile
        ORDER BY latestAccess DESC, history.stream_id ASC"""


def measure(connection, initial):
    start = perf_counter()
    cursor = connection.execute(query(initial), {"profile": PROFILE})
    executed = perf_counter()
    rows = cursor.fetchall()
    fetched = perf_counter()
    # Approximate rich object/date conversion separately from executing/fetching SQLite rows.
    columns = [column[0] for column in cursor.description]
    entries = [dict(zip(columns, row)) for row in rows]
    for entry in entries:
        for field in ("upload_date", "latestAccess"):
            entry[field] = datetime.fromtimestamp(entry[field] / 1000, timezone.utc)
    converted = perf_counter()
    return entries, [1000 * (executed - start), 1000 * (fetched - executed),
                     1000 * (converted - fetched), 1000 * (converted - start)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--streams", type=int, default=67_000)
    parser.add_argument("--runs", type=int, default=5)
    args = parser.parse_args()
    if args.streams < INITIAL_SIZE or args.runs < 1:
        parser.error("--streams must be at least 100 and --runs must be positive")
    with create_fixture(args.streams) as connection:
        full, _ = measure(connection, False)
        initial, _ = measure(connection, True)
        assert initial == full[:INITIAL_SIZE], "Initial snapshot differs from the full prefix"
        samples = {False: [], True: []}
        for run in range(args.runs):
            # Alternate order to reduce cache/order bias; both measurements are warm-cache.
            for bounded in ((False, True) if run % 2 == 0 else (True, False)):
                _, timing = measure(connection, bounded)
                samples[bounded].append(timing)
        print(f"Host SQLite {sqlite3.sqlite_version}; {args.streams:,} streams; "
              f"median of {args.runs} warm-cache runs (milliseconds)")
        print("Path          Rows    Execute     Fetch   Convert     Total")
        for bounded, label in ((False, "Full"), (True, "Initial")):
            values = [median(sample[i] for sample in samples[bounded]) for i in range(4)]
            count = INITIAL_SIZE if bounded else args.streams
            print(f"{label:10} {count:7,}" + "".join(f"{value:10.2f}" for value in values))
        print("The complete query still follows the initial snapshot; totals are not additive "
              "UI timings and do not predict device speedups.")


if __name__ == "__main__":
    main()
