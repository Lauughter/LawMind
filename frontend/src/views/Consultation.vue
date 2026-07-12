<template>
  <div class="consultation-wrapper" :class="{ 'is-fullscreen': isFullscreen }">
    <!-- 会话侧边栏 -->
    <ConversationSidebar
      v-show="!isFullscreen"
      :collapsed="sidebarCollapsed"
      :agent-mode="useAgentMode"
      @toggle-collapse="sidebarCollapsed = !sidebarCollapsed"
      @new-chat="handleNewChat"
      @select-conversation="handleSelectConversation"
    />

    <!-- 聊天主区域 -->
    <div class="consultation-container">
      <!-- 聊天头部 -->
      <div class="chat-header">
        <div class="chat-title">
          <el-icon size="20"><ChatDotRound /></el-icon>
          <span>{{ currentTitle }}</span>
        </div>
        <div class="chat-header-right">
          <div class="chat-status">
            <span class="status-dot"></span>
            <span>在线</span>
          </div>
          <el-tooltip
            :content="useAgentMode ? 'Agent 模式：智能多步推理' : '传统模式：快速检索回答'"
            placement="bottom"
          >
            <div class="mode-switch">
              <span class="mode-label">{{ useAgentMode ? 'Agent' : '传统' }}</span>
              <el-switch
                v-model="useAgentMode"
                size="small"
                @change="handleModeSwitch"
              />
            </div>
          </el-tooltip>
          <div
            class="fullscreen-btn"
            @click="$emit('toggle-fullscreen')"
            :title="isFullscreen ? '退出全屏' : '全屏模式'"
          >
            <el-icon size="18">
              <FullScreen v-if="!isFullscreen" />
              <Close v-else />
            </el-icon>
          </div>
        </div>
      </div>

      <!-- 聊天消息区域 -->
      <div class="chat-messages" ref="messagesContainer" @scroll="handleScroll">
        <!-- 欢迎界面 -->
        <div v-if="isWelcomeMode" class="welcome-screen">
          <div class="welcome-icon">
            <el-icon size="56"><Service /></el-icon>
          </div>
          <h2 class="welcome-title">LawMind 智能法律助手</h2>
          <p class="welcome-subtitle">我能帮您解答劳动纠纷、合同争议、婚姻家庭、消费维权等法律问题</p>
          <div class="suggested-questions">
            <div
              v-for="q in suggestedQuestions"
              :key="q.id"
              class="suggested-card"
              @click="askSuggested(q.question)"
            >
              <el-icon size="20" :color="q.color"><component :is="q.icon" /></el-icon>
              <span>{{ q.question }}</span>
            </div>
          </div>
        </div>

        <div
          v-for="(message, index) in messages"
          :key="index"
          :class="['message-item', message.type]"
        >
          <!-- 系统消息 -->
          <template v-if="message.type === 'system'">
            <div class="system-message">
              <div class="system-content">{{ message.content }}</div>
              <div class="system-time">{{ message.time }}</div>
            </div>
          </template>

          <!-- 用户消息 -->
          <template v-else-if="message.type === 'user'">
            <div class="user-message">
              <div class="message-content-wrapper">
                <div class="message-time">{{ message.time }}</div>
                <div class="message-bubble user-bubble">
                  {{ message.content }}
                </div>
              </div>
              <div class="avatar user-avatar">
                <el-icon size="24"><User /></el-icon>
              </div>
            </div>
          </template>

          <!-- AI消息 -->
          <template v-else-if="message.type === 'ai'">
            <div
              class="ai-message"
              @mouseenter="message._hover = true"
              @mouseleave="message._hover = false"
            >
              <div class="avatar ai-avatar">
                <el-icon size="24"><Service /></el-icon>
              </div>
              <div class="message-content-wrapper">
                <div class="message-bubble ai-bubble">
                  <div v-if="message.loading" class="loading-dots">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                  <div v-else>
                    <MarkdownContent :content="message.content" :streaming="message.streaming" />
                    <div v-if="message.relatedKnowledge && message.relatedKnowledge.length > 0" class="knowledge-section">
                      <h4 class="knowledge-section-title">📚 相关法条</h4>
                      <div class="knowledge-card-grid">
                        <div v-for="(knowledge, kIndex) in message.relatedKnowledge" :key="kIndex" class="knowledge-card">
                          <div class="knowledge-card-header">
                            <el-icon class="knowledge-card-icon"><Document /></el-icon>
                            <span class="knowledge-card-title">{{ knowledge.title }}</span>
                            <el-tag v-if="knowledge.law_type" size="small" type="info">{{ knowledge.law_type }}</el-tag>
                          </div>
                          <div class="knowledge-card-body">{{ knowledge.content }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <MessageActions
                  v-if="!message.loading && !message.streaming"
                  :content="message.content"
                  :chat-id="message.chatId"
                  :hover="!!message._hover"
                  :initial-feedback="message.feedback"
                  :mode="message.mode || 'traditional'"
                  @regenerate="handleRegenerate(index)"
                  @feedback="handleFeedback"
                />
                <div class="message-time">{{ message.time }}</div>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 回到底部浮动按钮 -->
      <transition name="fade-up">
        <div v-show="showScrollBottom" class="scroll-bottom-btn" @click="scrollToBottom">
          <el-icon size="18"><ArrowDown /></el-icon>
        </div>
      </transition>

      <!-- 输入区域 - 动态收缩 -->
      <div class="chat-input-area" :class="{ expanded: inputExpanded }">
        <!-- 收起状态：单行输入条 -->
        <div class="input-collapsed" v-show="!inputExpanded" @click="expandInput">
          <div class="collapsed-placeholder">
            <el-icon size="16" color="#999"><EditPen /></el-icon>
            <span>输入您的法律问题...</span>
          </div>
          <el-icon size="16" class="collapsed-arrow"><ArrowUp /></el-icon>
        </div>

        <!-- 展开状态：完整输入框 -->
        <div class="input-expanded-content" v-show="inputExpanded">
          <div class="input-toolbar">
            <div class="toolbar-left">
              <el-button type="text" :icon="Document" title="发送文件"></el-button>
              <el-button type="text" :icon="Picture" title="发送图片"></el-button>
              <el-button type="text" :icon="Microphone" title="语音输入"></el-button>
            </div>
            <div class="toolbar-right">
              <el-icon size="16" class="collapse-btn" @click="collapseInput" title="收起输入框"><ArrowDown /></el-icon>
            </div>
          </div>
          <div class="input-box">
            <el-input
              ref="textareaRef"
              v-model="inputMessage"
              type="textarea"
              :rows="3"
              placeholder="请输入您的法律问题，按 Enter 发送，Shift + Enter 换行"
              resize="none"
              @keydown.enter.prevent="handleKeyDown"
              @blur="handleInputBlur"
            />
            <el-button
              v-if="streaming"
              type="danger"
              class="send-btn abort-btn"
              @click="abortStream"
              title="停止生成"
            >
              <el-icon size="18"><VideoPause /></el-icon>
            </el-button>
            <el-button
              v-else
              type="primary"
              class="send-btn"
              :loading="sending"
              :disabled="!inputMessage.trim()"
              @click="sendMessage"
            >
              <el-icon size="18"><Promotion /></el-icon>
            </el-button>
          </div>
          <div class="input-hint">
            <span>AI 助手会根据您的描述提供专业的法律建议</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  ChatDotRound,
  User,
  Service,
  Document,
  Picture,
  Microphone,
  Promotion,
  FullScreen,
  Close,
  EditPen,
  ArrowUp,
  ArrowDown,
  VideoPause,
  Warning,
  FirstAidKit,
  Money,
  House,
  ShoppingCart,
} from "@element-plus/icons-vue";
import request from "../utils/axios";
import { streamChat, streamAgentChat } from "../utils/sse";
import { useUserStore } from "../stores/user";
import { useConversationStore } from "../stores/conversation";
import ConversationSidebar from "../components/ConversationSidebar.vue";
import MarkdownContent from "../components/MarkdownContent.vue";
import MessageActions from "../components/MessageActions.vue";

