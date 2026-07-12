<template>
  <div class="profile-container">
    <div class="profile-layout">
      <!-- 左侧：用户信息卡片 -->
      <div class="left-panel">
        <el-card class="user-card" shadow="never">
          <div class="user-avatar">
            <el-avatar :size="80" :icon="UserFilled" />
          </div>
          <h2 class="user-name">{{ userInfo.username }}</h2>
          <p class="user-role">普通用户</p>
          <el-divider />
          <div class="user-stats">
            <div class="stat-item">
              <span class="stat-value">{{ stats.chatCount }}</span>
              <span class="stat-label">咨询次数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.fileCount }}</span>
              <span class="stat-label">上传文件</span>
            </div>
          </div>
        </el-card>
        
        <el-card class="menu-card" shadow="never">
          <div
            v-for="item in menuItems"
            :key="item.key"
            :class="['menu-item', { active: activeTab === item.key }]"
            @click="activeTab = item.key"
          >
            <el-icon size="18">
              <component :is="item.icon" />
            </el-icon>
            <span>{{ item.label }}</span>
          </div>
        </el-card>
      </div>
      
      <!-- 右侧：内容区域 -->
      <div class="right-panel">
        <!-- 个人信息 -->
        <div v-if="activeTab === 'info'" class="content-section">
          <h3 class="section-title">个人信息</h3>
          <el-card class="info-card" shadow="never">
            <el-form
              ref="infoFormRef"
              :model="userInfo"
              :rules="infoRules"
              label-width="100px"
              class="info-form"
            >
              <el-form-item label="用户名">
                <el-input v-model="userInfo.username" disabled />
              </el-form-item>
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="userInfo.nickname" placeholder="请输入昵称" />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="userInfo.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveUserInfo">
                  保存修改
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>
          
          <h3 class="section-title" style="margin-top: 24px;">修改密码</h3>
          <el-card class="info-card" shadow="never">
            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              label-width="100px"
              class="info-form"
            >
              <el-form-item label="原密码" prop="oldPassword">
                <el-input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  placeholder="请输入原密码"
                  show-password
                />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  placeholder="请输入新密码"
                  show-password
                />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  placeholder="请确认新密码"
                  show-password
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="changingPassword" @click="changePassword">
                  修改密码
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
        
        <!-- 对话历史 -->
        <div v-if="activeTab === 'chat'" class="content-section">
          <h3 class="section-title">对话历史</h3>
          <el-card class="list-card" shadow="never">
            <el-empty v-if="chatList.length === 0" description="暂无对话记录" />
            <div v-else class="chat-list">
              <div
                v-for="chat in chatList"
                :key="chat.id"
                class="chat-item"
                @click="viewChatDetail(chat)"
              >
                <div class="chat-icon">
                  <el-icon size="24"><ChatDotRound /></el-icon>
                </div>
                <div class="chat-info">
                  <div class="chat-header-info">
                    <span class="chat-title">{{ truncateText(chat.userQuestion, 30) }}</span>
                    <span class="chat-time">{{ formatDateTime(chat.createTime) }}</span>
                  </div>
                  <p class="chat-preview">{{ truncateText(chat.aiAnswer, 50) }}</p>
                </div>
              </div>
            </div>
            <div v-if="chatTotal > 0" class="pagination-wrapper">
              <el-pagination
                v-model:current-page="chatPage"
                v-model:page-size="chatPageSize"
                :total="chatTotal"
                layout="prev, pager, next"
                @current-change="loadChatHistory"
              />
            </div>
          </el-card>
        </div>
        
        <!-- 上传历史 -->
        <div v-if="activeTab === 'upload'" class="content-section">
          <h3 class="section-title">上传历史</h3>
          <el-card class="list-card" shadow="never">
            <el-empty v-if="fileList.length === 0" description="暂无上传记录" />
            <el-table v-else :data="fileList" style="width: 100%">
              <el-table-column prop="fileName" label="文件名" min-width="180" />
              <el-table-column prop="fileType" label="类型" width="80">
                <template #default="{ row }">
                  <el-tag size="small">{{ row.fileType || '未知' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="fileSize" label="大小" width="90">
                <template #default="{ row }">
                  {{ formatFileSize(row.fileSize) }}
                </template>
              </el-table-column>
              <el-table-column prop="processingStatus" label="状态" width="90">
                <template #default="{ row }">
                  <el-tag v-if="row.processingStatus === 'PROCESSING'" type="warning" size="small">处理中</el-tag>
                  <el-tag v-else-if="row.processingStatus === 'COMPLETED'" type="success" size="small">已完成</el-tag>
                  <el-tag v-else-if="row.processingStatus === 'FAILED'" type="danger" size="small">失败</el-tag>
                  <el-tag v-else size="small">待处理</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="上传时间" width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.uploadTime) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="viewFile(row)">
                    查看
                  </el-button>
                  <el-button type="danger" link size="small" @click="deleteFile(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="fileTotal > 0" class="pagination-wrapper">
              <el-pagination
                v-model:current-page="filePage"
                v-model:page-size="filePageSize"
                :total="fileTotal"
                layout="prev, pager, next"
                @current-change="loadFileHistory"
              />
            </div>
          </el-card>
        </div>
        
      </div>
    </div>

    <!-- 对话详情弹窗 -->
    <el-dialog
      v-model="chatDetailVisible"
      title="对话详情"
      width="600px"
      destroy-on-close
    >
      <div class="chat-detail">
        <div class="detail-item">
          <label>问题：</label>
          <p>{{ currentChat?.userQuestion }}</p>
        </div>
        <div class="detail-item">
          <label>回答：</label>
          <p>{{ currentChat?.aiAnswer }}</p>
        </div>
        <div class="detail-item">
          <label>时间：</label>
          <span>{{ formatDateTime(currentChat?.createTime) }}</span>
        </div>
      </div>
    </el-dialog>
    
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  UserFilled,
  User,
  ChatDotRound,
  Upload,
  Lock
} from '@element-plus/icons-vue'
import request from '../utils/axios'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()

