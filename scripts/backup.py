#!/usr/bin/env python3
"""
OfferCatcher 数据备份脚本。

备份范围：
  - PostgreSQL (Agent-Pgvector) → offer_catcher_v2.sql
  - Neo4j (Agent-Neo4j)        → neo4j.dump
  - Grafana (grafana)           → grafana_dashboards/*.json

用法:
  python3 scripts/backup.py
  python3 scripts/backup.py --skip neo4j    # 跳过某个服务
  python3 scripts/backup.py --dry-run       # 只打印，不执行
"""

import argparse
import os
import json
import shutil
import subprocess
import sys
import urllib.request
from datetime import datetime

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
BACKUP_ROOT = os.path.join(PROJECT_DIR, "backups")

CONTAINERS = {
    "postgres": "Agent-Pgvector",
    "neo4j":   "Agent-Neo4j",
    "grafana": "grafana",
}

GRAFANA_URL = "http://admin:liuchenyu222@localhost:3000"
PG_DATABASE = "offer_catcher_v2"
PG_USER     = "root"


def run(cmd: list[str], dry_run: bool = False) -> subprocess.CompletedProcess:
    if dry_run:
        print(f"  [DRY-RUN] {' '.join(cmd)}")
        return subprocess.CompletedProcess(args=cmd, returncode=0, stdout="")
    return subprocess.run(cmd, capture_output=True, text=True)


def check_container(name: str) -> bool:
    r = subprocess.run(
        ["docker", "inspect", name, "--format", "{{.State.Status}}"],
        capture_output=True, text=True
    )
    if r.returncode != 0:
        print(f"  [跳过] 容器 {name} 不存在，跳过")
        return False
    status = r.stdout.strip()
    print(f"  容器 {name}: {status}")
    return True


def get_container_image(name: str) -> str:
    r = subprocess.run(
        ["docker", "inspect", name, "--format", "{{.Config.Image}}"],
        capture_output=True, text=True
    )
    return r.stdout.strip()


# ─── PostgreSQL ───────────────────────────────────────────────────────────────

def backup_postgres(backup_path: str, dry_run: bool) -> bool:
    name = CONTAINERS["postgres"]
    header("PostgreSQL", name)
    if not check_container(name):
        return False

    sql_file = os.path.join(backup_path, "offer_catcher_v2.sql")
    cmd = [
        "docker", "exec", name,
        "pg_dump", "-U", PG_USER, PG_DATABASE,
    ]
    if dry_run:
        print(f"  [DRY-RUN] pg_dump → {sql_file}")
        return True

    print(f"  pg_dump {PG_DATABASE} ...")
    with open(sql_file, "w") as f:
        r = subprocess.run(cmd, stdout=f, stderr=subprocess.PIPE, text=True)
    if r.returncode == 0:
        size_mb = os.path.getsize(sql_file) / 1024 / 1024
        print(f"  [OK] {size_mb:.1f} MB")
        return True
    else:
        print(f"  [FAIL] {r.stderr.strip()}")
        return False


# ─── Neo4j ────────────────────────────────────────────────────────────────────

def backup_neo4j(backup_path: str, dry_run: bool) -> bool:
    name = CONTAINERS["neo4j"]
    header("Neo4j", name)
    if not check_container(name):
        return False

    dump_file = os.path.join(backup_path, "neo4j.dump")
    image = get_container_image(name)
    print(f"  image: {image}")

    if dry_run:
        print(f"  [DRY-RUN] stop → dump → restart → {dump_file}")
        return True

    was_running = is_container_running(name)

    try:
        if was_running:
            print("  停止 Neo4j ...")
            subprocess.run(["docker", "stop", name],
                           capture_output=True, check=True)

        print("  neo4j-admin database dump neo4j ...")
        r = subprocess.run([
            "docker", "run", "--rm",
            "--volumes-from", name,
            image,
            "sh", "-c",
            "neo4j-admin database dump neo4j --to-path=/tmp --overwrite-destination >&2 && cat /tmp/neo4j.dump",
        ], capture_output=True)

        if r.returncode == 0 and r.stdout:
            with open(dump_file, "wb") as f:
                f.write(r.stdout)
            size_mb = os.path.getsize(dump_file) / 1024 / 1024
            print(f"  [OK] {size_mb:.1f} MB")
            return True
        else:
            stderr = r.stderr.decode(errors="replace") if r.stderr else ""
            print(f"  [FAIL] {stderr.strip()[-300:]}")
            return False

    finally:
        if was_running:
            print("  重启 Neo4j ...")
            subprocess.run(["docker", "start", name],
                           capture_output=True, check=True)