const userStore = useUserStore();
const conversationStore = useConversationStore();

// 流式请求控制器，用于中断请求
let currentStreamController = null;

const props = defineProps({
  isFullscreen: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['toggle-fullscreen']);

const messagesContainer = ref(null);
const textareaRef = ref(null);
const inputMessage = ref("");
const sending = ref(false);
const streaming = ref(false);
const inputExpanded = ref(false);
const sidebarCollapsed = ref(false);
const showScrollBottom = ref(false);
const useAgentMode = ref(false);

// 欢迎页面是否显示（只有初始系统消息时显示）
const isWelcomeMode = computed(() => {
  return messages.value.length === 1 && messages.value[0].type === 'system'
})

// 建议问题列表
const suggestedQuestions = [
  { id: 1, question: '被公司辞退可以要求多少赔偿？', icon: Warning, color: '#e6a23c' },
  { id: 2, question: '劳动合同到期不续签有什么补偿？', icon: Document, color: '#1976d2' },
  { id: 3, question: '工伤认定的流程和标准是什么？', icon: FirstAidKit, color: '#e53935' },
  { id: 4, question: '拖欠工资该如何维权？', icon: Money, color: '#4caf50' },
  { id: 5, question: '离婚时房产如何分割？', icon: House, color: '#7c4dff' },
  { id: 6, question: '遭遇消费欺诈如何要求赔偿？', icon: ShoppingCart, color: '#ff6f00' },
]

function askSuggested(question) {
  inputMessage.value = question
  inputExpanded.value = true
  nextTick(() => {
    sendMessage()
  })
}

function handleModeSwitch(newVal) {
  ElMessage.info(newVal ? '已切换到 Agent 模式（智能多步推理）' : '已切换到传统模式（快速检索回答）')
}

// 当前会话标题
const currentTitle = computed(() => {
  if (conversationStore.currentConversation) {
    return conversationStore.currentConversation.title;
  }
  return "智能法律咨询助手";
});

function expandInput() {
  inputExpanded.value = true;
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.focus();
    }
  });
}

