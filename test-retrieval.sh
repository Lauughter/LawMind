#!/bin/bash
# LawMind 检索优化测试脚本
# 使用方法: 先替换 TOKEN，然后 bash test-retrieval.sh

TOKEN="<你的JWT_TOKEN>"
BASE="http://localhost:8080/api"

echo "========================================="
echo "LawMind 检索优化测试"
echo "========================================="

# 测试1: 劳动法咨询
echo -e "\n[测试1] 劳动法咨询 - 意图:LEGAL_CONSULTATION, 实体:劳动"
curl -s -X POST "$BASE/ai-chat/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question":"被公司无故辞退，工作3年了，能拿多少赔偿？"}' | python -c "import sys,json; d=json.load(sys.stdin); print('答案:', d['data']['answer'][:200])" 2>/dev/null || echo "(直接查看返回结果)"

# 测试2: 法条查询
echo -e "\n[测试2] 法条查询 - 意图:ARTICLE_LOOKUP"
curl -s -X POST "$BASE/ai-chat/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question":"劳动合同法第47条是怎么规定的？"}' | python -c "import sys,json; d=json.load(sys.stdin); print('答案:', d['data']['answer'][:200])" 2>/dev/null || echo "(直接查看返回结果)"

# 测试3: 婚姻法
echo -e "\n[测试3] 婚姻法 - 实体:婚姻"
curl -s -X POST "$BASE/ai-chat/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question":"离婚后财产怎么分割？房子是婚前买的"}' | python -c "import sys,json; d=json.load(sys.stdin); print('答案:', d['data']['answer'][:200])" 2>/dev/null || echo "(直接查看返回结果)"

# 测试4: 交通安全
echo -e "\n[测试4] 交通安全 - 实体:交通"
curl -s -X POST "$BASE/ai-chat/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question":"交通事故对方全责，我受伤住院了，怎么索赔？"}' | python -c "import sys,json; d=json.load(sys.stdin); print('答案:', d['data']['answer'][:200])" 2>/dev/null || echo "(直接查看返回结果)"

# 测试5: 网络诈骗
echo -e "\n[测试5] 网络诈骗 - 实体:刑法+网络安全"
curl -s -X POST "$BASE/ai-chat/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question":"被网络诈骗了5万元，报警后能追回来吗？"}' | python -c "import sys,json; d=json.load(sys.stdin); print('答案:', d['data']['answer'][:200])" 2>/dev/null || echo "(直接查看返回结果)"

# 测试6: 非法律问题拒绝
echo -e "\n[测试6] 非法律问题 - 应拒绝"
curl -s -X POST "$BASE/ai-chat/ask" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question":"今天天气怎么样？"}' | python -c "import sys,json; d=json.load(sys.stdin); print('答案:', d['data']['answer'][:100])" 2>/dev/null || echo "(直接查看返回结果)"

echo -e "\n========================================="
echo "测试完成！请查看服务端日志验证:"
echo "  - 意图分类: grep '意图:' logs/"
echo "  - 实体抽取: grep '实体抽取' logs/"
echo "  - 元数据过滤: grep 'lawType=' logs/"
echo "  - 合规声明: 检查回答末尾的免责声明"
echo "========================================="