const activeTab = ref('info')
const saving = ref(false)
const changingPassword = ref(false)
const chatDetailVisible = ref(false)
const currentChat = ref(null)

const menuItems = [
  { key: 'info', label: '个人信息', icon: 'User' },
  { key: 'chat', label: '对话历史', icon: 'ChatDotRound' },
  { key: 'upload', label: '上传历史', icon: 'Upload' },
]

const userInfo = reactive({
  username: '',
  nickname: '',
  phone: ''
})

const stats = reactive({
  chatCount: 0,
  fileCount: 0
})

const infoFormRef = ref(null)
const infoRules = {
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordFormRef = ref(null)
const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 对话历史
const chatList = ref([])
const chatPage = ref(1)
const chatPageSize = ref(10)
const chatTotal = ref(0)

// 上传历史
const fileList = ref([])
const filePage = ref(1)
const filePageSize = ref(10)
const fileTotal = ref(0)

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function truncateText(text, length) {
  if (!text) return ''
  return text.length > length ? text.substring(0, length) + '...' : text
}

async function loadUserInfo() {
  try {
    const res = await request.get('/user/info')
    if (res.code === 200) {
      Object.assign(userInfo, res.data)
    }
  } catch (error) {
    console.error('加载用户信息失败:', error)
  }
}

async function loadStats() {
  try {
    const res = await request.get('/user/stats')
    if (res.code === 200) {
      Object.assign(stats, res.data)
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

async function saveUserInfo() {
  if (!infoFormRef.value) return
  
  try {
    await infoFormRef.value.validate()
    saving.value = true
    
    // 只更新昵称和手机号，用户名不可修改
    const res = await request.post('/user/update-info', {
      nickname: userInfo.nickname,
      phone: userInfo.phone
    })
    
    if (res.code === 200) {
      ElMessage.success('保存成功')
      loadUserInfo() // 重新加载用户信息
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error) {
    console.error('保存用户信息失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (!passwordFormRef.value) return
  
  try {
    await passwordFormRef.value.validate()
    changingPassword.value = true
    
    const res = await request.post('/user/change-password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    
    if (res.code === 200) {
      ElMessage.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } else {
      ElMessage.error(res.message || '修改失败')
    }
  } catch (error) {
    console.error('修改密码失败:', error)
    ElMessage.error('修改失败')
  } finally {
    changingPassword.value = false
  }
}

async function loadChatHistory() {
  try {
    const res = await request.get('/user/chat-history')
    if (res.code === 200) {
      // 后端返回的是完整列表，前端简单分页
      const allChats = res.data || []
      chatTotal.value = allChats.length
      const start = (chatPage.value - 1) * chatPageSize.value
      const end = start + chatPageSize.value
      chatList.value = allChats.slice(start, end)
    }
  } catch (error) {
    console.error('加载对话历史失败:', error)
  }
}

function viewChatDetail(chat) {
  currentChat.value = chat
  chatDetailVisible.value = true
}

async function loadFileHistory() {
  try {
    const res = await request.get('/user/upload-history')
    if (res.code === 200) {
      // 后端返回的是完整列表，前端简单分页
      const allFiles = res.data || []
      fileTotal.value = allFiles.length
      const start = (filePage.value - 1) * filePageSize.value
      const end = start + filePageSize.value
      fileList.value = allFiles.slice(start, end)
    }
  } catch (error) {
    console.error('加载上传历史失败:', error)
  }
}

function viewFile(file) {
  // 跳转到上传页面查看文件
  window.open(`/home/upload?fileId=${file.id}`, '_blank')
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
      loadFileHistory()
      loadStats()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除文件失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadUserInfo()
  loadStats()
  loadChatHistory()
  loadFileHistory()
})
</script>

<style scoped>
.profile-container {
  height: 100%;
  padding: 24px;
}

.profile-layout {
  display: flex;
  gap: 24px;
  height: 100%;
}

/* 左侧面板 */
.left-panel {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.user-card {
  text-align: center;
  border-radius: 12px;
}

.user-avatar {
  margin-bottom: 16px;
}

.user-name {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0 0 4px 0;
}

.user-role {
  font-size: 13px;
  color: #999;
  margin: 0 0 16px 0;
}

.user-stats {
  display: flex;
  justify-content: space-around;
  padding: 16px 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1976d2;
}

.stat-label {
  font-size: 12px;
  color: #666;
}

.menu-card {
  border-radius: 12px;
  flex: 1;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.3s ease;
  color: #666;
}

.menu-item:hover {
  background: #f5f7fa;
  color: #1976d2;
}

.menu-item.active {
  background: linear-gradient(135deg, rgba(25, 118, 210, 0.1) 0%, rgba(21, 101, 192, 0.1) 100%);
  color: #1976d2;
  font-weight: 500;
}

/* 右侧面板 */
.right-panel {
  flex: 1;
  overflow-y: auto;
}

.content-section {
  height: 100%;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.info-card,
.list-card {
  border-radius: 12px;
}

.info-form {
  max-width: 500px;
}

/* 对话列表 */
.chat-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.chat-item:hover {
  background: #f5f7fa;
}

.chat-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-info {
  flex: 1;
  min-width: 0;
}

.chat-header-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.chat-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.chat-time {
  font-size: 12px;
  color: #999;
}

.chat-preview {
  font-size: 13px;
  color: #666;
  margin: 0;
  line-height: 1.5;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
  margin-top: 20px;
}

/* 详情弹窗 */
.chat-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-item label {
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 8px;
}

.detail-item p {
  margin: 0;
  line-height: 1.6;
  color: #555;
  background: #f8f9fa;
  padding: 12px;
  border-radius: 8px;
}
</style>