function collapseInput() {
  if (!inputMessage.value.trim()) {
    inputExpanded.value = false;
  }
}

function handleInputBlur() {
  setTimeout(() => {
    if (!inputMessage.value.trim() && !sending.value) {
      inputExpanded.value = false;
    }
  }, 200);
}

const messages = ref([
  {
    type: "system",
    content: "您好！我是 LawMind 智能法律咨询助手，请问有什么可以帮助您的？",
    time: formatTime(new Date()),
  },
]);

function formatTime(date) {
  const hours = date.getHours().toString().padStart(2, "0");
  const minutes = date.getMinutes().toString().padStart(2, "0");
  return `${hours}:${minutes}`;
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
}

/**
 * 智能滚动：仅当用户位于底部附近（150px内）时才自动滚动
 * 用于流式输出过程中，避免干扰用户查看历史消息
 */
function scrollToBottomIfNear() {
  nextTick(() => {
    const el = messagesContainer.value;
    if (!el) return;
    const { scrollTop, scrollHeight, clientHeight } = el;
    const isNearBottom = scrollHeight - scrollTop - clientHeight < 150;
    if (isNearBottom) {
      el.scrollTop = scrollHeight;
    }
  });
}

/**
 * 监听消息区域滚动，判断是否显示"回到底部"按钮
 * 距离底部超过 200px 时显示
 */
function handleScroll() {
  if (!messagesContainer.value) return;
  const { scrollTop, scrollHeight, clientHeight } = messagesContainer.value;
  showScrollBottom.value = scrollHeight - scrollTop - clientHeight > 200;
}

function handleKeyDown(e) {
  if (e.shiftKey) {
    return;
  }
  e.preventDefault();
  sendMessage();
}

/**
 * 新建对话：清空消息，重置系统欢迎语
 */
function handleNewChat() {
  messages.value = [
    {
      type: "system",
      content: "您好！我是 LawMind 智能法律咨询助手，请问有什么可以帮助您的？",
      time: formatTime(new Date()),
    },
  ];
  scrollToBottom();
}

/**
 * 选择历史会话：加载消息记录
 */
