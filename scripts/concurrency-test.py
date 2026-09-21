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

# 修改这里可以调整默认测试配置；命令行参数会覆盖这些默认值。
DEFAULT_BASE_URL = "http://localhost:8080/api"
DEFAULT_ROOM_ID = "1"
DEFAULT_SECOND_ROOM_ID = None
DEFAULT_DATE = None  # None 表示明天，或填写 YYYY-MM-DD
DEFAULT_START_TIME = "10:00"
DEFAULT_END_TIME = "11:00"
DEFAULT_COUNT = 100
DEFAULT_TITLE = "Concurrency baseline"
DEFAULT_PARTICIPANT_COUNT = 1
DEFAULT_REMARK = "repeatable concurrency test"
DEFAULT_REQUEST_PREFIX = "concurrency"


def build_interval(date_text: str | None, start_time_text: str, end_time_text: str) -> tuple[datetime, datetime]:
    """将用户配置的日期和时间转换为后端使用的 LocalDateTime 文本。"""
    try:
        test_date = (
            datetime.strptime(date_text, "%Y-%m-%d").date()
            if date_text
            else (datetime.now() + timedelta(days=1)).date()
        )
        start_clock = datetime.strptime(start_time_text, "%H:%M").time()
        end_clock = datetime.strptime(end_time_text, "%H:%M").time()
    except ValueError as error:
        raise ValueError("日期必须是 YYYY-MM-DD，时间必须是 HH:MM。") from error

    start = datetime.combine(test_date, start_clock)
    end = datetime.combine(test_date, end_clock)
    if not start < end:
        raise ValueError("结束时间必须晚于开始时间，暂不支持跨日时间段。")
    return start, end


# call方法发送HTTP请求并返回状态码和响应体
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
    except urllib.error.URLError as error:
        return 0, {"error": str(error.reason)}


def main() -> int:
    parser = argparse.ArgumentParser()
    # 解析测试参数；命令行传值会覆盖脚本顶部的默认配置。
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--token", required=True)
    parser.add_argument("--room-id", default=DEFAULT_ROOM_ID)
    parser.add_argument(
        "--second-room-id",
        default=DEFAULT_SECOND_ROOM_ID,
        help="also run the same workload for a second room in parallel",
    )
    parser.add_argument("--date", default=DEFAULT_DATE, help="test date in YYYY-MM-DD format")
    parser.add_argument("--start-time", default=DEFAULT_START_TIME, help="start time in HH:MM format")
    parser.add_argument("--end-time", default=DEFAULT_END_TIME, help="end time in HH:MM format")
    parser.add_argument("--count", type=int, default=DEFAULT_COUNT)
    parser.add_argument("--title", default=DEFAULT_TITLE)
    parser.add_argument("--participant-count", type=int, default=DEFAULT_PARTICIPANT_COUNT)
    parser.add_argument("--remark", default=DEFAULT_REMARK)
    parser.add_argument("--request-prefix", default=DEFAULT_REQUEST_PREFIX)
    args = parser.parse_args()

    if args.count <= 0:
        parser.error("--count 必须大于 0。")
    if args.participant_count <= 0:
        parser.error("--participant-count 必须大于 0。")
    try:
        start, end = build_interval(args.date, args.start_time, args.end_time)
    except ValueError as error:
        parser.error(str(error))

    start_text = start.strftime("%Y-%m-%dT%H:%M:%S")
    end_text = end.strftime("%Y-%m-%dT%H:%M:%S")
    base_url = args.base_url.rstrip("/")
    room_ids = [str(args.room_id)]
    if args.second_room_id is not None and str(args.second_room_id) not in room_ids:
        room_ids.append(str(args.second_room_id))
    results: list[tuple[str, int, dict]] = []
    lock = threading.Lock()
    started_at = time.perf_counter()

    def submit(room_id: str, index: int) -> None:
        body = {
            "requestId": f"{args.request_prefix}-{room_id}-{uuid.uuid4()}-{index}",
            "roomId": room_id,
            "title": args.title,
            "startTime": start_text,
            "endTime": end_text,
            "participantCount": args.participant_count,
            "remark": args.remark,
        }
        result = call(f"{base_url}/reservations", args.token, "POST", body)
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
    print(
        f"parallel workload rooms={len(room_ids)} requests={len(results)} "
        f"interval={start_text}..{end_text} elapsed_seconds={elapsed:.3f}"
    )
    for room_id in room_ids:
        room_results = [(status, payload) for result_room, status, payload in results if result_room == room_id]
        codes: dict[int, int] = {}
        for status, _ in room_results:
            codes[status] = codes.get(status, 0) + 1
        print(f"room {room_id} HTTP results: {codes}")

        status, payload = call(
            f"{base_url}/reservations/calendar?start={start_text}&end={end_text}&roomId={room_id}",
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
        failed = failed or overlap or codes.get(201, 0) != 1
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
