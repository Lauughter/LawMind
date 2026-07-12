<template>
  <div class="knowledge-container">
    <!-- 搜索和筛选区域 -->
    <div class="search-filter-bar">
      <div class="search-box">
        <el-input
          v-model="searchQuery"
          placeholder="搜索法律知识..."
          size="large"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <template #append>
            <el-button type="primary" @click="handleSearch">
              搜索
            </el-button>
          </template>
        </el-input>
      </div>
      
      <div class="filter-tabs">
        <el-radio-group v-model="activeType" size="large" @change="handleTypeChange">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="劳动">劳动法</el-radio-button>
          <el-radio-button label="合同">合同法</el-radio-button>
          <el-radio-button label="借贷">借贷法</el-radio-button>
          <el-radio-button label="婚姻">婚姻法</el-radio-button>
          <el-radio-button label="房产">房产法</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="showAddDialog" style="margin-left: 16px;">
          <el-icon><Plus /></el-icon>
          新增法条
        </el-button>
      </div>
    </div>
    
    <!-- 法条列表 -->
    <div class="knowledge-list">
      <el-row :gutter="20">
        <el-col
          v-for="item in knowledgeList"
          :key="item.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="8"
        >
          <el-card class="knowledge-card" shadow="hover" @click="showDetail(item)">
            <div class="card-header">
              <el-tag :type="getTagType(item.type)" size="small">{{ item.type }}</el-tag>
              <span class="card-time">{{ formatDate(item.createTime) }}</span>
            </div>
            <h3 class="card-title" v-html="highlightText(item.title, searchQuery)"></h3>
            <p class="card-content" v-html="highlightText(truncateContent(item.content), searchQuery)"></p>
            <div class="card-footer">
              <el-button type="primary" text @click.stop="showDetail(item)">
                查看详情
                <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <!-- 空状态 -->
      <el-empty
        v-if="knowledgeList.length === 0 && !loading"
        description="暂无相关法律知识"
      />
    </div>
    
    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[5, 10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
    
    <!-- 新增法条弹窗 -->
    <el-dialog
      v-model="addDialogVisible"
      title="新增法条"
      width="650px"
      destroy-on-close
      class="add-dialog"
    >
      <el-form
        ref="addFormRef"
        :model="addForm"
        :rules="addFormRules"
        label-width="90px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="法律类型" prop="lawType">
              <el-select v-model="addForm.lawType" placeholder="请选择" style="width: 100%;">
                <el-option label="劳动法" value="劳动" />
                <el-option label="合同法" value="合同" />
                <el-option label="借贷法" value="借贷" />
                <el-option label="婚姻法" value="婚姻" />
                <el-option label="房产法" value="房产" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源" prop="source">
              <el-select v-model="addForm.source" placeholder="请选择" style="width: 100%;">
                <el-option label="手动录入" value="MANUAL" />
                <el-option label="自动学习" value="AUTO_LEARN" />
                <el-option label="批量导入" value="BATCH_IMPORT" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="法条标题" prop="title">
          <el-input v-model="addForm.title" placeholder="请输入标题" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="法条内容" prop="content">
          <el-input v-model="addForm.content" type="textarea" :rows="8" placeholder="请输入法律条文内容" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="发布机构" prop="publisher">
              <el-input v-model="addForm.publisher" placeholder="如：全国人大常委会" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布日期" prop="publishDate">
              <el-date-picker
                v-model="addForm.publishDate"
                type="date"
                placeholder="选择日期"
                style="width: 100%;"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="submitAdd">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="currentItem.title"
      width="700px"
      destroy-on-close
      class="detail-dialog"
    >
      <div class="detail-content">
        <div class="detail-header">
          <el-tag :type="getTagType(currentItem.type)" size="large">{{ currentItem.type }}</el-tag>
          <span class="detail-time">{{ formatDate(currentItem.createTime) }}</span>
        </div>
        <div class="detail-body">
          <h4>法条内容</h4>
          <div class="content-text">{{ currentItem.content }}</div>
        </div>
        <div class="detail-meta">
          <div class="meta-item" v-if="currentItem.publisher">
            <span class="meta-label">发布机构</span>
            <span class="meta-value">{{ currentItem.publisher }}</span>
          </div>
          <div class="meta-item" v-if="currentItem.publishDate">
            <span class="meta-label">发布日期</span>
            <span class="meta-value">{{ formatDate(currentItem.publishDate) }}</span>
          </div>
          <div class="meta-item" v-if="currentItem.source">
            <span class="meta-label">来源</span>
            <span class="meta-value">{{ sourceLabel(currentItem.source) }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, ArrowRight, Plus } from '@element-plus/icons-vue'
import request from '../utils/axios'

const searchQuery = ref('')
const activeType = ref('')
const knowledgeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const detailVisible = ref(false)
const currentItem = ref({})

// 新增法条
const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const addFormRef = ref(null)
const addForm = reactive({
  lawType: '',
  source: 'MANUAL',
  title: '',
  content: '',
  publisher: '',
  publishDate: ''
})
const addFormRules = {
  lawType: [{ required: true, message: '请选择法律类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入法条内容', trigger: 'blur' }]
}

function getTagType(type) {
  const typeMap = {
    '劳动': 'success',
    '合同': 'primary',
    '借贷': 'warning',
    '婚姻': 'danger',
    '房产': 'info'
  }
  return typeMap[type] || ''
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

function truncateContent(content) {
  if (!content) return ''
  return content.length > 100 ? content.substring(0, 100) + '...' : content
}

function sourceLabel(source) {
  const map = { MANUAL: '手动录入', AUTO_LEARN: '自动学习', BATCH_IMPORT: '批量导入' }
  return map[source] || source
}

// ========== 搜索高亮 ==========

function splitQueryTerms(query) {
  if (!query) return []
  const terms = []
  const legalPattern = /第[^条章节编款项]{1,10}[条章节编款项]/g
  const parts = query.trim().split(/[\s,，]+/)
  for (const part of parts) {
    if (!part) continue
    let lastEnd = 0
    let match
    while ((match = legalPattern.exec(part)) !== null) {
      const before = part.substring(lastEnd, match.index).trim()
      if (before) terms.push(before)
      terms.push(match[0])
      lastEnd = match.index + match[0].length
    }
    const after = part.substring(lastEnd).trim()
    if (after) terms.push(after)
    if (lastEnd === 0) terms.push(part)
  }
  return [...new Set(terms)]
}

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function highlightText(text, query) {
  if (!query || !text) return text
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const terms = splitQueryTerms(query)
  if (terms.length === 0) return escaped
  const pattern = terms.map(t => escapeRegExp(t)).join('|')
  const regex = new RegExp(`(${pattern})`, 'gi')
  return escaped.replace(regex, '<mark>$1</mark>')
}

async function loadKnowledge() {
  loading.value = true
  try {
    const res = await request.get('/law-knowledge/list', {
      page: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchQuery.value,
      type: activeType.value
    })
    
    if (res.code === 200) {
      knowledgeList.value = res.data.list || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (error) {
    console.error('加载法律知识库失败:', error)
    ElMessage.error('加载失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadKnowledge()
}

function handleTypeChange() {
  currentPage.value = 1
  loadKnowledge()
}

function handleSizeChange(size) {
  pageSize.value = size
  loadKnowledge()
}

function handleCurrentChange(page) {
  currentPage.value = page
  loadKnowledge()
}

function showDetail(item) {
  currentItem.value = item
  detailVisible.value = true
}

function showAddDialog() {
  addForm.lawType = ''
  addForm.source = 'MANUAL'
  addForm.title = ''
  addForm.content = ''
  addForm.publisher = ''
  addForm.publishDate = ''
  addDialogVisible.value = true
}

async function submitAdd() {
  if (!addFormRef.value) return
  try {
    await addFormRef.value.validate()
    addSubmitting.value = true
    const res = await request.post('/law-knowledge/add', { ...addForm })
    if (res.code === 200) {
      ElMessage.success('添加成功')
      addDialogVisible.value = false
      loadKnowledge()
    } else {
      ElMessage.error(res.message || '添加失败')
    }
  } catch (error) {
    if (error !== 'cancel' && error?.message) {
      console.error('添加法条失败:', error)
    }
  } finally {
    addSubmitting.value = false
  }
}

onMounted(() => {
  loadKnowledge()
})
</script>

<style scoped>
.knowledge-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px;
}

.search-filter-bar {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.search-box {
  max-width: 600px;
  margin: 0 auto 20px;
}

:deep(.search-box .el-input__wrapper) {
  border-radius: 8px;
}

:deep(.search-box .el-input-group__append) {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  border-color: #1976d2;
}

:deep(.search-box .el-input-group__append .el-button) {
  color: white;
  border: none;
  background: transparent;
}

.filter-tabs {
  display: flex;
  justify-content: center;
  align-items: center;
}

:deep(.filter-tabs .el-radio-button__inner) {
  padding: 12px 24px;
  font-size: 14px;
}

:deep(.filter-tabs .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  border-color: #1976d2;
  box-shadow: -1px 0 0 0 #1976d2;
}

.knowledge-list {
  flex: 1;
  overflow-y: auto;
}

.knowledge-card {
  height: 100%;
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 12px;
}

.knowledge-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-time {
  font-size: 12px;
  color: #999;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-content {
  font-size: 14px;
  color: #666;
  line-height: 1.6;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 24px 0;
  background: white;
  border-radius: 12px;
  margin-top: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

/* 详情弹窗样式 */
:deep(.detail-dialog .el-dialog__header) {
  border-bottom: 1px solid #e0e0e0;
  padding: 20px 24px;
  margin-right: 0;
}

:deep(.detail-dialog .el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

:deep(.detail-dialog .el-dialog__body) {
  padding: 24px;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-time {
  font-size: 14px;
  color: #999;
}

.detail-body h4,
.detail-footer h4 {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.content-text {
  font-size: 14px;
  line-height: 1.8;
  color: #555;
  white-space: pre-wrap;
}

.detail-meta {
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meta-item {
  display: flex;
  gap: 12px;
}

.meta-label {
  font-size: 13px;
  font-weight: 600;
  color: #555;
  flex-shrink: 0;
  min-width: 64px;
}

.meta-value {
  font-size: 13px;
  color: #666;
}

</style>

<!-- 搜索高亮样式（非 scoped：v-html 注入的元素不受 scoped 影响） -->
<style>
.knowledge-container mark {
  background: #fff3cd;
  color: #856404;
  padding: 1px 3px;
  border-radius: 3px;
}
</style>