async function handleSelectConversation(conversationId) {
  // 清空当前消息
  messages.value = [
    {
      type: "system",
      content: "正在加载对话记录...",
      time: formatTime(new Date()),
    },
  ];

  try {
    const chatList = await conversationStore.loadMessages(conversationId);

    // 重建消息列表
    const loadedMessages = [];
    loadedMessages.push({
      type: "system",
      content: "以下是历史对话记录",
      time: formatTime(new Date()),
    });

    for (const chat of chatList) {
      const chatTime = chat.createTime ? new Date(chat.createTime) : new Date();
      // 用户消息
      loadedMessages.push({
        type: "user",
        content: chat.userQuestion,
        time: formatTime(chatTime),
      });
      // AI回复
      loadedMessages.push({
        type: "ai",
        content: chat.aiAnswer,
        time: formatTime(chatTime),
        loading: false,
        chatId: chat.id,
        feedback: chat.feedback ?? null,
        _hover: false,
      });
    }

    messages.value = loadedMessages;
    scrollToBottom();
  } catch (error) {
    console.error("加载对话记录失败:", error);
    messages.value = [
      {
        type: "system",
        content: "加载对话记录失败，请重试",
        time: formatTime(new Date()),
      },
    ];
  }
}

async function sendMessage() {
  const content = inputMessage.value.trim();
  if (!content || sending.value) return;

  // 添加用户消息
  messages.value.push({
    type: "user",
    content: content,
    time: formatTime(new Date()),
  });

  inputMessage.value = "";
  inputExpanded.value = false;
  scrollToBottom();

  // 添加 AI 流式消息占位（初始为 loading 状态）
  const aiMessage = {
    type: "ai",
    content: "",
    time: formatTime(new Date()),
    loading: true,
    streaming: false,
    _hover: false,
    feedback: null,
    mode: useAgentMode.value ? "agent" : "traditional",
  };
  messages.value.push(aiMessage);
  sending.value = true;
  scrollToBottom();

  if (useAgentMode.value) {
    // ========== Agent 模式 ==========
    const agentData = {
      question: content,
    };
    if (conversationStore.currentConversationId) {
      agentData.conversationId = conversationStore.currentConversationId;
    }

    const { abort } = streamAgentChat({
      data: agentData,
      onToken: (token) => {
        if (aiMessage.loading) {
          aiMessage.loading = false;
          aiMessage.streaming = true;
          streaming.value = true;
        }
        aiMessage.content += token;
        scrollToBottomIfNear();
      },
      onDone: ({ conversationId, chatId }) => {
        aiMessage.streaming = false;
        aiMessage.chatId = chatId;
        streaming.value = false;
        sending.value = false;
        currentStreamController = null;

        if (conversationId) {
          conversationStore.setCurrentConversationId(conversationId);
        }
        scrollToBottom();
      },
      onError: (errorMessage) => {
        console.error("Agent 流式回答出错:", errorMessage);
        aiMessage.loading = false;
        aiMessage.streaming = false;
        streaming.value = false;
        sending.value = false;
        currentStreamController = null;

        if (!aiMessage.content) {
          aiMessage.content = errorMessage || "抱歉，系统出现错误，请稍后再试。";
        }
        ElMessage.error(errorMessage || "Agent 回答生成失败");
        scrollToBottom();
      },
    });

    currentStreamController = { abort };
  } else {
    // ========== 传统模式 ==========
    const userId = userStore.getUserId;
    const requestData = {
      userId,
      question: content,
    };

    if (conversationStore.currentConversationId) {
      requestData.conversationId = conversationStore.currentConversationId;
    }

    const { abort } = streamChat({
      data: requestData,
      onToken: (token) => {
        if (aiMessage.loading) {
          aiMessage.loading = false;
          aiMessage.streaming = true;
          streaming.value = true;
        }
        aiMessage.content += token;
        scrollToBottomIfNear();
      },
      onKnowledge: (knowledgeList) => {
        aiMessage.relatedKnowledge = knowledgeList;
      },
      onDone: ({ conversationId, chatId }) => {
        aiMessage.streaming = false;
        aiMessage.chatId = chatId;
        streaming.value = false;
        sending.value = false;
        currentStreamController = null;

        if (conversationId) {
          conversationStore.setCurrentConversationId(conversationId);
        }
        scrollToBottom();
      },
      onError: (errorMessage) => {
        console.error("流式回答出错:", errorMessage);
        aiMessage.loading = false;
        aiMessage.streaming = false;
        streaming.value = false;
        sending.value = false;
        currentStreamController = null;

        if (!aiMessage.content) {
          aiMessage.content = errorMessage || "抱歉，系统出现错误，请稍后再试。";
        }
        ElMessage.error(errorMessage || "回答生成失败");
        scrollToBottom();
      },
    });

    currentStreamController = { abort };
  }
}

