<template>
  <div
    class="layout-container"
    :class="{ 'chat-fullscreen': isChatFullscreen }"
  >
    <!-- 侧边栏 -->
    <aside
      class="sidebar"
      :class="{ collapsed: isSidebarCollapsed }"
      v-show="!isChatFullscreen"
    >
      <div class="logo">
        <h2 v-show="!isSidebarCollapsed">LawMind</h2>
        <p v-show="!isSidebarCollapsed">智能法律咨询助手</p>
        <h2 v-show="isSidebarCollapsed" class="logo-short">LM</h2>
      </div>
      <nav class="nav-menu">
        <router-link
          to="/home/consultation"
          class="nav-item"
          :title="isSidebarCollapsed ? '智能咨询' : ''"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span v-show="!isSidebarCollapsed">智能咨询</span>
        </router-link>
        <router-link
          to="/home/knowledge"
          class="nav-item"
          :title="isSidebarCollapsed ? '法律知识库' : ''"
        >
          <el-icon><Document /></el-icon>
          <span v-show="!isSidebarCollapsed">法律知识库</span>
        </router-link>
        <router-link
          to="/home/upload"
          class="nav-item"
          :title="isSidebarCollapsed ? '文件上传' : ''"
        >
          <el-icon><Upload /></el-icon>
          <span v-show="!isSidebarCollapsed">文件上传</span>
        </router-link>
        <router-link
          to="/home/quality"
          class="nav-item"
          :title="isSidebarCollapsed ? '质量看板' : ''"
        >
          <el-icon><DataAnalysis /></el-icon>
          <span v-show="!isSidebarCollapsed">质量看板</span>
        </router-link>
        <router-link
          to="/home/agent-metrics"
          class="nav-item"
          :title="isSidebarCollapsed ? 'Agent监控' : ''"
        >
          <el-icon><TrendCharts /></el-icon>
          <span v-show="!isSidebarCollapsed">Agent监控</span>
        </router-link>
        <router-link
          to="/home/contract-review"
          class="nav-item"
          :title="isSidebarCollapsed ? '合同审查' : ''"
        >
          <el-icon><Checked /></el-icon>
          <span v-show="!isSidebarCollapsed">合同审查</span>
        </router-link>
        <router-link
          to="/home/profile"
          class="nav-item"
          :title="isSidebarCollapsed ? '个人中心' : ''"
        >
          <el-icon><User /></el-icon>
          <span v-show="!isSidebarCollapsed">个人中心</span>
        </router-link>
      </nav>
      <div class="sidebar-toggle" @click="toggleSidebar">
        <el-icon size="18"
          ><DArrowLeft v-if="!isSidebarCollapsed" /><DArrowRight v-else
        /></el-icon>
      </div>
      <div class="logout">
        <el-button type="primary" @click="logout">
          <span v-show="!isSidebarCollapsed">退出登录</span>
          <el-icon v-show="isSidebarCollapsed"><SwitchButton /></el-icon>
        </el-button>
      </div>
    </aside>

    <!-- 主内容区域 -->
    <main class="main-content">
      <header class="header" v-show="!isChatFullscreen">
        <div class="header-left">
          <el-icon
            v-if="isSidebarCollapsed"
            class="sidebar-expand-btn"
            size="20"
            @click="toggleSidebar"
            ><Expand
          /></el-icon>
          <h1>{{ currentPageTitle }}</h1>
        </div>
      </header>
      <div class="content" :class="{ 'content-fullscreen': isChatFullscreen }">
        <transition name="fade" mode="out-in">
          <component
            :is="currentComponent"
            v-if="currentComponent"
            @toggle-fullscreen="toggleChatFullscreen"
            :is-fullscreen="isChatFullscreen"
          />
          <div v-else>页面加载中...</div>
        </transition>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, ref, defineAsyncComponent } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ChatDotRound,
  Document,
  Upload,
  User,
  DArrowLeft,
  DArrowRight,
  SwitchButton,
  Expand,
  DataAnalysis,
  TrendCharts,
  Checked,
} from "@element-plus/icons-vue";
import { useUserStore } from "../stores/user";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const isSidebarCollapsed = ref(false);
const isChatFullscreen = ref(false);

const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
};

const toggleChatFullscreen = (val) => {
  isChatFullscreen.value =
    typeof val === "boolean" ? val : !isChatFullscreen.value;
};

// 根据当前路由计算页面标题
const currentPageTitle = computed(() => {
  const path = route.path;
  if (path.includes("consultation")) return "智能法律咨询";
  if (path.includes("knowledge")) return "法律知识库";
  if (path.includes("upload")) return "文件上传";
  if (path.includes("quality")) return "质量看板";
  if (path.includes("profile")) return "个人中心";
  if (path.includes("agent-metrics")) return "Agent监控";
  if (path.includes("contract-review")) return "合同审查";
  return "LawMind";
});

