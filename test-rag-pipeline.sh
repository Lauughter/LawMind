#!/bin/bash
# RAG Pipeline Self-Test Script
# Tests: intent classification, entity extraction, metadata filtering, hybrid search, MMR, compliance disclaimer

BASE_URL="http://localhost:8080"
TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2IiwiaWF0IjoxNzc4MzEyNTM4LCJleHAiOjE3NzgzMTk3MzgsInR5cGUiOiJhY2Nlc3MifQ.8ZVIU92Gn4LuzkzEv0VYOqfnj7J7mgxpowIqBxeV8gc"

echo "=========================================="
echo "  LawMind RAG Pipeline Self-Test"
echo "=========================================="

run_test() {
    local num=$1
    local question=$2
    local label=$3

    echo ""
    echo "--- Test $num: $label ---"
    echo "Q: $question"

    response=$(curl -s -X POST "$BASE_URL/api/ai-chat/ask" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        -d "{\"question\":\"$question\"}" 2>/dev/null)

    # Extract short summary from response
    code=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('code','no_code'))" 2>/dev/null || echo "parse_error")
    answer_preview=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); a=d.get('data',{}).get('answer',''); print(a[:200] if a else 'no_answer')" 2>/dev/null || echo "parse_error")

    echo "Code: $code"
    echo "Preview: $answer_preview"
    echo ""
}

# Test 1: Labor law - wages
run_test 1 "试用期被公司强制辞退，没有提前通知，能要赔偿吗？" "Labor Law - Unfair Dismissal"

# Test 2: Marriage law - divorce
run_test 2 "我妻子出轨了，我想离婚，财产应该怎么分割？" "Marriage Law - Divorce Property"

# Test 3: Contract law - breach
run_test 3 "签订了合同但对方不履行，我可以要求哪些赔偿？" "Contract Law - Breach"

# Test 4: Criminal law - theft
run_test 4 "偷窃价值5000元的东西会被判多久？" "Criminal Law - Theft"

# Test 5: Traffic accident
run_test 5 "交通事故中对方全责但不赔偿，我该如何维权？" "Traffic Law - Compensation"

# Test 6: Non-legal question (should be rejected)
run_test 6 "今天天气怎么样？" "Non-legal Rejection Test"

# Test 7: Social security
run_test 7 "公司不给交社保怎么办？可以去哪里投诉？" "Social Security - Complaint"

# Test 8: Labor contract
run_test 8 "劳动合同到期了公司不续签，有没有经济补偿？" "Labor Contract - Expiry"

# Test 9: Work injury
run_test 9 "在工地上受伤了，包工头不承认是工伤，怎么认定？" "Work Injury - Recognition"

echo "=========================================="
echo "  Test Complete - Check Backend Logs"
echo "=========================================="