/**
 * 中断当前流式生成
 */
function abortStream() {
  if (currentStreamController) {
    currentStreamController.abort();
    currentStreamController = null;
  }
  streaming.value = false;
  sending.value = false;

  // 将最后一条 AI 消息标记为完成
  const lastMsg = messages.value[messages.value.length - 1];
  if (lastMsg && lastMsg.type === "ai") {
    lastMsg.streaming = false;
    lastMsg.loading = false;
    if (!lastMsg.content) {
      lastMsg.content = "（已中断生成）";
    }
  }
}

/**
 * 重新生成：找到该 AI 消息对应的用户问题，重新发送
 * @param {number} aiMsgIndex AI 消息在 messages 数组中的索引
 */
function handleRegenerate(aiMsgIndex) {
  if (sending.value) return;

  // 向前查找最近的用户消息
  let userQuestion = '';
  for (let i = aiMsgIndex - 1; i >= 0; i--) {
    if (messages.value[i].type === 'user') {
      userQuestion = messages.value[i].content;
      break;
    }
  }

  if (!userQuestion) return;

  // 移除旧的 AI 回复
  messages.value.splice(aiMsgIndex, 1);

  // 重新发送（直接设置 inputMessage 并调用 sendMessage）
  inputMessage.value = userQuestion;
  sendMessage();
}

/**
 * 处理用户反馈（赞/踩）
 * @param {{ chatId: number, feedback: number|null, mode: string }} data
 */
async function handleFeedback({ chatId, feedback, feedbackContent, mode }) {
  if (!chatId) return;

  try {
    await request.post('/ai-chat/feedback', { chatId, feedback, feedbackContent, mode });
  } catch (error) {
    console.error('反馈提交失败:', error);
    ElMessage.error('反馈提交失败');
  }
}

onMounted(() => {
  // 如果当前有选中的会话（如页面切换后返回），自动加载该会话的消息记录
  if (conversationStore.currentConversationId) {
    handleSelectConversation(conversationStore.currentConversationId);
  }
  scrollToBottom();
});
</script>

<style scoped>
/* 外层包裹容器：侧边栏 + 聊天区域 */
.consultation-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  overflow: hidden;
}

.consultation-wrapper.is-fullscreen {
  height: 100vh;
  width: 100vw;
}

.consultation-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #f0f2f5;
  overflow: hidden;
  min-width: 0;
  position: relative;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 28px;
  background: linear-gradient(90deg, #1976d2 0%, #1565c0 100%);
  color: white;
  box-shadow: 0 2px 12px rgba(25, 118, 210, 0.3);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 600;
  color: white;
}

.chat-title .el-icon {
  font-size: 22px;
}

.chat-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
}

.chat-header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.mode-switch {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 8px;
  background-color: rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

.mode-switch:hover {
  background-color: rgba(255, 255, 255, 0.2);
}

.mode-label {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  min-width: 28px;
  text-align: center;
  letter-spacing: 0.5px;
}

.fullscreen-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: rgba(255, 255, 255, 0.8);
  background-color: rgba(255, 255, 255, 0.1);
}

.fullscreen-btn:hover {
  background-color: rgba(255, 255, 255, 0.25);
  color: white;
  transform: scale(1.1);
}

/* 全屏模式 */
.consultation-wrapper.is-fullscreen .chat-messages {
  padding: 32px 10%;
}

.consultation-wrapper.is-fullscreen .ai-message .message-content-wrapper,
.consultation-wrapper.is-fullscreen .user-message .message-content-wrapper {
  max-width: 70%;
}

