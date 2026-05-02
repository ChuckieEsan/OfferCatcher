#!/usr/bin/env python3
"""
MQ 补偿脚本 — 将漏发消息的题目重新推送到 RabbitMQ。

背景：
    IngestFlowApplicationService 在题目入库后调用 RabbitMQProducer.publishTask()
    向 MQ 发送消息。如果 MQ 发送失败（网络抖动、MQ 不可达等），题目已写入 PostgreSQL
    但消息未送达，导致题目永无答案。本脚本补偿这类场景。

题目类型过滤（参考 resend_to_queue.py）：
    只补偿 knowledge / scenario / algorithm 三类需要 AI 生成答案的题目。
    project、behavioral、coding 类型不需要异步答案生成，跳过。

用法：
    # 预览（dry-run），不实际发送
    python scripts/compensate_mq.py --dry-run

    # 补偿最近 7 天内未生成答案的题目（默认过滤 knowledge/scenario/algorithm）
    python scripts/compensate_mq.py --days 7

    # 按公司补偿
    python scripts/compensate_mq.py --company 阿里巴巴

    # 自定义题目类型过滤
    python scripts/compensate_mq.py --types knowledge,algorithm,scenario

    # 限定补偿数量
    python scripts/compensate_mq.py --limit 50

    # 只补偿某时间点之后的题目
    python scripts/compensate_mq.py --after "2026-05-01 00:00:00"

    # 从 DLQ 重新投递（死信队列中的消息）
    python scripts/compensate_mq.py --from-dlq

依赖：
    pip install pika psycopg2-binary

环境变量（与项目配置一致）：
    POSTGRES_HOST, POSTGRES_PORT, POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB
    RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER, RABBITMQ_PASSWORD
"""

import argparse
import json
import os
import sys
from datetime import datetime, timedelta
from typing import Optional

import pika

# =============================================================================
# DB / MQ helpers
# =============================================================================


def get_db_connection():
    """获取 PostgreSQL 连接"""
    import psycopg2

    return psycopg2.connect(
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=int(os.getenv("POSTGRES_PORT", "5432")),
        user=os.getenv("POSTGRES_USER", "root"),
        password=os.getenv("POSTGRES_PASSWORD", "root"),
        dbname=os.getenv("POSTGRES_DB", "offer_catcher_v2"),
    )


def get_rabbitmq_channel():
    """获取 RabbitMQ channel"""
    params = pika.ConnectionParameters(
        host=os.getenv("RABBITMQ_HOST", "localhost"),
        port=int(os.getenv("RABBITMQ_PORT", "5672")),
        credentials=pika.PlainCredentials(
            username=os.getenv("RABBITMQ_USER", "guest"),
            password=os.getenv("RABBITMQ_PASSWORD", "guest"),
        ),
        heartbeat=600,
    )
    connection = pika.BlockingConnection(params)
    channel = connection.channel()
    channel.queue_declare(
        queue=os.getenv("RABBITMQ_QUEUE", "answer_tasks"), durable=True
    )
    return connection, channel


# =============================================================================
# Query helpers
# =============================================================================


# 需要异步生成答案的题目类型。
# 注意：数据库用 @Enumerated(EnumType.STRING) 存储枚举名，因此是大写。
DEFAULT_TYPES = ("KNOWLEDGE", "SCENARIO", "ALGORITHM")


def build_query(
    days: Optional[int] = None,
    after: Optional[str] = None,
    company: Optional[str] = None,
    types: Optional[list[str]] = None,
    limit: int = 500,
    offset: int = 0,
) -> tuple[str, list]:
    """构建查询未生成答案题目的 SQL"""
    where = ["(q.answer IS NULL OR q.answer = '')"]
    params: list = []

    # 题目类型过滤 — 只补偿需要 AI 生成答案的类型
    include_types = types if types else list(DEFAULT_TYPES)
    placeholders = ",".join(["%s"] * len(include_types))
    where.append(f"q.question_type IN ({placeholders})")
    params.extend(include_types)

    if days is not None and days > 0:
        cutoff = datetime.now() - timedelta(days=days)
        where.append("q.created_at >= %s")
        params.append(cutoff)

    if after is not None:
        where.append("q.created_at >= %s")
        params.append(after)

    if company is not None:
        where.append("q.company = %s")
        params.append(company)

    sql = f"""
        SELECT q.id, q.question_text, q.question_type, q.company, q.position,
               q.question_hash, q.created_at
        FROM questions q
        WHERE {' AND '.join(where)}
        ORDER BY q.created_at ASC
        LIMIT %s OFFSET %s
    """
    params.extend([limit, offset])
    return sql, params


def count_questions(
    days: Optional[int] = None,
    after: Optional[str] = None,
    company: Optional[str] = None,
    types: Optional[list[str]] = None,
) -> int:
    """统计待补偿题目数量"""
    where = ["(q.answer IS NULL OR q.answer = '')"]
    params: list = []

    include_types = types if types else list(DEFAULT_TYPES)
    placeholders = ",".join(["%s"] * len(include_types))
    where.append(f"q.question_type IN ({placeholders})")
    params.extend(include_types)

    if days is not None and days > 0:
        cutoff = datetime.now() - timedelta(days=days)
        where.append("q.created_at >= %s")
        params.append(cutoff)

    if after is not None:
        where.append("q.created_at >= %s")
        params.append(after)

    if company is not None:
        where.append("q.company = %s")
        params.append(company)

    sql = f"SELECT COUNT(*) FROM questions q WHERE {' AND '.join(where)}"
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchone()[0]
    finally:
        conn.close()


# =============================================================================
# DLQ helpers
# =============================================================================


