/**
 * SSE 流式请求工具（带指数退避重连机制）
 * 使用 fetch + ReadableStream 实现，支持 POST 请求和 Authorization 头
 * 原生 EventSource 仅支持 GET 请求，无法满足需求
 * 
 * 指数退避策略：
 * - 第 1 次失败：等待 1 秒后重连 (1 × 2^0)
 * - 第 2 次失败：等待 2 秒后重连 (1 × 2^1)
 * - 第 3 次失败：等待 4 秒后重连 (1 × 2^2)
 * - 最大等待 10 秒，最多重试 3 次
 */

// 重连配置
const MAX_RETRIES = 3              // 最大重试次数
const INITIAL_DELAY = 1000         // 初始延迟 1 秒
const MAX_DELAY = 10000            // 最大延迟 10 秒
const BACKOFF_MULTIPLIER = 2       // 退避倍数（指数增长）

/**
 * 计算指数退避延迟时间
 * @param {number} retryCount 当前重试次数
 * @returns {number} 延迟时间（毫秒）
 */
function calculateBackoffDelay(retryCount) {
  const delay = INITIAL_DELAY * Math.pow(BACKOFF_MULTIPLIER, retryCount)
  return Math.min(delay, MAX_DELAY)
}

/**
 * 发起 SSE 流式聊天请求（带自动重连）
 * @param {Object} options 请求选项
 * @param {Object} options.data 请求体 { userId, question, conversationId }
 * @param {Function} options.onToken 收到文本片段回调 (token: string)
 * @param {Function} options.onKnowledge 收到相关法条回调 (knowledgeList: Array)
 * @param {Function} options.onDone 流式完成回调 ({ conversationId })
 * @param {Function} options.onError 错误回调 (errorMessage: string)
 * @param {number} options.retryCount 当前重试次数（内部使用）
 * @returns {Object} { abort } 返回一个包含 abort 方法的对象，用于中断请求
 */
export function streamChat({ data, onToken, onKnowledge, onDone, onError, retryCount = 0 }) {
  const controller = new AbortController()
  const { signal } = controller
  let aborted = false

  // 获取 token 用于鉴权
  const token = localStorage.getItem('token')

  // 异步执行 SSE 请求
  ;(async () => {
    try {
      console.log(`[SSE] 发起请求 (重试次数: ${retryCount}/${MAX_RETRIES})`)
      
      const response = await fetch('/api/ai-chat/ask-stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(data),
        signal
      })

      if (!response.ok) {
        const errorText = await response.text()
        console.error('[SSE] 请求失败:', response.status, errorText)
        
        // 如果是 401 未授权，不重试（应该跳转登录）
        if (response.status === 401) {
          onError?.('登录已过期，请重新登录')
          return
        }
        
        // 触发重连机制
        handleReconnection({
          retryCount,
          error: `请求失败: ${response.status}`,
          onError,
          data,
          onToken,
          onKnowledge,
          onDone
        })
        return
      }

      // 使用 ReadableStream 逐块读取 SSE 数据
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        // 将二进制数据解码并追加到缓冲区
        buffer += decoder.decode(value, { stream: true })

        // 按双换行符分割 SSE 事件（SSE 标准格式：事件之间用空行分隔）
        const parts = buffer.split('\n\n')
        // 最后一个元素可能是不完整的事件，保留到下次处理
        buffer = parts.pop() || ''

        for (const part of parts) {
          if (!part.trim()) continue
          processSSEEvent(part, { onToken, onKnowledge, onDone, onError })
        }
      }

      // 处理缓冲区中剩余的数据
      if (buffer.trim()) {
        processSSEEvent(buffer, { onToken, onKnowledge, onDone, onError })
      }
      
      console.log('[SSE] 流式传输完成')
    } catch (err) {
      // 用户主动中断不视为错误
      if (err.name === 'AbortError' || aborted) {
        console.info('[SSE] 用户中断请求')
        return
      }
      
      console.error('[SSE] 流式请求异常:', err)
      
      // 触发重连机制
      handleReconnection({
        retryCount,
        error: '网络连接异常',
        onError,
        data,
        onToken,
        onKnowledge,
        onDone
      })
    }
  })()

  return {
    abort: () => {
      aborted = true
      controller.abort()
    }
  }
}

/**
 * 处理重连逻辑（指数退避策略）
 * @param {Object} params 重连参数
 */
function handleReconnection({ retryCount, error, onError, data, onToken, onKnowledge, onDone }) {
  // 超过最大重试次数，放弃重连
  if (retryCount >= MAX_RETRIES) {
    console.error(`[SSE] 重试次数已达上限 (${MAX_RETRIES})，放弃重连`)
    onError?.(`${error}，请稍后再试`)
    return
  }
  
  // 计算退避延迟时间
  const delay = calculateBackoffDelay(retryCount)
  console.log(`[SSE] ${delay}ms 后第 ${retryCount + 1} 次重连...`)
  
  // 通知用户正在重连
  if (retryCount === 0) {
    onError?.('连接断开，正在重连...')
  }
  
  // 延迟后重试
  setTimeout(() => {
    streamChat({
      data,
      onToken,
      onKnowledge,
      onDone,
      onError,
      retryCount: retryCount + 1
    })
  }, delay)
}