.status-dot {
  width: 10px;
  height: 10px;
  background-color: #4caf50;
  border-radius: 50%;
  animation: pulse 2s infinite;
  box-shadow: 0 0 8px rgba(76, 175, 80, 0.6);
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background-color: #f0f2f5;
  background-image: 
    radial-gradient(circle at 25px 25px, #e3f2fd 2%, transparent 0%),
    radial-gradient(circle at 75px 75px, #e3f2fd 2%, transparent 0%);
  background-size: 50px 50px;
}

.message-item {
  display: flex;
  flex-direction: column;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 系统消息 */
.system-message {
  text-align: center;
  padding: 10px 0;
}

.system-content {
  display: inline-block;
  background-color: rgba(25, 118, 210, 0.1);
  color: #1976d2;
  font-size: 12px;
  padding: 8px 16px;
  border-radius: 16px;
  font-weight: 500;
}

.system-time {
  font-size: 11px;
  color: #999;
  margin-top: 6px;
}

/* 用户消息 */
.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 16px;
}

.user-message .message-content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 75%;
}

.user-bubble {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  color: white;
  padding: 16px 20px;
  border-radius: 20px 20px 4px 20px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.2);
  position: relative;
  overflow: hidden;
}

.user-bubble::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.1) 0%, transparent 100%);
  pointer-events: none;
}

/* AI消息 */
.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 16px;
}

.ai-message .message-content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 75%;
}

.ai-bubble {
  background-color: white;
  color: #333;
  padding: 20px 24px;
  border-radius: 20px 20px 20px 4px;
  font-size: 15px;
  line-height: 1.8;
  word-break: break-word;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  max-width: 100%;
  position: relative;
  overflow: hidden;
}

.ai-bubble::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(135deg, #4caf50 0%, #45a049 100%);
  border-radius: 4px 0 0 4px;
}

.ai-bubble .knowledge-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed #e0e0e0;
}

.knowledge-section-title {
  margin: 0 0 14px 0;
  color: #1976d2;
  font-size: 14px;
  font-weight: 600;
}

.knowledge-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.knowledge-card {
  background: #fafbfc;
  border: 1px solid #e8ecf1;
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.25s ease;
  cursor: default;
}

.knowledge-card:hover {
  background: #fff;
  border-color: #c5d5e8;
  box-shadow: 0 4px 16px rgba(25, 118, 210, 0.08);
  transform: translateY(-2px);
}

.knowledge-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.knowledge-card-icon {
  color: #1976d2;
  flex-shrink: 0;
}

.knowledge-card-title {
  font-weight: 600;
  font-size: 13px;
  color: #1a1a1a;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-card-body {
  font-size: 13px;
  color: #555;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease;
}

.avatar:hover {
  transform: scale(1.05);
}

.user-avatar {
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  color: white;
}

.ai-avatar {
  background: linear-gradient(135deg, #4caf50 0%, #45a049 100%);
  color: white;
}

.avatar .el-icon {
  font-size: 24px;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
  font-weight: 500;
}

/* 加载动画 */
.loading-dots {
  display: flex;
  gap: 6px;
  padding: 8px 0;
}

.loading-dots span {
  width: 10px;
  height: 10px;
  background-color: #1976d2;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
  box-shadow: 0 2px 4px rgba(25, 118, 210, 0.3);
}

.loading-dots span:nth-child(1) {
  animation-delay: -0.32s;
}
.loading-dots span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1.2);
  }
}

