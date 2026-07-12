import json
import urllib.request
import sys
import io
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE_URL = "http://localhost:8080"
TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2IiwiaWF0IjoxNzc4MzEyNTM4LCJleHAiOjE3NzgzMTk3MzgsInR5cGUiOiJhY2Nlc3MifQ.8ZVIU92Gn4LuzkzEv0VYOqfnj7J7mgxpowIqBxeV8gc"

TESTS = [
    ("试用期被公司强制辞退，没有提前通知，能要赔偿吗？", "Labor-Dismissal"),
    ("我妻子出轨了，我想离婚，财产应该怎么分割？", "Marriage-Divorce"),
    ("签订了合同但对方不履行，我可以要求哪些赔偿？", "Contract-Breach"),
    ("偷窃价值5000元的东西会被判多久？", "Criminal-Theft"),
    ("交通事故中对方全责但不赔偿，我该如何维权？", "Traffic-Compensation"),
    ("今天天气怎么样？", "NonLegal-Reject"),
    ("公司不给交社保怎么办？可以去哪里投诉？", "SocialSecurity"),
    ("劳动合同到期了公司不续签，有没有经济补偿？", "LaborContract-Expiry"),
    ("在工地上受伤了，包工头不承认是工伤，怎么认定？", "WorkInjury"),
]

def run_one(question, label):
    payload = json.dumps({"question": question}, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(
        f"{BASE_URL}/api/ai-chat/ask",
        data=payload,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Authorization": f"Bearer {TOKEN}"
        },
        method='POST'
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            body = json.loads(r.read().decode('utf-8'))
    except Exception as e:
        return {"label": label, "error": str(e), "elapsed": time.time() - t0}

    elapsed = time.time() - t0
    data = body.get("data", {})
    answer = data.get("answer", "")
    rk = data.get("relatedKnowledge", [])

    return {
        "label": label,
        "code": body.get("code"),
        "elapsed": round(elapsed, 2),
        "answerLen": len(answer),
        "knowledgeCount": len(rk),
        "answer": answer,
        "knowledge": [{"title": k.get("title",""), "score": k.get("score",""), "lawType": k.get("lawType","")} for k in rk[:5]]
    }

print("=" * 60)
print("  LawMind RAG Pipeline Test")
print("=" * 60)

results = []
for question, label in TESTS:
    print(f"\n--- {label} ---")
    print(f"Q: {question}")
    sys.stdout.flush()
    r = run_one(question, label)
    results.append(r)
    if "error" in r:
        print(f"  ERROR: {r['error']}")
    else:
        print(f"  Code: {r['code']} | Time: {r['elapsed']}s | Answer: {r['answerLen']} chars | Knowledge: {r['knowledgeCount']} items")
        for k in r.get("knowledge", []):
            print(f"    - [{k['lawType']}] {k['title']} (score={k['score']})")
        has_disclaimer = "不构成法律建议" in r.get("answer", "")
        print(f"  Disclaimer: {'YES' if has_disclaimer else 'MISSING'}")

print("\n" + "=" * 60)
print("  SUMMARY")
print("=" * 60)
for r in results:
    if "error" in r:
        print(f"  FAIL  {r['label']}: {r['error']}")
    elif r["code"] != 200:
        print(f"  FAIL  {r['label']}: code={r['code']}")
    else:
        print(f"  OK    {r['label']}: {r['elapsed']}s, {r['answerLen']} chars, {r['knowledgeCount']} knowledge")

# Save detailed results
with open("test_results.json", "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)
print(f"\nDetailed results saved to test_results.json")
