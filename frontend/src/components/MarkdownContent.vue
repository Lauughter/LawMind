<template>
  <div class="markdown-body" :class="{ 'is-streaming': streaming }">
    <span v-html="renderedHtml"></span>
    <span v-if="streaming" class="streaming-cursor"></span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  streaming: {
    type: Boolean,
    default: false
  }
})

const renderedHtml = computed(() => renderMarkdown(props.content))
</script>

<style>
/* 引入 highlight.js 主题 */
@import 'highlight.js/styles/github.css';

/* Markdown 渲染样式 */
.markdown-body {
  font-size: 15.5px;
  line-height: 1.85;
  color: #333;
  word-break: break-word;
}

/* ========== 标题样式 ========== */
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin: 24px 0 12px;
  font-weight: 600;
  line-height: 1.4;
  color: #1a1a1a;
}

.markdown-body h1 { font-size: 1.5em; font-weight: 700; }
.markdown-body h2 {
  font-size: 1.3em;
  font-weight: 650;
  padding-bottom: 6px;
  border-bottom: 2px solid rgba(25, 118, 210, 0.15);
}
.markdown-body h3 { font-size: 1.15em; font-weight: 600; }
.markdown-body h4 { font-size: 1.05em; }

/* 段落 */
.markdown-body p {
  margin: 8px 0 1em;
}

/* ========== 列表样式 ========== */
.markdown-body ul,
.markdown-body ol {
  padding-left: 24px;
  margin: 8px 0;
}

.markdown-body li {
  margin: 4px 0;
}

.markdown-body li::marker {
  color: #1976d2;
}

/* 嵌套列表间距优化 */
.markdown-body li > ul,
.markdown-body li > ol {
  margin: 4px 0;
}

/* ========== 引用块（法律依据区域） ========== */
.markdown-body blockquote {
  margin: 14px 0;
  padding: 12px 18px;
  border-left: 4px solid #1976d2;
  background: linear-gradient(135deg, rgba(25, 118, 210, 0.06) 0%, rgba(25, 118, 210, 0.02) 100%);
  border-radius: 0 8px 8px 0;
  color: #444;
  font-size: 14px;
}

.markdown-body blockquote p {
  margin: 4px 0;
}

/* 嵌套引用（用于法条详细内容） */
.markdown-body blockquote blockquote {
  margin: 8px 0;
  border-left-color: #4caf50;
  background-color: rgba(76, 175, 80, 0.04);
}

/* ========== 行内代码 ========== */
.markdown-body code:not(.hljs) {
  background-color: rgba(25, 118, 210, 0.08);
  color: #d63384;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* ========== 代码块 ========== */
.markdown-body .hljs-code-block {
  margin: 12px 0;
  border-radius: 8px;
  overflow: hidden;
  background-color: #f6f8fa;
  border: 1px solid #e8e8e8;
}

.markdown-body .hljs-code-block code {
  display: block;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* ========== 表格 ========== */
.markdown-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 14px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e0e0e0;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #e0e0e0;
  padding: 10px 14px;
  text-align: left;
}

.markdown-body th {
  background-color: #f0f5ff;
  font-weight: 600;
  color: #1976d2;
}

.markdown-body tr:nth-child(even) {
  background-color: #fafafa;
}

.markdown-body tr:hover {
  background-color: rgba(25, 118, 210, 0.04);
}

/* ========== 分割线 ========== */
.markdown-body hr {
  border: none;
  border-top: 2px solid #e8e8e8;
  margin: 20px 0;
}

/* ========== 链接 ========== */
.markdown-body a {
  color: #1976d2;
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s;
}

.markdown-body a:hover {
  border-bottom-color: #1976d2;
}

/* ========== 加粗和强调 ========== */
.markdown-body strong {
  font-weight: 600;
  color: #1a1a1a;
}

.markdown-body em {
  font-style: italic;
  color: #555;
}

/* ========== 图片 ========== */
.markdown-body img {
  max-width: 100%;
  border-radius: 8px;
  margin: 8px 0;
}

/* ========== 法律特殊书名号引用样式 ========== */
/* 对于包含《》书名号的内容高亮显示 */
.markdown-body p strong,
.markdown-body li strong {
  color: #1565c0;
}

/* ========== 有序列表数字样式增强 ========== */
.markdown-body ol {
  counter-reset: list-counter;
  list-style: none;
  padding-left: 28px;
}

.markdown-body ol > li {
  counter-increment: list-counter;
  position: relative;
}

.markdown-body ol > li::before {
  content: counter(list-counter) ".";
  position: absolute;
  left: -24px;
  color: #1976d2;
  font-weight: 600;
  font-size: 14px;
}

/* ========== 流式输出闪烁光标 ========== */
.streaming-cursor {
  display: inline-block;
  width: 8px;
  height: 1.1em;
  background-color: #1976d2;
  border-radius: 2px;
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: cursorBlink 0.8s steps(2) infinite;
}

@keyframes cursorBlink {
  0% { opacity: 1; }
  50% { opacity: 0; }
  100% { opacity: 1; }
}

/* ========== 自定义容器（warning / tip / danger） ========== */
.md-container {
  margin: 14px 0;
  padding: 14px 18px;
  border-radius: 8px;
  border-left: 4px solid;
  font-size: 14px;
  line-height: 1.7;
}

.md-container-title {
  font-weight: 600;
  margin-bottom: 6px;
  font-size: 14px;
}

.md-container-warning {
  background: #fff8e6;
  border-left-color: #f0a020;
  color: #6b4a0a;
}
.md-container-warning .md-container-title { color: #b87a14; }

.md-container-tip {
  background: rgba(25, 118, 210, 0.04);
  border-left-color: #1976d2;
  color: #1a3a5c;
}
.md-container-tip .md-container-title { color: #1976d2; }

.md-container-danger {
  background: #fff0f0;
  border-left-color: #e53935;
  color: #6b1a1a;
}
.md-container-danger .md-container-title { color: #c62828; }

.md-container p {
  margin: 4px 0;
}

/* ========== 任务列表复选框 ========== */
.markdown-body ul.task-list {
  padding-left: 4px;
  list-style: none;
}
.markdown-body ul.task-list > li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 4px 0;
}
.markdown-body ul.task-list > li::before {
  display: none;
}
.markdown-body .task-list-item-checkbox {
  margin-top: 3px;
  accent-color: #1976d2;
  width: 16px;
  height: 16px;
  cursor: pointer;
  flex-shrink: 0;
}

/* ========== 表格响应式包装 ========== */
.table-responsive-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin: 12px 0;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.table-responsive-wrapper table {
  margin: 0;
  border-radius: 0;
  min-width: 100%;
  border: none;
}
</style>
