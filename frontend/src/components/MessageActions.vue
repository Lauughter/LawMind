<template>
  <div class="message-actions" :class="{ visible: hover || feedback !== null }">
    <div class="action-btn" @click="handleCopy" :title="copySuccess ? '已复制' : '复制内容'">
      <el-icon size="14"><DocumentCopy /></el-icon>
      <transition name="label-fade">
        <span v-if="copySuccess" class="action-label success">已复制</span>
      </transition>
    </div>

    <div class="action-btn" @click="$emit('regenerate')" title="重新生成">
      <el-icon size="14"><RefreshRight /></el-icon>
    </div>

    <div class="action-btn" :class="{ active: feedback === 1 }" @click="handleFeedback(1)" title="有帮助">
      <el-icon size="14">
        <component :is="feedback === 1 ? StarFilled : Star" />
      </el-icon>
    </div>

    <!-- 踩按钮：点击后弹出反馈面板 -->
    <el-popover
      v-model:visible="showDislikePanel"
      placement="top"
      :width="280"
      trigger="click"
      :show-arrow="false"
    >
      <template #reference>
        <div class="action-btn" :class="{ active: feedback === -1 }" title="无帮助">
          <el-icon size="14"><WarningFilled /></el-icon>
        </div>
      </template>
      <div class="feedback-panel">
        <div class="feedback-title">请告诉我们哪里做得不好</div>
        <el-checkbox-group v-model="dislikeReasons" class="feedback-reasons">
          <el-checkbox label="inaccurate">回答不准确</el-checkbox>
          <el-checkbox label="wrong_citation">引用的法条有误</el-checkbox>
          <el-checkbox label="irrelevant">没有回答我的问题</el-checkbox>
          <el-checkbox label="too_vague">回答太笼统</el-checkbox>
          <el-checkbox label="other">其他</el-checkbox>
        </el-checkbox-group>
        <el-input
          v-model="dislikeNote"
          type="textarea"
          :rows="2"
          placeholder="补充说明（选填）"
          class="feedback-note"
        />
        <div class="feedback-actions">
          <el-button size="small" @click="cancelDislike">取消</el-button>
          <el-button size="small" type="primary" @click="submitDislike">提交</el-button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { DocumentCopy, RefreshRight, Star, StarFilled, WarningFilled } from '@element-plus/icons-vue'

const props = defineProps({
  content: { type: String, default: '' },
  chatId: { type: [Number, null], default: null },
  hover: { type: Boolean, default: false },
  initialFeedback: { type: [Number, null], default: null },
  mode: { type: String, default: 'traditional' }
})

const emit = defineEmits(['regenerate', 'feedback'])

const feedback = ref(props.initialFeedback)
const copySuccess = ref(false)
const showDislikePanel = ref(false)
const dislikeReasons = ref([])
const dislikeNote = ref('')

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.content)
    copySuccess.value = true
    setTimeout(() => { copySuccess.value = false }, 1500)
  } catch (err) {
    const textarea = document.createElement('textarea')
    textarea.value = props.content
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    copySuccess.value = true
    setTimeout(() => { copySuccess.value = false }, 1500)
  }
}

function handleFeedback(value) {
  if (value === 1) {
    // 点赞：直接发送，无需弹窗
    const newValue = feedback.value === 1 ? null : 1
    feedback.value = newValue
    emit('feedback', { chatId: props.chatId, feedback: newValue, feedbackContent: null, mode: props.mode })
  }
  // 踩按钮由 popover 的 trigger="click" 处理，这里不做额外逻辑
}

function submitDislike() {
  const parts = [...dislikeReasons.value]
  if (dislikeNote.value.trim()) {
    parts.push(dislikeNote.value.trim())
  }
  const content = parts.length > 0 ? parts.join('|') : null

  feedback.value = -1
  showDislikePanel.value = false
  emit('feedback', { chatId: props.chatId, feedback: -1, feedbackContent: content, mode: props.mode })
}

function cancelDislike() {
  showDislikePanel.value = false
  dislikeReasons.value = []
  dislikeNote.value = ''
}
</script>

<style scoped>
.message-actions {
  display: flex;
  gap: 2px;
  margin-top: 6px;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity 0.25s ease, transform 0.25s ease;
  height: 30px;
  align-items: center;
}

/* hover 时或已有反馈时显示 */
.message-actions.visible {
  opacity: 1;
  transform: translateY(0);
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: 8px;
  cursor: pointer;
  color: #999;
  font-size: 12px;
  transition: all 0.2s ease;
  user-select: none;
  position: relative;
}

.action-btn:hover {
  background-color: rgba(25, 118, 210, 0.08);
  color: #1976d2;
  transform: translateY(-1px);
}

.action-btn:active {
  transform: scale(0.92);
}

/* 赞激活 */
.action-btn.active {
  color: #1976d2;
  background-color: rgba(25, 118, 210, 0.12);
}

/* "已复制" 文字过渡 */
.label-fade-enter-active,
.label-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.label-fade-enter-from,
.label-fade-leave-to {
  opacity: 0;
  transform: translateX(-4px);
}

.action-label.success {
  color: #4caf50;
  font-weight: 500;
  font-size: 11px;
}

.feedback-panel {
  padding: 4px 0;
}

.feedback-title {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  margin-bottom: 10px;
}

.feedback-reasons {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}

.feedback-reasons :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
}

.feedback-reasons :deep(.el-checkbox__label) {
  font-size: 13px;
  color: #555;
}

.feedback-note {
  margin-bottom: 10px;
}

.feedback-note :deep(.el-textarea__inner) {
  font-size: 12px;
}

.feedback-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
