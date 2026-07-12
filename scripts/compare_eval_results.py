#!/usr/bin/env python3
"""
Golden Dataset 评估结果基线对比工具。

比较最新的评估报告与基线，检测质量回归：
  - 低于基线 × 容忍阈值 → 标记为 REGRESSION
  - 高于基线 → 标记为 IMPROVEMENT
  - 在容忍范围内 → PASS

用法：
  python scripts/compare_eval_results.py                          # 与基线对比
  python scripts/compare_eval_results.py --update-baseline        # 用当前结果更新基线
  python scripts/compare_eval_results.py --report target/evaluation/golden-eval-report.json  # 指定报告路径

退出码：
  0 — 通过（无回归或仅改善）
  1 — 发现回归
  2 — 参数/文件错误
"""

import argparse
import json
import sys
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_REPORT = ROOT / "target" / "evaluation" / "golden-eval-report.json"
BASELINE_FILE = Path(__file__).resolve().parent / "eval-baseline.json"

# 各维度容忍阈值：当前值低于 baseline 超过此阈值才视为回归
REGRESSION_THRESHOLDS = {
    "sourceMatch": 0.05,
    "keywordRecall": 0.10,
    "lawTypeMatch": 0.05,
    "answerMinLength": 0.05,
    "forbiddenContentClean": 0.02,
    "minRetrievalOk": 0.05,
    "faithfulness": 0.10,
    "answerRelevance": 0.10,
    "totalScore": 0.05,
    "passedCases": 0.05,
}

# 默认阈值
DEFAULT_THRESHOLD = 0.05

# 指标显示名称
METRIC_LABELS = {
    "totalCases": "总用例数",
    "passedCases": "通过数",
    "failedCases": "失败数",
    "sourceMatch": "来源匹配率",
    "keywordRecall": "关键词召回率",
    "lawTypeMatch": "法条类型匹配率",
    "answerMinLength": "最低回答长度",
    "forbiddenContentClean": "禁止内容清洁率",
    "minRetrievalOk": "最低检索满足率",
    "faithfulness": "忠实度(RAGAS)",
    "answerRelevance": "答案相关性(RAGAS)",
    "totalScore": "综合得分",
}


