#!/usr/bin/env python3
"""LawMind RAG Pipeline Self-Test"""
import json
import urllib.request
import time

BASE_URL = "http://localhost:8080"
TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2IiwiaWF0IjoxNzc4MzEyNTM4LCJleHAiOjE3NzgzMTk3MzgsInR5cGUiOiJhY2Nlc3MifQ.8ZVIU92Gn4LuzkzEv0VYOqfnj7J7mgxpowIqBxeV8gc"

TESTS = [
    ("1", "试用期被公司强制辞退，没有提前通知，能要赔偿吗？", "Labor - Dismissal"),
    ("2", "我妻子出轨了，我想离婚，财产应该怎么分割？", "Marriage - Divorce"),
    ("3", "签订了合同但对方不履行，我可以要求哪些赔偿？", "Contract - Breach"),
    ("4", "偷窃价值5000元的东西会被判多久？", "Criminal - Theft"),
    ("5", "交通事故中对方全责但不赔偿，我该如何维权？", "Traffic - Compensation"),
    ("6", "今天天气怎么样？", "Non-legal Rejection"),
    ("7", "公司不给交社保怎么办？可以去哪里投诉？", "Social Security"),
    ("8", "劳动合同到期了公司不续签，有没有经济补偿？", "Labor Contract Expiry"),
    ("9", "在工地上受伤了，包工头不承认是工伤，怎么认定？", "Work Injury"),
]

def run_test(num, question, label):
    print(f"\n{'='*60}")
    print(f"Test {num}: {label}")
    print(f"Q: {question}")

    payload = json.dumps({"question": question}).encode("utf-8")
    req = urllib.request.Request(
        f"{BASE_URL}/api/ai-chat/ask",
        data=payload,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Authorization": f"Bearer {TOKEN}"
        }
    )

    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            body = json.loads(resp.read().decode("utf-8"))
    except Exception as e:
        print(f"ERROR: {e}")
        return

    elapsed = time.time() - t0
    code = body.get("code", "unknown")
    data = body.get("data", {})
    answer = data.get("answer", "")
    related = data.get("relatedKnowledge", [])

    print(f"Status: {code} | Time: {elapsed:.1f}s")
    print(f"Answer length: {len(answer)} chars")
    print(f"Related knowledge: {len(related)} items")

    # Show first 300 chars of answer
    preview = answer[:300].replace("\n", "\\n")
    print(f"Preview: {preview}...")

    # Show top knowledge items
    if related:
        for i, k in enumerate(related[:3]):
            title = k.get("title", "N/A")
            score = k.get("score", "N/A")
            law_type = k.get("lawType", "N/A")
            print(f"  [{i+1}] {title} | score={score} | type={law_type}")

    return {"label": label, "question": question, "code": code, "elapsed": elapsed,
            "answerLen": len(answer), "knowledgeCount": len(related), "answer": answer}

def main():
    print("=" * 60)
    print("  LawMind RAG Pipeline Self-Test")
    print("=" * 60)

    results = []
    for num, question, label in TESTS:
        result = run_test(num, question, label)
        if result:
            results.append(result)

    # Summary
    print(f"\n\n{'='*60}")
    print("  SUMMARY")
    print(f"{'='*60}")
    for r in results:
        status = "OK" if r["code"] == 200 else "FAIL"
        print(f"[{status}] {r['label']}: {r['elapsed']:.1f}s, {r['answerLen']} chars, {r['knowledgeCount']} knowledge items")

if __name__ == "__main__":
    main()
