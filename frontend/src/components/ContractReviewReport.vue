<template>
  <div class="review-report">
    <div class="report-content">
      <MarkdownContent :content="content" :streaming="streaming" />
    </div>
    <div v-if="streaming" class="streaming-indicator">
      <span></span><span></span><span></span>
      AI 正在分析中...
    </div>
  </div>
</template>

<script setup>
import MarkdownContent from "./MarkdownContent.vue";

defineProps({
  content: {
    type: String,
    default: "",
  },
  streaming: {
    type: Boolean,
    default: false,
  },
});
</script>

<style scoped>
.review-report {
  background: white;
  border-radius: 16px;
  padding: 36px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.report-content {
  font-size: 15px;
  line-height: 1.8;
  color: #333;
}

.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #eee;
  color: #1976d2;
  font-size: 14px;
  font-weight: 500;
}

.streaming-indicator span {
  width: 8px;
  height: 8px;
  background-color: #1976d2;
  border-radius: 50%;
  animation: dotBounce 1.4s infinite ease-in-out both;
}

.streaming-indicator span:nth-child(1) { animation-delay: -0.32s; }
.streaming-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes dotBounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
