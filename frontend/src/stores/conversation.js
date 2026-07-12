import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../utils/axios'

/**
 * 会话管理 Store
 * 管理会话列表、当前会话、消息加载等状态
 */
export const useConversationStore = defineStore('conversation', () => {
  // 会话列表
  const conversations = ref([])
  // 当前选中的会话ID（null 表示新对话）
  const currentConversationId = ref(null)
  // 加载状态
  const loading = ref(false)

  // 当前选中的会话对象
  const currentConversation = computed(() => {
    if (!currentConversationId.value) return null
    return conversations.value.find(c => c.id === currentConversationId.value) || null
  })

  /**
   * 加载当前用户的会话列表
   */
  async function loadConversations() {
    loading.value = true
    try {
      const res = await request.get('/conversation/list')
      conversations.value = res.data?.list || []
    } catch (error) {
      console.error('加载会话列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  /**
   * 开始新对话（重置当前会话ID）
   */
  function startNewConversation() {
    currentConversationId.value = null
  }

  /**
   * 选择一个已有会话
   * @param {number} id 会话ID
   */
  function selectConversation(id) {
    currentConversationId.value = id
  }

  /**
   * 设置当前会话ID（从后端响应中获取，首次发消息后后端自动创建会话返回ID）
   * @param {number} id 会话ID
   */
  function setCurrentConversationId(id) {
    currentConversationId.value = id
    // 如果列表中没有这个会话（新创建的），刷新列表
    if (id && !conversations.value.find(c => c.id === id)) {
      loadConversations()
    }
  }

  /**
   * 重命名会话
   * @param {number} id 会话ID
   * @param {string} title 新标题
   */
  async function renameConversation(id, title) {
    try {
      await request.put(`/conversation/${id}/rename`, { title })
      const conv = conversations.value.find(c => c.id === id)
      if (conv) conv.title = title
    } catch (error) {
      console.error('重命名会话失败:', error)
      throw error
    }
  }

  /**
   * 删除会话（软删除）
   * @param {number} id 会话ID
   */
  async function deleteConversation(id) {
    try {
      await request.delete(`/conversation/${id}`)
      conversations.value = conversations.value.filter(c => c.id !== id)
      // 如果删除的是当前会话，重置为新对话
      if (currentConversationId.value === id) {
        currentConversationId.value = null
      }
    } catch (error) {
      console.error('删除会话失败:', error)
      throw error
    }
  }

  /**
   * 加载指定会话的消息列表
   * @param {number} conversationId 会话ID
   * @returns {Array} 消息列表
   */
  async function loadMessages(conversationId) {
    try {
      const res = await request.get(`/conversation/${conversationId}/messages`)
      return res.data?.list || []
    } catch (error) {
      console.error('加载会话消息失败:', error)
      return []
    }
  }

  return {
    conversations,
    currentConversationId,
    loading,
    currentConversation,
    loadConversations,
    startNewConversation,
    selectConversation,
    setCurrentConversationId,
    renameConversation,
    deleteConversation,
    loadMessages
  }
})
