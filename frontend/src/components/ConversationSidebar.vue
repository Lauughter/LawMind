<template>
  <div class="conversation-sidebar" :class="{ collapsed: collapsed }">
    <!-- 侧边栏头部 -->
    <div class="sidebar-header">
      <el-button
        type="primary"
        class="new-chat-btn"
        @click="handleNewChat"
        :title="collapsed ? '新对话' : ''"
      >
        <el-icon><Plus /></el-icon>
        <span v-show="!collapsed">新对话</span>
      </el-button>
      <el-icon
        class="toggle-btn"
        size="16"
        @click="$emit('toggle-collapse')"
        :title="collapsed ? '展开' : '收起'"
      >
        <DArrowLeft v-if="!collapsed" />
        <DArrowRight v-else />
      </el-icon>
    </div>

    <!-- 模式指示器 -->
    <div v-show="!collapsed" class="mode-indicator" :class="{ agent: agentMode }">
      <span class="mode-dot"></span>
      <span>{{ agentMode ? 'Agent 模式' : '传统模式' }}</span>
    </div>

    <!-- 会话列表 -->
    <div class="conversation-list" v-show="!collapsed">
      <div v-if="conversationStore.loading" class="loading-hint">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <div v-else-if="conversationStore.conversations.length === 0" class="empty-hint">
        <span>暂无对话记录</span>
      </div>

      <div
        v-else
        v-for="conv in conversationStore.conversations"
        :key="conv.id"
        class="conversation-item"
        :class="{ active: conv.id === conversationStore.currentConversationId }"
        @click="handleSelect(conv.id)"
      >
        <div class="conv-info">
          <el-icon size="14" class="conv-icon"><ChatLineSquare /></el-icon>
          <!-- 重命名编辑状态 -->
          <el-input
            v-if="renamingId === conv.id"
            v-model="renameTitle"
            size="small"
            class="rename-input"
            @keyup.enter="confirmRename(conv.id)"
            @blur="confirmRename(conv.id)"
            ref="renameInputRef"
          />
          <span v-else class="conv-title" :title="conv.title">{{ conv.title }}</span>
        </div>
        <div class="conv-actions" v-show="renamingId !== conv.id">
          <el-icon
            size="14"
            class="action-icon"
            title="重命名"
            @click.stop="startRename(conv)"
          ><Edit /></el-icon>
          <el-icon
            size="14"
            class="action-icon delete-icon"
            title="删除"
            @click.stop="handleDelete(conv.id)"
          ><Delete /></el-icon>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  DArrowLeft,
  DArrowRight,
  Loading,
  ChatLineSquare,
  Edit,
  Delete
} from '@element-plus/icons-vue'
import { useConversationStore } from '../stores/conversation'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  },
  agentMode: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggle-collapse', 'new-chat', 'select-conversation'])

const conversationStore = useConversationStore()
const renamingId = ref(null)
const renameTitle = ref('')
const renameInputRef = ref(null)

// 新建对话
function handleNewChat() {
  conversationStore.startNewConversation()
  emit('new-chat')
}

// 选择会话
function handleSelect(id) {
  if (id === conversationStore.currentConversationId) return
  conversationStore.selectConversation(id)
  emit('select-conversation', id)
}

// 开始重命名
function startRename(conv) {
  renamingId.value = conv.id
  renameTitle.value = conv.title
  nextTick(() => {
    if (renameInputRef.value) {
      // renameInputRef 可能是数组（v-for 中的 ref）
      const inputEl = Array.isArray(renameInputRef.value) ? renameInputRef.value[0] : renameInputRef.value
      if (inputEl) inputEl.focus()
    }
  })
}

// 确认重命名
async function confirmRename(id) {
  if (!renamingId.value) return
  const newTitle = renameTitle.value.trim()
  if (!newTitle) {
    renamingId.value = null
    return
  }
  try {
    await conversationStore.renameConversation(id, newTitle)
  } catch {
    ElMessage.error('重命名失败')
  }
  renamingId.value = null
}

// 删除会话
async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定要删除这个对话吗？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await conversationStore.deleteConversation(id)
    if (conversationStore.currentConversationId === null) {
      emit('new-chat')
    }
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  conversationStore.loadConversations()
})
</script>

<style scoped>
.conversation-sidebar {
  width: 240px;
  flex-shrink: 0;
  background-color: #f7f8fa;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  overflow: hidden;
}

.conversation-sidebar.collapsed {
  width: 52px;
}

/* 头部 */
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid #e8e8e8;
}

.new-chat-btn {
  flex: 1;
  border-radius: 8px;
  font-size: 13px;
  height: 34px;
}

.collapsed .new-chat-btn {
  flex: none;
  width: 28px;
  height: 28px;
  padding: 0;
  min-width: 28px;
}

.toggle-btn {
  cursor: pointer;
  color: #999;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.toggle-btn:hover {
  color: #1976d2;
  background-color: rgba(25, 118, 210, 0.08);
}

/* 模式指示器 */
.mode-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin: 4px 8px;
  border-radius: 6px;
  background-color: #e8ecf1;
  font-size: 12px;
  color: #666;
}

.mode-indicator.agent {
  background-color: #e8f5e9;
  color: #2e7d32;
}

.mode-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #999;
  flex-shrink: 0;
}

.mode-indicator.agent .mode-dot {
  background-color: #4caf50;
}

/* 会话列表 */
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.loading-hint,
.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 24px 12px;
  color: #999;
  font-size: 13px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  margin-bottom: 2px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.conversation-item:hover {
  background-color: #eef0f4;
}

.conversation-item.active {
  background-color: #e3f2fd;
}

.conv-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.conv-icon {
  color: #999;
  flex-shrink: 0;
}

.conversation-item.active .conv-icon {
  color: #1976d2;
}

.conv-title {
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item.active .conv-title {
  color: #1976d2;
  font-weight: 500;
}

.rename-input {
  flex: 1;
}

/* 操作按钮 */
.conv-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.conversation-item:hover .conv-actions {
  opacity: 1;
}

.action-icon {
  cursor: pointer;
  color: #999;
  padding: 2px;
  border-radius: 4px;
  transition: all 0.2s;
}

.action-icon:hover {
  color: #1976d2;
  background-color: rgba(25, 118, 210, 0.1);
}

.delete-icon:hover {
  color: #f56c6c;
  background-color: rgba(245, 108, 108, 0.1);
}

/* 滚动条 */
.conversation-list::-webkit-scrollbar {
  width: 4px;
}

.conversation-list::-webkit-scrollbar-track {
  background: transparent;
}

.conversation-list::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 2px;
}

.conversation-list::-webkit-scrollbar-thumb:hover {
  background: #bbb;
}
</style>