// 根据当前路由加载对应的组件
const currentComponent = computed(() => {
  const path = route.path;
  if (path.includes("consultation"))
    return defineAsyncComponent(() => import("../views/Consultation.vue"));
  if (path.includes("knowledge"))
    return defineAsyncComponent(() => import("../views/Knowledge.vue"));
  if (path.includes("upload"))
    return defineAsyncComponent(() => import("../views/Upload.vue"));
  if (path.includes("quality"))
    return defineAsyncComponent(() => import("../views/QualityDashboard.vue"));
  if (path.includes("profile"))
    return defineAsyncComponent(() => import("../views/Profile.vue"));
  if (path.includes("agent-metrics"))
    return defineAsyncComponent(() => import("../views/AgentMetrics.vue"));
  if (path.includes("contract-review"))
    return defineAsyncComponent(() => import("../views/ContractReview.vue"));
  return null;
});

// 退出登录
const logout = () => {
  userStore.logout();
  router.push("/login");
};
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100%;
  width: 100%;
  background-color: #f0f2f5;
  overflow: hidden;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 全屏模式 */
.layout-container.chat-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
}

.sidebar {
  width: 260px;
  flex-shrink: 0;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  color: white;
  padding: 24px;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.1);
  transition:
    width 0.3s ease,
    padding 0.3s ease;
  position: relative;
}

.sidebar.collapsed {
  width: 72px;
  padding: 24px 12px;
}

.sidebar:hover {
  box-shadow: 2px 0 16px rgba(0, 0, 0, 0.15);
}

.logo {
  text-align: center;
  margin-bottom: 48px;
  padding: 20px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo h2 {
  margin: 0 0 12px 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 1px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.logo .logo-short {
  margin: 0;
  font-size: 22px;
}

.logo p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
  line-height: 1.4;
}

.nav-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  border-radius: 12px;
  color: white;
  text-decoration: none;
  transition: all 0.3s ease;
  font-size: 15px;
  position: relative;
  overflow: hidden;
  white-space: nowrap;
}

.sidebar.collapsed .nav-item {
  padding: 14px;
  justify-content: center;
}

.nav-item::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  background-color: rgba(255, 255, 255, 0.3);
  transform: scaleY(0);
  transition: transform 0.3s ease;
  border-radius: 0 2px 2px 0;
}

.nav-item:hover {
  background-color: rgba(255, 255, 255, 0.15);
  transform: translateX(4px);
}

.sidebar.collapsed .nav-item:hover {
  transform: none;
}

.nav-item:hover::before {
  transform: scaleY(1);
}

.nav-item.router-link-active {
  background-color: rgba(255, 255, 255, 0.25);
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.nav-item.router-link-active::before {
  transform: scaleY(1);
  background-color: white;
}

.nav-item .el-icon {
  font-size: 20px;
  transition: transform 0.3s ease;
  flex-shrink: 0;
}

.nav-item:hover .el-icon {
  transform: scale(1.1);
}

/* 侧边栏折叠按钮 */
.sidebar-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  margin-bottom: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: rgba(255, 255, 255, 0.7);
}

.sidebar-toggle:hover {
  background-color: rgba(255, 255, 255, 0.15);
  color: white;
}

.logout {
  margin-top: auto;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.logout .el-button {
  width: 100%;
  border-radius: 12px;
  padding: 12px;
  font-size: 15px;
  font-weight: 500;
  background-color: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  transition: all 0.3s ease;
}

.sidebar.collapsed .logout .el-button {
  padding: 12px 8px;
}

.logout .el-button:hover {
  background-color: rgba(255, 255, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f0f2f5;
  min-width: 0;
}

.header {
  background: linear-gradient(90deg, #ffffff 0%, #f8f9fa 100%);
  padding: 0 40px;
  height: 72px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sidebar-expand-btn {
  cursor: pointer;
  color: #666;
  transition: all 0.3s ease;
  padding: 6px;
  border-radius: 6px;
}

.sidebar-expand-btn:hover {
  color: #1976d2;
  background-color: rgba(25, 118, 210, 0.08);
}

.header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #333;
  letter-spacing: 0.5px;
}

.content {
  flex: 1;
  padding: 0;
  overflow-y: auto;
  min-height: 0;
  background-color: #f0f2f5;
}

.content-fullscreen {
  overflow: hidden;
}

/* 滚动条样式 */
.content::-webkit-scrollbar {
  width: 8px;
}

.content::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.content::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.content::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
