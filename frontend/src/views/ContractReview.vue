<template>
  <div class="contract-review-page">
    <!-- 上传区域 -->
    <div class="upload-section" v-if="!reviewing && !reviewResult">
      <div class="upload-card">
        <div class="upload-header">
          <h2>合同审查</h2>
          <p>上传合同文件，AI 将逐条审查条款的合法性与公平性</p>
        </div>

        <el-upload
          ref="uploadRef"
          class="upload-area"
          drag
          :auto-upload="false"
          :limit="1"
          :accept="acceptedFormats"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :file-list="fileList"
        >
          <el-icon class="upload-icon" size="56"><UploadFilled /></el-icon>
          <div class="upload-text">
            <p class="upload-title">点击或拖拽合同文件到此处</p>
            <p class="upload-hint">支持 PDF、Word (.doc/.docx)、TXT 格式，最大 20MB</p>
          </div>
        </el-upload>

        <div class="upload-info" v-if="!selectedFile">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              <span>审查说明</span>
            </template>
            <ul>
              <li>AI 将基于系统法律知识库中的真实法条进行逐条审查</li>
              <li>审查维度包括：主体资格、标的明确性、违约责任、争议解决等 10 大维度</li>
              <li>同时检测常见不公平条款（最终解释权、过高违约金、单方解除权等）</li>
              <li>审查报告仅供法律参考，不构成正式法律意见</li>
              <li><strong>阅后即焚：</strong>合同文本仅在内存中处理，不会存储到服务器</li>
            </ul>
          </el-alert>
        </div>

        <div class="upload-actions">
          <el-button
            type="primary"
            size="large"
            :disabled="!selectedFile"
            :loading="uploading"
            @click="startReview"
          >
            <el-icon><Finished /></el-icon>
            {{ uploading ? '正在提取文件...' : '开始审查' }}
          </el-button>
          <span class="file-name" v-if="selectedFile">{{ selectedFile.name }}（{{ formatFileSize(selectedFile.size) }}）</span>
        </div>
      </div>
    </div>

    <!-- 审查进度 -->
    <div class="reviewing-section" v-if="reviewing">
      <div class="review-card">
        <div class="review-status">
          <el-icon class="status-icon rotating" size="32"><Loading /></el-icon>
          <h3>正在审查合同...</h3>
          <p>{{ progressMessage }}</p>
        </div>
        <div class="review-pipeline">
          <div class="pipeline-step" :class="{ active: progressPercent >= 0, done: progressPercent >= 20 }">
            <el-icon><Document /></el-icon>
            <span>文本提取</span>
          </div>
          <div class="pipeline-connector" :class="{ active: progressPercent >= 20 }"></div>
          <div class="pipeline-step" :class="{ active: progressPercent >= 20, done: progressPercent >= 40 }">
            <el-icon><Search /></el-icon>
            <span>知识库检索法条</span>
          </div>
          <div class="pipeline-connector" :class="{ active: progressPercent >= 40 }"></div>
          <div class="pipeline-step" :class="{ active: progressPercent >= 40, done: progressPercent >= 70 }">
            <el-icon><Finished /></el-icon>
            <span>逐条对照分析</span>
          </div>
          <div class="pipeline-connector" :class="{ active: progressPercent >= 70 }"></div>
          <div class="pipeline-step" :class="{ active: progressPercent >= 70, done: progressPercent >= 100 }">
            <el-icon><Checked /></el-icon>
            <span>生成审查报告</span>
          </div>
        </div>
        <el-progress :percentage="progressPercent" :stroke-width="6" :indeterminate="progressPercent < 10" />
        <div class="review-stream" v-if="streamContent">
          <ContractReviewReport :content="streamContent" :streaming="true" />
        </div>
      </div>
    </div>

    <!-- 审查结果 -->
    <div class="result-section" v-if="reviewResult && !reviewing">
      <ContractReviewReport :content="reviewResult" :streaming="false" />
      <div class="result-actions">
        <el-button type="primary" @click="resetReview">
          <el-icon><Plus /></el-icon>
          审查新合同
        </el-button>
        <el-button @click="copyReport">
          <el-icon><CopyDocument /></el-icon>
          复制报告
        </el-button>
        <el-popconfirm title="确定要下载审查报告吗？" @confirm="downloadReport">
          <template #reference>
            <el-button>
              <el-icon><Download /></el-icon>
              下载报告
            </el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>

    <!-- 错误提示 -->
    <div class="error-section" v-if="errorMessage && !reviewing">
      <el-result icon="error" title="审查失败" :sub-title="errorMessage">
        <template #extra>
          <el-button type="primary" @click="resetReview">重新上传</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import {
  UploadFilled,
  Finished,
  Loading,
  Plus,
  CopyDocument,
  Download,
  Document,
  Search,
  Checked,
} from "@element-plus/icons-vue";
import ContractReviewReport from "../components/ContractReviewReport.vue";

