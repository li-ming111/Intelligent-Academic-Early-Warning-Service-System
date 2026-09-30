"""校验 teacher_write_probe_20260929_rollback.sql 是否把四张表复原到位。

用法（在仓库根 D:\\ABS 下）:
    python db_backups/verify_teacher_write_probe.py

判据不是"脚本跑完了"，而是"逐字段等于验证之前实测到的值"——
快照文件也在同目录，真出问题可以直接按它恢复那四张表。
"""
import sys

import pymysql

HOST, USER, PASSWORD, DB = "127.0.0.1", "root", "root", "academic_warning_system"

# 验证之前实测到的行内容（见 teacher_write_probe_20260929_rollback.sql 头部说明）
EXPECT_SCORE_RECORD_277 = {
    "score": 87.00, "score_level": 2, "score_total": 87.00, "grade": "A-",
    "grade_point": 3.70, "regular_score": 85.00, "final_score": 88.00,
    "remark": "第3学期高等数学", "semester": "3", "year": 2024,
}
EXPECT_SCORES_145 = {
    "score_total": 87.00, "regular_score": 85.00, "final_score": 88.00,
    "grade": "A-", "grade_point": 3.70,
}
EXPECT_FEEDBACK_1 = {"status": 0, "reply": None, "update_time": "2026-06-25 09:53:16"}
EXPECT_AUDIT_LOG_TOTAL = 1  # 验证前 score_audit_log 只有 1 行（max id = 1）

failures = []


def check(label, actual, expected):
    if actual == expected:
        print(f"  [OK]   {label} = {actual!r}")
    else:
        print(f"  [FAIL] {label} = {actual!r}，期望 {expected!r}")
        failures.append(label)


def main():
    conn = pymysql.connect(host=HOST, user=USER, password=PASSWORD, database=DB,
                           charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor)
    try:
        with conn.cursor() as cur:
            print("1) score_record#277")
            cur.execute("SELECT * FROM score_record WHERE id = 277")
            row = cur.fetchone()
            if row is None:
                print("  [FAIL] 行不存在")
                failures.append("score_record#277")
            else:
                for col, want in EXPECT_SCORE_RECORD_277.items():
                    got = row[col]
                    # DECIMAL 经 pymysql 出来是 Decimal，量化后比对
                    if isinstance(want, float):
                        got = float(got) if got is not None else None
                    check(f"score_record#277.{col}", got, want)

            print("2) scores#145")
            cur.execute("SELECT * FROM scores WHERE id = 145")
            row = cur.fetchone()
            if row is None:
                print("  [FAIL] 行不存在")
                failures.append("scores#145")
            else:
                for col, want in EXPECT_SCORES_145.items():
                    got = row[col]
                    if isinstance(want, float):
                        got = float(got) if got is not None else None
                    check(f"scores#145.{col}", got, want)

            print("3) feedback#1")
            cur.execute("SELECT id, status, reply, update_time FROM feedback WHERE id = 1")
            row = cur.fetchone()
            if row is None:
                print("  [FAIL] 行不存在")
                failures.append("feedback#1")
            else:
                for col, want in EXPECT_FEEDBACK_1.items():
                    got = row[col]
                    if col == "update_time":
                        got = str(got)
                    check(f"feedback#1.{col}", got, want)

            print("4) score_audit_log")
            cur.execute("SELECT COUNT(*) AS n FROM score_audit_log")
            check("总行数", cur.fetchone()["n"], EXPECT_AUDIT_LOG_TOTAL)
            cur.execute("SELECT COUNT(*) AS n FROM score_audit_log WHERE reason = %s",
                        ("写路径验证-临时",))
            check("验证残留行数", cur.fetchone()["n"], 0)

            print("5) 其他残留")
            cur.execute("SELECT COUNT(*) AS n FROM score_record WHERE remark = %s",
                        ("写路径验证-临时",))
            check("score_record 残留", cur.fetchone()["n"], 0)
            cur.execute("SELECT COUNT(*) AS n FROM feedback WHERE reply = %s",
                        ("写路径验证-临时回复",))
            check("feedback 残留", cur.fetchone()["n"], 0)
    finally:
        conn.close()

    if failures:
        print(f"\nFAIL  {len(failures)} 项不通过：{failures}")
        print("      可从 teacher_write_probe_20260929_before.sql 直接恢复那四张表。")
        return 1
    print("\nPASS  写路径验证的改动已全部复原，四张表逐字段等于验证之前")
    return 0


if __name__ == "__main__":
    sys.exit(main())
