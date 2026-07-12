<template>
  <div class="dashboard-container">
    <div class="dashboard-header">
      <h3>质量看板</h3>
      <div class="header-actions">
        <el-select v-model="trendDays" @change="loadAll" size="small">
          <el-option :value="7" label="最近7天" />
          <el-option :value="14" label="最近14天" />
          <el-option :value="30" label="最近30天" />
        </el-select>
        <el-button type="primary" size="small" :loading="loading" @click="loadAll">
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- 今日概览卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">今日请求量</div>
          <div class="stat-value">{{ overview.total ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">缓存命中率</div>
          <div class="stat-value">{{ pct(overview.cacheHitRate) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">好评率</div>
          <div class="stat-value">{{ pct(overview.feedbackRate) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">平均延迟</div>
          <div class="stat-value">{{ overview.avgLatencyMs ? overview.avgLatencyMs + 'ms' : '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="stat-cards second-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">LLM兜底率</div>
          <div class="stat-value" :class="{ warning: pctNum(quality.llmFallbackRate) > 30 }">
            {{ pct(quality.llmFallbackRate) }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">点赞 / 点踩</div>
          <div class="stat-value feedback-ratio">
            <span class="up">{{ quality.feedbackUp ?? overview.feedbackUp ?? 0 }}</span>
            /
            <span class="down">{{ quality.feedbackDown ?? overview.feedbackDown ?? 0 }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">P50 延迟</div>
          <div class="stat-value">{{ overview.p50LatencyMs ? overview.p50LatencyMs + 'ms' : '-' }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">P95 延迟</div>
          <div class="stat-value">{{ overview.p95LatencyMs ? overview.p95LatencyMs + 'ms' : '-' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 来源分布 & 延迟分解 -->
    <el-row :gutter="16" class="section-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><h4>来源分布</h4></template>
          <div v-if="overview.sourceDistribution" class="source-bars">
            <div v-for="(count, src) in overview.sourceDistribution" :key="src" class="source-item">
              <span class="source-name">{{ sourceLabel(src) }}</span>
              <el-progress
                :percentage="barPercent(count, overview.total)"
                :color="sourceColor(src)"
                :stroke-width="18"
              >
                <span class="progress-text">{{ count }}</span>
              </el-progress>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><h4>延迟分解（平均值）</h4></template>
          <div v-if="overview.latencyBreakdown" class="source-bars">
            <div v-for="(ms, phase) in overview.latencyBreakdown" :key="phase" class="source-item">
              <span class="source-name">{{ latencyLabel(phase) }}</span>
              <el-progress
                :percentage="latencyBarPercent(ms, overview.latencyBreakdown)"
                :color="latencyColor(phase)"
                :stroke-width="18"
              >
                <span class="progress-text">{{ ms }}ms</span>
              </el-progress>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 反馈原因分布 -->
    <el-row :gutter="16" class="section-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><h4>反馈原因分布</h4></template>
          <div v-if="hasFeedbackReasons(quality.feedbackReasons)" class="source-bars">
            <div v-for="(count, reason) in quality.feedbackReasons" :key="reason" class="source-item">
              <span class="source-name">{{ reasonLabel(reason) }}</span>
              <el-progress
                :percentage="barPercent(count, feedbackReasonsTotal(quality.feedbackReasons))"
                color="#e6a23c"
                :stroke-width="18"
              >
                <span class="progress-text">{{ count }}</span>
              </el-progress>
            </div>
          </div>
          <el-empty v-else description="暂无点踩反馈" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><h4>各来源请求量</h4></template>
          <div v-if="hasSourceCounts(quality)" class="source-bars">
            <div v-for="src in sourceKeys" :key="src" class="source-item">
              <span class="source-name">{{ sourceLabel(src) }}</span>
              <el-progress
                :percentage="qualitySourcePercent(src)"
                :color="sourceColor(src)"
                :stroke-width="18"
              >
                <span class="progress-text">{{ qualitySourceCount(src) }}</span>
              </el-progress>
            </div>
          </div>
          <el-empty v-else description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 质量趋势 -->
    <el-card shadow="hover" class="section-row">
      <template #header><h4>质量趋势（{{ trendDays }}天）</h4></template>
      <el-table v-if="trendData.length" :data="trendData" stripe size="small" max-height="400">
        <el-table-column prop="date" label="日期" width="110" />
        <el-table-column label="LLM兜底率" width="110">
          <template #default="{ row }">{{ pct(row.llmFallbackRate) }}</template>
        </el-table-column>
        <el-table-column label="好评率" width="100">
          <template #default="{ row }">{{ pct(row.feedbackRate) }}</template>
        </el-table-column>
        <el-table-column label="点赞" width="70">
          <template #default="{ row }">{{ row.feedbackUp ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="点踩" width="70">
          <template #default="{ row }">{{ row.feedbackDown ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="反馈原因" min-width="200">
          <template #default="{ row }">
            <template v-if="hasFeedbackReasons(row.feedbackReasons)">
              <el-tag
                v-for="(count, reason) in row.feedbackReasons"
                :key="reason"
                size="small"
                type="warning"
                style="margin-right: 4px; margin-bottom: 2px;"
              >
                {{ reasonLabel(reason) }}: {{ count }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无趋势数据" :image-size="60" />
    </el-card>

    <!-- 评估报告历史 -->
    <el-card shadow="hover" class="section-row">
      <template #header><h4>Golden Dataset 评估报告历史</h4></template>
      <el-table v-if="evalReports.length" :data="evalReports" stripe size="small" max-height="400">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="通过率" width="100">
          <template #default="{ row }">
            {{ row.totalCases ? Math.round(row.passedCases / row.totalCases * 100) + '%' : '-' }}
            <span class="detail-hint">({{ row.passedCases }}/{{ row.totalCases }})</span>
          </template>
        </el-table-column>
        <el-table-column label="关键词召回" width="100">
          <template #default="{ row }">{{ fmt(row.avgKeywordRecall) }}</template>
        </el-table-column>
        <el-table-column label="来源匹配" width="100">
          <template #default="{ row }">{{ fmt(row.avgSourceMatch) }}</template>
        </el-table-column>
        <el-table-column label="法条类型" width="90">
          <template #default="{ row }">{{ fmt(row.avgLawTypeMatch) }}</template>
        </el-table-column>
        <el-table-column label="忠实度" width="90">
          <template #default="{ row }">{{ fmt(row.avgFaithfulness) }}</template>
        </el-table-column>
        <el-table-column label="答案相关性" width="100">
          <template #default="{ row }">{{ fmt(row.avgAnswerRelevance) }}</template>
        </el-table-column>
        <el-table-column label="总分" width="80">
          <template #default="{ row }">{{ fmt(row.avgTotalScore) }}</template>
        </el-table-column>
        <el-table-column label="数据集" min-width="180">
          <template #default="{ row }">{{ row.datasetPath }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无评估报告" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from '../utils/axios'

const loading = ref(false)
const trendDays = ref(7)

const overview = ref({})
const quality = ref({})
const trendData = ref([])
const evalReports = ref([])

const sourceKeys = ['hot_cache', 'similar_question', 'law_knowledge', 'llm_direct']

async function loadAll() {
  loading.value = true
  try {
    const [todayRes, qualityRes, trendRes, evalRes] = await Promise.all([
      axios.get('/admin/metrics/today'),
      axios.get('/admin/metrics/quality/today'),
      axios.get('/admin/metrics/quality/trend', { days: trendDays.value }),
      axios.get('/admin/metrics/eval/reports', { limit: 20 }),
    ])
    overview.value = todayRes.data ?? {}
    quality.value = qualityRes.data ?? {}
    trendData.value = trendRes.data?.daily ?? []
    evalReports.value = evalRes.data ?? []
  } catch (e) {
    ElMessage.error('加载质量数据失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function pct(val) {
  if (val === null || val === undefined) return '-'
  return (val * 100).toFixed(1) + '%'
}

function pctNum(val) {
  if (val === null || val === undefined) return 0
  return Math.round(val * 100)
}

function fmt(val) {
  if (val === null || val === undefined) return '-'
  return Number(val).toFixed(2)
}

function barPercent(count, total) {
  if (!total || total === 0) return 0
  return Math.round((count / total) * 100)
}

function sourceLabel(src) {
  const map = {
    hot_cache: '热点缓存',
    similar_question: '相似问题',
    law_knowledge: '法律知识库',
    llm_direct: 'LLM直答',
    non_legal_reject: '非法律拒答',
    preprocess: '预处理',
    embedding: '向量化',
    search: '检索',
    generation: '生成',
  }
  return map[src] ?? src
}

function sourceColor(src) {
  const map = {
    hot_cache: '#67c23a',
    similar_question: '#409eff',
    law_knowledge: '#e6a23c',
    llm_direct: '#f56c6c',
    non_legal_reject: '#909399',
  }
  return map[src] ?? '#409eff'
}

function latencyLabel(phase) {
  const map = { preprocess: '预处理', embedding: '向量化', search: '检索', generation: '生成' }
  return map[phase] ?? phase
}

function latencyColor(phase) {
  const map = { preprocess: '#909399', embedding: '#409eff', search: '#e6a23c', generation: '#67c23a' }
  return map[phase] ?? '#409eff'
}

function latencyBarPercent(ms, breakdown) {
  const total = Object.values(breakdown).reduce((a, b) => a + b, 0)
  if (!total) return 0
  return Math.round((ms / total) * 100)
}

function reasonLabel(reason) {
  const map = {
    inaccurate: '回答不准确',
    wrong_citation: '法条引用错误',
    irrelevant: '答非所问',
    too_vague: '回答太模糊',
    other: '其他',
  }
  return map[reason] ?? reason
}

function hasFeedbackReasons(reasons) {
  return reasons && Object.keys(reasons).length > 0
}

function feedbackReasonsTotal(reasons) {
  if (!reasons) return 0
  return Object.values(reasons).reduce((a, b) => a + b, 0)
}

function hasSourceCounts(q) {
  if (!q) return false
  return sourceKeys.some(k => q['sourceCount_' + k] > 0)
}

function qualitySourceCount(src) {
  return quality.value?.['sourceCount_' + src] ?? 0
}

function qualitySourcePercent(src) {
  const total = sourceKeys.reduce((sum, k) => sum + qualitySourceCount(k), 0)
  return total ? Math.round((qualitySourceCount(src) / total) * 100) : 0
}

onMounted(loadAll)
</script>

<style scoped>
.dashboard-container {
  padding: 20px 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.dashboard-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.stat-cards {
  margin-bottom: 16px;
}

.stat-cards.second-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.stat-card .stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-card .stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-card .stat-value.warning {
  color: #f56c6c;
}

.feedback-ratio .up { color: #67c23a; }
.feedback-ratio .down { color: #f56c6c; }

.section-row {
  margin-bottom: 16px;
}

.section-row h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.source-bars {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.source-name {
  width: 90px;
  flex-shrink: 0;
  font-size: 13px;
  color: #606266;
  text-align: right;
}

.source-item .el-progress {
  flex: 1;
}

.progress-text {
  font-size: 12px;
  color: #fff;
}

.detail-hint {
  font-size: 11px;
  color: #c0c4cc;
}
</style>