const acceptedFormats = ".pdf,.doc,.docx,.txt";

const uploadRef = ref(null);
const fileList = ref([]);
const selectedFile = ref(null);
const uploading = ref(false);
const reviewing = ref(false);
const reviewResult = ref("");
const streamContent = ref("");
const progressMessage = ref("");
const progressPercent = ref(0);
const errorMessage = ref("");
let abortController = null;

function formatFileSize(bytes) {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

function handleFileChange(file) {
  selectedFile.value = file.raw;
}

function handleFileRemove() {
  selectedFile.value = null;
}

function startReview() {
  if (!selectedFile.value) {
    ElMessage.warning("请先选择合同文件");
    return;
  }

  uploading.value = true;
  progressMessage.value = "正在上传文件...";
  progressPercent.value = 5;

  const formData = new FormData();
  formData.append("file", selectedFile.value);

  abortController = new AbortController();
  const token = localStorage.getItem("token");

  fetch("/api/contract-review/upload", {
    method: "POST",
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: formData,
    signal: abortController.signal,
  })
    .then((response) => {
      if (!response.ok) {
        return response.text().then((text) => {
          throw new Error(`请求失败 (${response.status}): ${text}`);
        });
      }

      uploading.value = false;
      reviewing.value = true;
      progressPercent.value = 10;

      const reader = response.body.getReader();
      const decoder = new TextDecoder("utf-8");
      let buffer = "";

      function processLoop() {
        reader
          .read()
          .then(({ done, value }) => {
            if (done) {
              reviewing.value = false;
              reviewResult.value = streamContent.value;
              ElMessage.success("合同审查完成");
              return;
            }

            buffer += decoder.decode(value, { stream: true });
            const parts = buffer.split("\n\n");
            buffer = parts.pop() || "";

            for (const part of parts) {
              if (!part.trim()) continue;
              processEvent(part);
            }

            processLoop();
          })
          .catch((err) => {
            if (err.name === "AbortError") {
              return;
            }
            console.error("[ContractReview] 流读取失败:", err);
            reviewing.value = false;
            errorMessage.value = "网络连接异常，请稍后重试";
          });
      }

      processLoop();
    })
    .catch((err) => {
      if (err.name === "AbortError") return;
      console.error("[ContractReview] 请求失败:", err);
      uploading.value = false;
      errorMessage.value = err.message || "文件上传失败，请稍后重试";
    });
}

function processEvent(raw) {
  let eventName = "";
  let eventData = "";

  const lines = raw.split("\n");
  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventName = line.substring(6).trim();
    } else if (line.startsWith("data:")) {
      eventData = line.substring(5).trim();
    }
  }

  if (!eventData) return;

  try {
    switch (eventName) {
      case "progress": {
        const parsed = JSON.parse(eventData);
        progressMessage.value = parsed.message || "";
        progressPercent.value = Math.min(progressPercent.value + 5, 90);
        break;
      }
      case "message": {
        streamContent.value += eventData;
        if (progressPercent.value < 90) {
          progressPercent.value = Math.min(progressPercent.value + 1, 90);
        }
        break;
      }
      case "done": {
        progressPercent.value = 100;
        break;
      }
      case "error": {
        const parsed = JSON.parse(eventData);
        errorMessage.value = parsed.message || "审查失败";
        reviewing.value = false;
        break;
      }
    }
  } catch {
    // 非 JSON 数据当作流式内容处理
    if (eventName === "message") {
      streamContent.value += eventData;
    }
  }
}