def load_report(path):
    """加载评估报告 JSON。"""
    if not os.path.exists(path):
        print(f"[ERROR] 报告文件不存在: {path}", file=sys.stderr)
        sys.exit(2)
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_baseline():
    """加载基线文件。"""
    if not BASELINE_FILE.exists():
        print("[INFO] 基线文件不存在，本次结果将作为首次基线。", file=sys.stderr)
        return None
    with open(BASELINE_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def save_baseline(report):
    """保存当前报告作为新基线。"""
    baseline = extract_dimensions(report)
    BASELINE_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(BASELINE_FILE, "w", encoding="utf-8") as f:
        json.dump(baseline, f, ensure_ascii=False, indent=2)
    print(f"[OK] 基线已保存: {BASELINE_FILE}")


def extract_dimensions(report):
    """从报告中提取对比维度。"""
    dims = report.get("dimensionAverages", {})
    result = {
        "generatedAt": report.get("generatedAt", ""),
        "totalCases": report.get("totalCases", 0),
        "passedCases": report.get("passedCases", 0),
        "failedCases": report.get("failedCases", 0),
    }
    for k, v in dims.items():
        result[k] = v
    # passedCases 转换为比率
    if result["totalCases"] > 0:
        result["passedRate"] = result["passedCases"] / result["totalCases"]
    else:
        result["passedRate"] = 0.0
    return result


def compare(current, baseline):
    """返回对比结果列表。"""
    results = []
    regressions = 0

    for key in sorted(baseline.keys()):
        base_val = baseline[key]
        curr_val = current.get(key)

        if curr_val is None:
            continue
        if not isinstance(base_val, (int, float)) or not isinstance(curr_val, (int, float)):
            continue

        label = METRIC_LABELS.get(key, key)
        threshold = REGRESSION_THRESHOLDS.get(key, DEFAULT_THRESHOLD)
        diff = round(curr_val - base_val, 6)

        if diff < -threshold:
            status = "REGRESSION"
            regressions += 1
        elif diff > threshold:
            status = "IMPROVEMENT"
        else:
            status = "STABLE"

        results.append({
            "key": key,
            "label": label,
            "baseline": base_val,
            "current": curr_val,
            "diff": diff,
            "threshold": threshold,
            "status": status,
        })

    # 检查新增的维度
    for key in sorted(current.keys()):
        if key not in baseline and isinstance(current[key], (int, float)):
            label = METRIC_LABELS.get(key, key)
            results.append({
                "key": key,
                "label": label,
                "baseline": None,
                "current": current[key],
                "diff": None,
                "threshold": REGRESSION_THRESHOLDS.get(key, DEFAULT_THRESHOLD),
                "status": "NEW",
            })

    return results, regressions


def print_table(results, regressions):
    """打印对比结果表格。"""
    col_widths = {
        "label": 24,
        "baseline": 12,
        "current": 12,
        "diff": 10,
        "status": 14,
    }

    header = (
        f"{'指标':<{col_widths['label']}} "
        f"{'基线':>{col_widths['baseline']}} "
        f"{'当前':>{col_widths['current']}} "
        f"{'差异':>{col_widths['diff']}} "
        f"{'状态':<{col_widths['status']}}"
    )
    sep = "─" * len(header)

    print("\n" + sep)
    print("  评估基线对比报告")
    print(sep)
    print(header)
    print(sep)

    for r in results:
        key = r["key"]
        label = r["label"]
        base_str = f"{r['baseline']:.2%}" if r["baseline"] is not None else "  NEW     "
        curr_str = f"{r['current']:.2%}" if isinstance(r["current"], float) and abs(r["current"]) < 10 else f"{r['current']:>10.4f}" if isinstance(r['current'], float) else f"{r['current']:>10}"
        # Re-format for rates vs counts
        if "Cases" in label or "数" in label:
            base_str = f"{int(r['baseline']):>6}" if r["baseline"] is not None else "  NEW   "
            curr_str = f"{int(r['current']):>6}"
            diff_str = f"{int(r['diff']):>+6}" if r["diff"] is not None else "   -  "
        elif r["diff"] is not None:
            diff_str = f"{r['diff']:>+.2%}"
        else:
            diff_str = "   -    "

        status_display = {
            "REGRESSION": "[REGRESSION]",
            "IMPROVEMENT": "[IMPROVED]",
            "STABLE": "[STABLE]",
            "NEW": "[NEW]",
        }.get(r["status"], r["status"])

        print(
            f"{label:<{col_widths['label']}} "
            f"{base_str:>{col_widths['baseline']}} "
            f"{curr_str:>{col_widths['current']}} "
            f"{diff_str:>{col_widths['diff']}} "
            f"{status_display:<{col_widths['status']}}"
        )

    print(sep)
    if regressions > 0:
        print(f"  [FAIL] 发现 {regressions} 项回归")
    else:
        print(f"  [PASS] 未发现回归")
    print(sep + "\n")


def main():
    parser = argparse.ArgumentParser(description="Golden Dataset 评估基线对比")
    parser.add_argument("--report", type=str, default=str(DEFAULT_REPORT),
                        help="评估报告 JSON 路径")
    parser.add_argument("--update-baseline", action="store_true",
                        help="用当前报告更新基线")
    args = parser.parse_args()

    report = load_report(args.report)
    current = extract_dimensions(report)

    if args.update_baseline:
        save_baseline(report)
        print("[OK] 基线已更新，未进行对比。", file=sys.stderr)
        return

    baseline = load_baseline()
    if baseline is None:
        # 首次运行，保存基线
        save_baseline(report)
        print("[INFO] 已创建初始基线，跳过对比。", file=sys.stderr)
        return

    results, regressions = compare(current, baseline)
    print_table(results, regressions)

    if regressions > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
