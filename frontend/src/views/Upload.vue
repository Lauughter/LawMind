<template>
  <div class="upload-container">
    <div class="upload-layout">
      <!-- 左侧：文件列表和上传 -->
      <div class="left-panel">
        <div class="upload-section">
          <h3>上传文件</h3>
          <el-upload
            class="upload-area"
            drag
            action="/api/file/upload"
            :headers="getUploadHeaders()"
            :before-upload="beforeUpload"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            accept=".txt,.doc,.docx,.pdf"
          >
            <el-icon class="el-icon--upload" :size="48"><Upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 TXT、Word、PDF 文档，单个文件不超过 10MB
              </div>
            </template>
          </el-upload>
        </div>
        
        <div class="file-list-section">
          <h3>已上传文件</h3>
          <el-empty v-if="fileList.length === 0" description="暂无文件" />
          <div v-else class="file-list">
            <div
              v-for="file in fileList"
              :key="file.id"
              :class="['file-item', { active: selectedFile?.id === file.id }]"
              @click="selectFile(file)"
            >
              <el-icon size="20"><Document /></el-icon>
              <div class="file-info">
                <div class="file-name-row">
                  <span class="file-name">{{ file.fileName }}</span>
                </div>
                <span class="file-time">{{ formatDate(file.uploadTime) }} · {{ formatFileSize(file.fileSize) }}</span>
              </div>
              <el-button
                type="danger"
                link
                size="small"
                @click.stop="deleteFile(file)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 右侧：文件内容 -->
      <div class="right-panel">
        <div v-if="!selectedFile" class="empty-state">
          <el-empty description="请选择或上传一个文件" />
        </div>

        <template v-else>
          <div class="content-section">
            <div class="section-header">
              <h3>文件内容</h3>
            </div>
            <div class="content-box">
              <pre v-if="fileContent">{{ fileContent }}</pre>
              <el-skeleton v-else :rows="10" animated />
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Document, Delete } from '@element-plus/icons-vue'
import request from '../utils/axios'

const fileList = ref([])
const selectedFile = ref(null)
const fileContent = ref('')

function getUploadHeaders() {
  return {
    Authorization: `Bearer ${localStorage.getItem('token')}`
  }
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function beforeUpload(file) {
  const allowedTypes = ['text/plain', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/pdf']
  const isAllowed = allowedTypes.includes(file.type) || file.name.endsWith('.txt') || file.name.endsWith('.doc') || file.name.endsWith('.docx') || file.name.endsWith('.pdf')

  if (!isAllowed) {
    ElMessage.error('只支持 TXT、Word、PDF 文档')
    return false
  }
  
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  
  return true
}

function handleUploadSuccess(response) {
  if (response.code === 200) {
    ElMessage.success('上传成功')
    loadFileList()
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

function handleUploadError() {
  ElMessage.error('上传失败')
}

async function loadFileList() {
  try {
    const res = await request.get('/file/list')
    if (res.code === 200) {
      fileList.value = res.data?.list || []
    }
  } catch (error) {
    console.error('加载文件列表失败:', error)
  }
}

async function selectFile(file) {
  selectedFile.value = file
  fileContent.value = ''
  
  try {
    const res = await request.get(`/file/content/${file.id}`)
    if (res.code === 200) {
      fileContent.value = res.data
    }
  } catch (error) {
    console.error('加载文件内容失败:', error)
    ElMessage.error('加载文件内容失败')
  }
}

async function deleteFile(file) {
  try {
    await ElMessageBox.confirm('确定要删除这个文件吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const res = await request.delete(`/file/${file.id}`)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (selectedFile.value?.id === file.id) {
        selectedFile.value = null
        fileContent.value = ''
      }
      loadFileList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除文件失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadFileList()
})
</script>

<style scoped>
.upload-container {
  height: 100%;
  padding: 24px;
}

.upload-layout {
  display: flex;
  gap: 24px;
  height: 100%;
}

/* 左侧面板 */
.left-panel {
  width: 320px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.upload-section,
.file-list-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.upload-section h3,
.file-list-section h3 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.upload-area {
  width: 100%;
}

:deep(.upload-area .el-upload-dragger) {
  width: 100%;
  padding: 40px 20px;
  border-radius: 8px;
  border-color: #d9d9d9;
}

:deep(.upload-area .el-upload-dragger:hover) {
  border-color: #1976d2;
}

:deep(.upload-area .el-icon--upload) {
  color: #1976d2;
  margin-bottom: 16px;
}

:deep(.upload-area .el-upload__text) {
  color: #666;
}

:deep(.upload-area .el-upload__text em) {
  color: #1976d2;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.file-item:hover {
  background: #f5f7fa;
}

.file-item.active {
  background: linear-gradient(135deg, rgba(25, 118, 210, 0.1) 0%, rgba(21, 101, 192, 0.1) 100%);
  border-left: 3px solid #1976d2;
}

.file-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: hidden;
}

.file-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-name {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-time {
  font-size: 12px;
  color: #999;
}

/* 右侧面板 */
.right-panel {
  flex: 1;
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;
}

.section-header h3,
.section-header h4 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.content-box {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 20px;
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.content-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
}

</style>