function resetReview() {
  if (abortController) {
    abortController.abort();
    abortController = null;
  }
  selectedFile.value = null;
  fileList.value = [];
  streamContent.value = "";
  reviewResult.value = "";
  progressMessage.value = "";
  progressPercent.value = 0;
  errorMessage.value = "";
  uploading.value = false;
  reviewing.value = false;
}

function copyReport() {
  const text = reviewResult.value || streamContent.value;
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success("报告已复制到剪贴板");
  }).catch(() => {
    ElMessage.error("复制失败");
  });
}

function downloadReport() {
  const text = reviewResult.value || streamContent.value;
  const blob = new Blob([text], { type: "text/markdown;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "合同审查报告_" + new Date().toISOString().slice(0, 10) + ".md";
  link.click();
  URL.revokeObjectURL(url);
  ElMessage.success("报告下载中...");
}
</script>

<style scoped>
.contract-review-page {
  height: 100%;
  padding: 24px;
  overflow-y: auto;
  background-color: #f0f2f5;
  display: flex;
  justify-content: center;
}

.upload-section {
  width: 100%;
  max-width: 720px;
  margin-top: 40px;
}

.upload-card {
  background: white;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.upload-header {
  text-align: center;
  margin-bottom: 32px;
}

.upload-header h2 {
  margin: 0 0 10px;
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
}

.upload-header p {
  margin: 0;
  font-size: 15px;
  color: #888;
}

.upload-area {
  margin-bottom: 20px;
}

.upload-icon {
  color: #1976d2;
  margin-bottom: 8px;
}

.upload-title {
  margin: 0 0 8px;
  font-size: 16px;
  color: #333;
  font-weight: 500;
}

.upload-hint {
  margin: 0;
  font-size: 13px;
  color: #999;
}

.upload-info {
  margin-bottom: 24px;
}

.upload-info ul {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 13px;
  color: #666;
  line-height: 1.8;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  justify-content: center;
}

.file-name {
  font-size: 13px;
  color: #666;
}

/* 审查进度 */
.reviewing-section {
  width: 100%;
  max-width: 960px;
}

.review-card {
  background: white;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.review-status {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.review-status h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.review-status p {
  margin: 0;
  font-size: 14px;
  color: #888;
}

.status-icon {
  color: #1976d2;
}

.rotating {
  animation: rotate 1.5s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 审查流水线 */
.review-pipeline {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  margin-bottom: 20px;
  padding: 16px 0;
}

.pipeline-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #bbb;
  transition: all 0.4s ease;
  min-width: 80px;
}

.pipeline-step .el-icon {
  font-size: 20px;
}

.pipeline-step.active {
  color: #1976d2;
}

.pipeline-step.done {
  color: #4caf50;
}

.pipeline-connector {
  width: 40px;
  height: 3px;
  background-color: #e0e0e0;
  border-radius: 2px;
  transition: background-color 0.4s ease;
  margin: 0 4px;
  margin-bottom: 18px;
}

.pipeline-connector.active {
  background-color: #1976d2;
}

.review-stream {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #eee;
}

/* 审查结果 */
.result-section {
  width: 100%;
  max-width: 960px;
}

.result-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  justify-content: center;
}

/* 错误提示 */
.error-section {
  width: 100%;
  max-width: 500px;
  margin-top: 80px;
}
</style>