def is_container_running(name: str) -> bool:
    r = subprocess.run(
        ["docker", "inspect", name, "--format", "{{.State.Running}}"],
        capture_output=True, text=True
    )
    return r.stdout.strip() == "true"


# ─── Grafana ──────────────────────────────────────────────────────────────────

def grafana_api(path: str) -> list:
    url = f"{GRAFANA_URL}{path}"
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            return json.loads(resp.read())
    except Exception as e:
        print(f"  [WARN] Grafana API 不可达: {e}")
        return []


def backup_grafana(backup_path: str, dry_run: bool) -> bool:
    name = CONTAINERS["grafana"]
    header("Grafana", name)
    if not check_container(name):
        return False

    grafana_dir = os.path.join(backup_path, "grafana_dashboards")
    if dry_run:
        print(f"  [DRY-RUN] export dashboards → {grafana_dir}/")
        return True

    dashboards = grafana_api("/api/search?type=dash-db")
    if not dashboards and not _grafana_reachable():
        print("  Grafana API 不可达，尝试文件拷贝...")
        return _backup_grafana_files(grafana_dir)

    os.makedirs(grafana_dir, exist_ok=True)
    count = 0
    for d in dashboards:
        full = grafana_api(f"/api/dashboards/uid/{d['uid']}")
        if not full:
            continue
        title = d["title"].replace("/", "_")
        fpath = os.path.join(grafana_dir, f"{title}.json")
        with open(fpath, "w") as f:
            json.dump(full["dashboard"], f, indent=2, ensure_ascii=False)
        count += 1

    print(f"  [OK] {count} dashboards")
    return True


def _grafana_reachable() -> bool:
    try:
        urllib.request.urlopen(f"{GRAFANA_URL}/api/health", timeout=5)
        return True
    except Exception:
        return False


def _backup_grafana_files(grafana_dir: str) -> bool:
    """从 Grafana 数据卷直接拷贝 sqlite 数据库。"""
    name = CONTAINERS["grafana"]
    os.makedirs(grafana_dir, exist_ok=True)
    r = subprocess.run([
        "docker", "cp", f"{name}:/var/lib/grafana/grafana.db",
        os.path.join(grafana_dir, "grafana.db"),
    ], capture_output=True, text=True)
    if r.returncode == 0:
        print(f"  [OK] 已拷贝 grafana.db")
        return True
    print(f"  [FAIL] {r.stderr.strip()}")
    return False


# ─── 工具函数 ──────────────────────────────────────────────────────────────────

def header(title: str, container: str):
    print(f"\n{'─' * 60}")
    print(f"  {title}  [{container}]")
    print(f"{'─' * 60}")


def main():
    parser = argparse.ArgumentParser(description="OfferCatcher 数据备份")
    parser.add_argument("--skip", nargs="*", default=[],
                        choices=["postgres", "neo4j", "grafana"],
                        help="跳过的服务")
    parser.add_argument("--only", nargs="*", default=[],
                        choices=["postgres", "neo4j", "grafana"],
                        help="仅备份指定服务")
    parser.add_argument("--dry-run", action="store_true",
                        help="只打印计划，不执行")
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = os.path.join(BACKUP_ROOT, timestamp)
    os.makedirs(backup_path, exist_ok=True)

    services = ["postgres", "neo4j", "grafana"]
    if args.only:
        services = args.only

    print(f"备份目标: {backup_path}")
    if args.dry_run:
        print("[DRY-RUN 模式]\n")

    results = {}
    for svc in services:
        if svc in args.skip:
            print(f"\n  [跳过] {svc}")
            continue

        fn = {
            "postgres": backup_postgres,
            "neo4j":   backup_neo4j,
            "grafana": backup_grafana,
        }[svc]
        results[svc] = fn(backup_path, args.dry_run)

    # 清理空目录
    if not os.listdir(backup_path):
        os.rmdir(backup_path)

    # 汇总
    print(f"\n{'=' * 60}")
    print("  备份结果")
    print(f"{'=' * 60}")
    all_ok = True
    for svc, ok in results.items():
        status = "[OK]" if ok else "[FAIL]"
        print(f"  {status}  {svc}")
        if not ok:
            all_ok = False

    if not all_ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