def get_dlq_messages(channel, dlq_name: str, limit: int = 100) -> list:
    """从 DLQ 读取消息（不删除，只查看）"""
    msgs = []
    for _ in range(limit):
        method, properties, body = channel.basic_get(
            queue=dlq_name, auto_ack=False
        )
        if method is None:
            break
        try:
            task = json.loads(body)
            msgs.append({
                "delivery_tag": method.delivery_tag,
                "task": task,
                "headers": properties.headers or {},
            })
        except json.JSONDecodeError:
            print(f"[skip] DLQ 消息无法解析: {body[:100]}")
    return msgs


# =============================================================================
# Main logic
# =============================================================================


def compensate(
    dry_run: bool = False,
    days: Optional[int] = None,
    after: Optional[str] = None,
    company: Optional[str] = None,
    types: Optional[list[str]] = None,
    limit: int = 500,
    from_dlq: bool = False,
):
    """主补偿逻辑"""
    mq_queue = os.getenv("RABBITMQ_QUEUE", "answer_tasks")
    mq_dlq = os.getenv("RABBITMQ_DLQ", "answer_tasks_dlq")
    include_types = types if types else list(DEFAULT_TYPES)

    print(f"{'[DRY-RUN] ' if dry_run else ''}补偿脚本启动")
    print(f"  MQ 队列: {mq_queue}")
    print(f"  题目类型过滤: {include_types}")

    # --- establish MQ connection ---
    try:
        mq_conn, channel = get_rabbitmq_channel()
    except Exception as e:
        print(f"[FATAL] 无法连接 RabbitMQ: {e}")
        sys.exit(1)

    try:
        if from_dlq:
            # --- DLQ 补偿模式 ---
            msgs = get_dlq_messages(channel, mq_dlq, limit=limit)
            print(f"  DLQ ({mq_dlq}) 发现 {len(msgs)} 条待处理消息")

            processed = 0
            for m in msgs:
                task = m["task"]
                qid = task.get("questionId") or task.get("question_id", "?")
                try:
                    if not dry_run:
                        channel.basic_publish(
                            exchange="",
                            routing_key=mq_queue,
                            body=json.dumps(task, ensure_ascii=False),
                            properties=pika.BasicProperties(
                                delivery_mode=2,
                                content_type="application/json",
                            ),
                        )
                        channel.basic_ack(m["delivery_tag"])
                    processed += 1
                    print(f"  [{processed}/{len(msgs)}] DLQ→MQ: qId={qid}")
                except Exception as e:
                    print(f"  [ERROR] DLQ republish failed for {qid}: {e}")

            print(f"\n{'[DRY-RUN] ' if dry_run else ''}DLQ 补偿完成: {processed} 条")
            return

        # --- PostgreSQL 补偿模式 ---
        total = count_questions(days=days, after=after, company=company, types=include_types)
        if total == 0:
            print("  没有待补偿的题目（所有题目都有答案）")
            return

        print(f"  待补偿题目总数: {total}")

        published = 0
        errors = 0
        offset = 0

        while offset < total:
            sql, params = build_query(
                days=days, after=after, company=company, types=include_types,
                limit=min(limit, total - offset), offset=offset,
            )

            conn = get_db_connection()
            try:
                with conn.cursor() as cur:
                    cur.execute(sql, params)
                    rows = cur.fetchall()
            finally:
                conn.close()

            for row in rows:
                q_id, q_text, q_type, q_company, q_position, q_hash, created = row
                try:
                    message = {
                        "questionId": q_id,
                        "questionText": q_text,
                        "company": q_company or "",
                        "position": q_position or "",
                        "coreEntities": [],
                    }

                    if dry_run:
                        print(f"  [DRY-RUN] qId={q_id}  type={q_type}  company={q_company}  hash={q_hash}  {created}")
                    else:
                        channel.basic_publish(
                            exchange="",
                            routing_key=mq_queue,
                            body=json.dumps(message, ensure_ascii=False),
                            properties=pika.BasicProperties(
                                delivery_mode=2,
                                content_type="application/json",
                                message_id=str(q_id),
                            ),
                        )

                    published += 1
                    if published % 50 == 0:
                        print(f"  已发送: {published}/{total}")

                except Exception as e:
                    errors += 1
                    print(f"  [ERROR] qId={q_id}: {e}")

            offset += limit

        print(f"\n{'[DRY-RUN] ' if dry_run else ''}补偿完成: 发送 {published} 条, 失败 {errors} 条")

    finally:
        try:
            mq_conn.close()
        except Exception:
            pass


# =============================================================================
# CLI
# =============================================================================

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="MQ 补偿脚本 — 将漏发消息的题目重新推送至 RabbitMQ",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--dry-run", action="store_true", help="预览模式，不实际发送消息")
    parser.add_argument("--days", type=int, help="仅补偿最近 N 天内创建的题目")
    parser.add_argument("--after", type=str, help="仅补偿此时间之后创建的题目 (YYYY-MM-DD HH:MM:SS)")
    parser.add_argument("--company", type=str, help="仅补偿指定公司的题目")
    parser.add_argument("--types", type=str,
                        default=",".join(DEFAULT_TYPES),
                        help=f"补偿的题目类型，逗号分隔 (默认: {','.join(DEFAULT_TYPES)})")
    parser.add_argument("--limit", type=int, default=500, help="批次大小 (默认 500)")
    parser.add_argument("--from-dlq", action="store_true", help="从 DLQ 重新投递（而非从 PostgreSQL 补偿）")

    args = parser.parse_args()
    compensate(
        dry_run=args.dry_run,
        days=args.days,
        after=args.after,
        company=args.company,
        types=[t.strip() for t in args.types.split(",") if t.strip()],
        limit=args.limit,
        from_dlq=args.from_dlq,
    )
