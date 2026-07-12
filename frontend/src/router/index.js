import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "../stores/user";

const routes = [
  {
    path: "/",
    redirect: "/login",
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("../views/Login.vue"),
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("../views/Register.vue"),
  },
  {
    path: "/home/consultation",
    name: "Consultation",
    component: () => import("../components/Layout.vue"),
  },
  {
    path: "/home/knowledge",
    name: "Knowledge",
    component: () => import("../components/Layout.vue"),
  },
  {
    path: "/home/upload",
    name: "Upload",
    component: () => import("../components/Layout.vue"),
  },
  {
    path: "/home/quality",
    name: "QualityDashboard",
    component: () => import("../components/Layout.vue"),
  },
  {
    path: "/home/profile",
    name: "Profile",
    component: () => import("../components/Layout.vue"),
  },
  {
    path: "/home/agent-metrics",
    name: "AgentMetrics",
    component: () => import("../components/Layout.vue"),
  },
  {
    path: "/home/contract-review",
    name: "ContractReview",
    component: () => import("../components/Layout.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore();

  if (to.path === "/login" || to.path === "/register") {
    next();
  } else {
    // 尝试从 localStorage 恢复 Pinia 状态
    if (!userStore.isLoggedIn) {
      const restored = userStore.restoreState();
      if (!restored) {
        next("/login");
        return;
      }
    }
    next();
  }
});

export default router;
