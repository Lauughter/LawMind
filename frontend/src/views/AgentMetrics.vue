<template>
  <div class="metrics-dashboard">
    <!-- 页面标题栏 -->
    <div class="dashboard-header">
      <h2>Agent 监控看板</h2>
      <div class="header-actions">
        <span class="last-update" v-if="lastUpdateTime">
          上次更新：{{ lastUpdateTime }}
        </span>
        <el-button :icon="Refresh" @click="fetchMetrics" :loading="loading" size="default">
          刷新
        </el-button>
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          inactive-text="手动"
          @change="onAutoRefreshChange"
        />
      </div>
    </div>

    <!-- 权限拦截提示 -->
    <el-result
      v-if="forbidden"
      icon="warning"
      title="无权访问"
      sub-title="仅管理员用户可查看 Agent 监控数据"
    >
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>

    <!-- 看板内容 -->
    <template v-if="!forbidden">
      <!-- 概览卡片 -->
      <el-row :gutter="20" class="summary-row">
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="metric-card metric-card--primary">
            <div class="metric-value">{{ metrics.totalAgentCalls ?? '-' }}</div>
            <div class="metric-label">Agent 请求总数</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="metric-card metric-card--success">
            <div class="metric-value">{{ metrics.totalToolCalls ?? '-' }}</div>
            <div class="metric-label">工具调用次数</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="metric-card metric-card--warning">
            <div class="metric-value">{{ metrics.totalFallbackCalls ?? '-' }}</div>
            <div class="metric-label">降级次数</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card shadow="hover" class="metric-card metric-card--info">
            <div class="metric-value">{{ avgToolCalls }}</div>
            <div class="metric-label">平均工具调用 / 请求</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 压缩统计卡片 -->
      <el-row :gutter="20" class="summary-row">
        <el-col :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="metric-card metric-card--compress">
            <div class="metric-value">{{ metrics.totalCompressions ?? '-' }}</div>
            <div class="metric-label">压缩总次数</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="metric-card metric-card--saved">
            <div class="metric-value">{{ formatTokens(metrics.estimatedTokensSaved) }}</div>
            <div class="metric-label">估算节省 Token</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="8">
          <el-card shadow="hover" class="metric-card metric-card--atoms">
            <div class="metric-value">{{ totalKnowledgeAtoms }}</div>
            <div class="metric-label">知识原子总数</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 知识原子分布 -->
      <el-row :gutter="20" class="detail-row" v-if="atomEntries.length > 0">
        <el-col :span="14">
          <el-card shadow="hover" class="tool-card">
            <template #header>
              <div class="card-header">
                <span>知识原子分布</span>
                <span class="card-tip">article: 法条 | calc: 金额 | reminder: 时效 | case: 案例</span>
              </div>
            </template>
            <div v-for="[type, count] in atomEntries" :key="type" class="tool-item">
              <div class="tool-name">{{ atomTypeLabel(type) }}</div>
              <div class="tool-bar-wrap">
                <div
                  class="tool-bar tool-bar--atom"
                  :style="{ width: atomBarWidth(count) + '%' }"
                  :title="type + ': ' + count + ' 个'"
                ></div>
              </div>
              <div class="tool-count">{{ count }} 个</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 工具调用分布 + 运行信息 -->
      <el-row :gutter="20" class="detail-row">
        <el-col :span="14">
          <el-card shadow="hover" class="tool-card">
            <template #header>
              <div class="card-header">
                <span>工具调用分布</span>
                <span class="card-tip">点击柱状条查看详情</span>
              </div>
            </template>
            <div v-if="toolEntries.length === 0" class="empty-hint">
              暂无工具调用记录
            </div>
            <div v-for="[name, count] in toolEntries" :key="name" class="tool-item">
              <div class="tool-name">{{ name }}</div>
              <div class="tool-bar-wrap">
                <div
                  class="tool-bar"
                  :style="{ width: barWidth(count) + '%' }"
                  :title="'调用 ' + count + ' 次'"
                ></div>
              </div>
              <div class="tool-count">{{ count }} 次</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="hover" class="info-card">
            <template #header>
              <span>运行信息</span>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="监控起始时间">
                {{ formatTime(metrics.startTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="工具种类数">
                {{ toolEntries.length }}
              </el-descriptions-item>
              <el-descriptions-item label="最常用工具">
                {{ topTool }}
              </el-descriptions-item>
              <el-descriptions-item label="上次更新">
                {{ lastUpdateTime || '未获取' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { Refresh, TrendCharts } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import request from '../utils/axios';

const router = useRouter();

const loading = ref(false);
const forbidden = ref(false);
const autoRefresh = ref(false);
const lastUpdateTime = ref('');
let refreshTimer = null;

const metrics = ref({
  totalAgentCalls: null,
  totalToolCalls: null,
  totalFallbackCalls: null,
  toolCallCounts: {},
  startTime: '',
});

const toolEntries = computed(() => {
  const map = metrics.value.toolCallCounts || {};
  return Object.entries(map).sort((a, b) => b[1] - a[1]);
});

const atomEntries = computed(() => {
  const map = metrics.value.knowledgeStateAtomCounts || {};
  return Object.entries(map).sort((a, b) => b[1] - a[1]);
});

const maxAtomCount = computed(() => {
  if (atomEntries.value.length === 0) return 1;
  return Math.max(...atomEntries.value.map(([, c]) => c));
});

const totalKnowledgeAtoms = computed(() => {
  return atomEntries.value.reduce((sum, [, c]) => sum + c, 0) || '-';
});

const maxToolCount = computed(() => {
  if (toolEntries.value.length === 0) return 1;
  return Math.max(...toolEntries.value.map(([, c]) => c));
});

const avgToolCalls = computed(() => {
  const total = metrics.value.totalAgentCalls;
  const tools = metrics.value.totalToolCalls;
  if (!total || total === 0) return '-';
  return (tools / total).toFixed(1);
});

const topTool = computed(() => {
  if (toolEntries.value.length === 0) return '无';
  return toolEntries.value[0][0];
});

function barWidth(count) {
  return Math.max((count / maxToolCount.value) * 100, 2);
}

function formatTime(str) {
  if (!str) return '-';
  try {
    return new Date(str).toLocaleString('zh-CN');
  } catch {
    return str;
  }
}

function formatTokens(n) {
  if (n == null) return '-';
  if (n >= 10000) return (n / 10000).toFixed(1) + ' 万';
  if (n >= 1000) return (n / 1000).toFixed(1) + ' K';
  return String(n);
}

function atomBarWidth(count) {
  return Math.max((count / maxAtomCount.value) * 100, 2);
}

function atomTypeLabel(type) {
  const labels = {
    article: '法条引用',
    calc: '金额计算',
    reminder: '时效提醒',
    case: '参考案例',
  };
  return labels[type] || type;
}

async function fetchMetrics() {
  loading.value = true;
  try {
    const res = await request.get('/agent/metrics');
    if (res.success) {
      metrics.value = res.data;
      lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN');
      forbidden.value = false;
    }
  } catch (err) {
    if (err.message && err.message.includes('403')) {
      forbidden.value = true;
    } else {
      ElMessage.error('获取监控数据失败：' + (err.message || '未知错误'));
    }
  } finally {
    loading.value = false;
  }
}

function onAutoRefreshChange(val) {
  if (val) {
    refreshTimer = setInterval(fetchMetrics, 10000);
  } else {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

function goHome() {
  router.push('/home/consultation');
}

onMounted(() => {
  fetchMetrics();
});

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
  }
});
</script>

<style scoped>
.metrics-dashboard {
  padding: 24px 32px;
  max-width: 1200px;
  margin: 0 auto;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.dashboard-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.last-update {
  font-size: 13px;
  color: #909399;
}

/* 概览卡片 */
.summary-row {
  margin-bottom: 20px;
}

.metric-card {
  text-align: center;
  padding: 8px 0;
}

.metric-card .metric-value {
  font-size: 36px;
  font-weight: 700;
  line-height: 1.4;
}

.metric-card .metric-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.metric-card--primary .metric-value { color: #409eff; }
.metric-card--success .metric-value { color: #67c23a; }
.metric-card--warning .metric-value { color: #e6a23c; }
.metric-card--info .metric-value { color: #909399; }
.metric-card--compress .metric-value { color: #e040fb; }
.metric-card--saved .metric-value { color: #00bcd4; }
.metric-card--atoms .metric-value { color: #ff9800; }

/* 详情行 */
.detail-row {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-tip {
  font-size: 12px;
  color: #c0c4cc;
}

.empty-hint {
  text-align: center;
  color: #c0c4cc;
  padding: 32px 0;
}

/* 工具条 */
.tool-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.tool-name {
  width: 160px;
  font-size: 13px;
  color: #606266;
  text-align: right;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-bar-wrap {
  flex: 1;
  height: 22px;
  background: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
}

.tool-bar {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #66b1ff);
  border-radius: 4px;
  transition: width 0.5s ease;
  min-width: 2px;
}

.tool-count {
  width: 56px;
  font-size: 13px;
  color: #909399;
  flex-shrink: 0;
}

/* Element Plus Result 居中 */
.el-result {
  margin-top: 60px;
}
</style>