/* 输入区域 - 动态收缩 */
.chat-input-area {
  background: linear-gradient(90deg, #ffffff 0%, #f8f9fa 100%);
  border-top: 1px solid #e0e0e0;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

/* 收起状态 */
.chat-input-area:not(.expanded) {
  padding: 12px 28px;
}

/* 展开状态 */
.chat-input-area.expanded {
  padding: 16px 28px 14px;
}

/* 收起时的单行条 */
.input-collapsed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-radius: 24px;
  background-color: #f5f6f8;
  border: 1px solid #e8e8e8;
  cursor: pointer;
  transition: all 0.3s ease;
}

.input-collapsed:hover {
  background-color: #eef0f4;
  border-color: #1976d2;
  box-shadow: 0 2px 8px rgba(25, 118, 210, 0.1);
}

.collapsed-placeholder {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #999;
  font-size: 14px;
  user-select: none;
}

.collapsed-arrow {
  color: #bbb;
  transition: transform 0.3s ease;
}

.input-collapsed:hover .collapsed-arrow {
  color: #1976d2;
  transform: translateY(-2px);
}

/* 展开时的内容 */
.input-expanded-content {
  animation: slideUp 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.input-toolbar .el-button {
  color: #666;
  font-size: 20px;
  transition: all 0.3s ease;
  padding: 8px;
  border-radius: 8px;
}

.input-toolbar .el-button:hover {
  color: #1976d2;
  background-color: rgba(25, 118, 210, 0.05);
  transform: translateY(-2px);
}

.collapse-btn {
  cursor: pointer;
  color: #bbb;
  padding: 6px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.collapse-btn:hover {
  color: #1976d2;
  background-color: rgba(25, 118, 210, 0.08);
}

.input-box {
  display: flex;
  gap: 16px;
  align-items: flex-end;
}

.input-box .el-textarea {
  flex: 1;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

:deep(.input-box .el-textarea__inner) {
  border-radius: 12px;
  border-color: #e0e0e0;
  font-size: 15px;
  line-height: 1.6;
  padding: 16px;
  resize: none;
  transition: all 0.3s ease;
}

:deep(.input-box .el-textarea__inner:focus) {
  border-color: #1976d2;
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.1);
}

.send-btn {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  border: none;
  box-shadow: 0 6px 16px rgba(25, 118, 210, 0.4);
  transition: all 0.3s ease;
  cursor: pointer;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.1);
  box-shadow: 0 8px 20px rgba(25, 118, 210, 0.5);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.send-btn:disabled {
  background: #e0e0e0;
  box-shadow: none;
  cursor: not-allowed;
  transform: none;
}

.send-btn .el-icon {
  font-size: 20px;
  color: white;
}

/* 中断按钮样式 */
.abort-btn {
  background: linear-gradient(135deg, #e53935 0%, #c62828 100%) !important;
  box-shadow: 0 6px 16px rgba(229, 57, 53, 0.4) !important;
  animation: abortPulse 1.5s ease-in-out infinite;
}

.abort-btn:hover {
  box-shadow: 0 8px 20px rgba(229, 57, 53, 0.5) !important;
}

@keyframes abortPulse {
  0%, 100% { box-shadow: 0 6px 16px rgba(229, 57, 53, 0.4); }
  50% { box-shadow: 0 6px 20px rgba(229, 57, 53, 0.6); }
}

.input-hint {
  margin-top: 12px;
  font-size: 13px;
  color: #666;
  text-align: center;
  font-weight: 500;
}

/* 滚动条样式 */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
  transition: background 0.3s ease;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 回到底部浮动按钮 */
.scroll-bottom-btn {
  position: absolute;
  bottom: 130px;
  right: 32px;
  z-index: 10;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: white;
  color: #1976d2;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
}

.scroll-bottom-btn:hover {
  background: #1976d2;
  color: white;
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(25, 118, 210, 0.4);
}

/* 浮动按钮过渡动画 */
.fade-up-enter-active,
.fade-up-leave-active {
  transition: all 0.25s ease;
}
.fade-up-enter-from,
.fade-up-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* ========== 欢迎页面 ========== */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  animation: welcomeFadeIn 0.5s ease;
}

@keyframes welcomeFadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.welcome-icon {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 32px rgba(25, 118, 210, 0.3);
}

.welcome-title {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 10px;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.welcome-subtitle {
  font-size: 15px;
  color: #888;
  margin: 0 0 36px;
  max-width: 420px;
  line-height: 1.6;
}

.suggested-questions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 560px;
  width: 100%;
}

.suggested-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: white;
  border: 1px solid #e8ecf1;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s ease;
  font-size: 14px;
  color: #333;
  text-align: left;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.suggested-card:hover {
  border-color: #1976d2;
  box-shadow: 0 6px 20px rgba(25, 118, 210, 0.12);
  transform: translateY(-3px);
}

.suggested-card .el-icon {
  flex-shrink: 0;
}
</style>
