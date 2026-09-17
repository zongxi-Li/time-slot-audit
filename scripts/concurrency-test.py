# 文件职责：预约并发验证脚本，模拟多个客户端访问预约接口并统计结果。
# 接口：调用后端 /api/auth 和 /api/reservations。
"""Repeatable 100-request baseline check for one room/time interval.

Usage:
  python scripts/concurrency-test.py --token <JWT> --room-id 1
  python scripts/concurrency-test.py --token <JWT> --room-id 1 --second-room-id 2

The script expects the backend to be running and uses only Python's standard library.
It reports HTTP results and then queries the calendar endpoint to verify that the returned
PENDING/CONFIRMED intervals do not overlap. It does not mutate schema or delete data.
"""

from __future__ import annotations

import argparse
import json
import threading
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta


def call(url: str, token: str, method: str, body: dict | None = None) -> tuple[int, dict]:
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080/api")
    parser.add_argument("--token", required=True)
    parser.add_argument("--room-id", default="1")
    parser.add_argument(
        "--second-room-id",
        default=None,
        help="also run the same workload for a second room in parallel",
    )
    parser.add_argument("--count", type=int, default=100)
    args = parser.parse_args()

    start = (datetime.now() + timedelta(days=1)).replace(hour=10, minute=0, second=0, microsecond=0)
    end = start + timedelta(hours=1)
    start_text = start.strftime("%Y-%m-%dT%H:%M:%S")
    end_text = end.strftime("%Y-%m-%dT%H:%M:%S")
    room_ids = [str(args.room_id)]
    if args.second_room_id is not None and str(args.second_room_id) not in room_ids:
        room_ids.append(str(args.second_room_id))
    results: list[tuple[str, int, dict]] = []
    lock = threading.Lock()
    started_at = time.perf_counter()

    def submit(room_id: str, index: int) -> None:
        body = {
            "requestId": f"concurrency-{room_id}-{uuid.uuid4()}-{index}",
            "roomId": room_id,
            "title": "Concurrency baseline",
            "startTime": start_text,
            "endTime": end_text,
            "participantCount": 1,
            "remark": "repeatable concurrency test",
        }
        result = call(f"{args.base_url}/reservations", args.token, "POST", body)
        with lock:
            results.append((room_id, *result))

    threads = [
        threading.Thread(target=submit, args=(room_id, index))
        for room_id in room_ids
        for index in range(args.count)
    ]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    elapsed = time.perf_counter() - started_at

    failed = False
    print(f"parallel workload rooms={len(room_ids)} requests={len(results)} elapsed_seconds={elapsed:.3f}")
    for room_id in room_ids:
        room_results = [(status, payload) for result_room, status, payload in results if result_room == room_id]
        codes: dict[int, int] = {}
        for status, _ in room_results:
            codes[status] = codes.get(status, 0) + 1
        print(f"room {room_id} HTTP results: {codes}")

        status, payload = call(
            f"{args.base_url}/reservations/calendar?start={start_text}&end={end_text}&roomId={room_id}",
            args.token,
            "GET",
        )
        if status != 200:
            print(f"room {room_id} calendar query failed: HTTP {status} {payload}")
            failed = True
            continue
        reservations = payload.get("data") or []
        intervals = sorted(
            (item["startTime"], item["endTime"], item["status"]) for item in reservations
            if item["status"] in {"PENDING", "CONFIRMED"}
        )
        overlap = any(left[1] > right[0] for left, right in zip(intervals, intervals[1:]))
        print(f"room {room_id} effective intervals returned: {len(intervals)}; overlap: {overlap}")
        failed = failed or overlap or codes.get(201, 0) > 1
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