/**
 * 发起 Agent 模式 SSE 流式请求（/api/agent/ask）
 *
 * Agent SSE 事件格式（与传统模式不同）：
 * - event:message data:单字符 → 逐字推送
 * - event:done    data:{"status":"completed"} → 完成
 * - event:error   data:{"message":"..."} → 错误
 *
 * @param {Object} options 请求选项
 * @param {Object} options.data 请求体 { question, conversationId }
 * @param {Function} options.onToken 收到文本片段回调 (token: string)
 * @param {Function} options.onDone 流式完成回调
 * @param {Function} options.onError 错误回调 (errorMessage: string)
 * @returns {Object} { abort } 返回中断控制器
 */
export function streamAgentChat({ data, onToken, onDone, onError }) {
  const controller = new AbortController()
  const { signal } = controller
  let aborted = false

  const token = localStorage.getItem('token')

  ;(async () => {
    try {
      const response = await fetch('/api/agent/ask', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(data),
        signal
      })

      if (!response.ok) {
        const errorText = await response.text()
        console.error('[Agent SSE] 请求失败:', response.status, errorText)
        if (response.status === 401) {
          onError?.('登录已过期，请重新登录')
          return
        }
        onError?.(`请求失败: ${response.status}`)
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split('\n\n')
        buffer = parts.pop() || ''

        for (const part of parts) {
          if (!part.trim()) continue
          processAgentSSEEvent(part, { onToken, onDone, onError })
        }
      }

      if (buffer.trim()) {
        processAgentSSEEvent(buffer, { onToken, onDone, onError })
      }
    } catch (err) {
      if (err.name === 'AbortError' || aborted) {
        console.info('[Agent SSE] 用户中断请求')
        return
      }
      console.error('[Agent SSE] 流式请求异常:', err)
      onError?.('网络连接异常，请稍后重试')
    }
  })()

  return {
    abort: () => {
      aborted = true
      controller.abort()
    }
  }
}

/**
 * 解析并处理单个 Agent SSE 事件
 * Agent 格式: event: message\ndata: 单字符\n\n (data 是裸字符，非 JSON)
 *            event: done\ndata: {"status":"completed"}\n\n
 *            event: error\ndata: {"message":"..."}\n\n
 */
function processAgentSSEEvent(raw, { onToken, onDone, onError }) {
  let eventName = ''
  let eventData = ''

  const lines = raw.split('\n')
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.substring(6).trim()
    } else if (line.startsWith('data:')) {
      eventData = line.substring(5).trim()
    }
  }

  if (!eventData && eventName !== 'message') return

  try {
    switch (eventName) {
      case 'message': {
        // Agent 模式逐字推送，data 就是字符本身
        onToken?.(eventData || '')
        break
      }
      case 'done': {
        const parsed = JSON.parse(eventData)
        onDone?.({ conversationId: parsed.conversationId, chatId: parsed.chatId })
        break
      }
      case 'error': {
        const parsed = JSON.parse(eventData)
        onError?.(parsed.message || 'Agent 回答生成失败')
        break
      }
      default:
        break
    }
  } catch (parseErr) {
    console.warn('[Agent SSE] 事件数据解析失败:', eventData, parseErr)
  }
}

/**
 * 解析并处理单个 SSE 事件
 * SSE 格式: event: xxx\ndata: {...}\n\n
 * @param {string} raw 原始 SSE 事件文本
 * @param {Object} callbacks 回调函数集合
 */
function processSSEEvent(raw, { onToken, onKnowledge, onDone, onError }) {
  let eventName = ''
  let eventData = ''

  const lines = raw.split('\n')
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.substring(6).trim()
    } else if (line.startsWith('data:')) {
      eventData = line.substring(5).trim()
    }
  }

  if (!eventData) return

  try {
    switch (eventName) {
      case 'token': {
        const parsed = JSON.parse(eventData)
        onToken?.(parsed.content || '')
        break
      }
      case 'knowledge': {
        const parsed = JSON.parse(eventData)
        onKnowledge?.(parsed.list || [])
        break
      }
      case 'done': {
        const parsed = JSON.parse(eventData)
        onDone?.({ conversationId: parsed.conversationId, chatId: parsed.chatId })
        break
      }
      case 'error': {
        const parsed = JSON.parse(eventData)
        onError?.(parsed.message || 'AI回答生成失败')
        break
      }
      default:
        // 未知事件类型，尝试作为 token 处理（兼容无 event 字段的情况）
        if (eventData) {
          try {
            const parsed = JSON.parse(eventData)
            if (parsed.content !== undefined) {
              onToken?.(parsed.content)
            }
          } catch {
            // 非 JSON 数据，忽略
          }
        }
        break
    }
  } catch (parseErr) {
    console.warn('[SSE] 事件数据解析失败:', eventData, parseErr)
  }
}
