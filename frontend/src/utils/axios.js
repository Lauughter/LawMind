import axios from "axios";

// 创建 axios 实例
const service = axios.create({
  baseURL: "/api",
  timeout: 50000,
  headers: {
    "Content-Type": "application/json",
  },
});

// ========== Token 刷新状态管理 ==========
// 是否正在刷新 Token
let isRefreshing = false;
// 等待刷新完成的请求队列
let refreshQueue = [];

/**
 * 将等待中的请求加入队列，刷新完成后统一重试
 * @param {Function} resolve
 * @param {Function} reject
 */
function addToRefreshQueue(resolve, reject) {
  refreshQueue.push({ resolve, reject });
}

/**
 * 刷新完成后，处理队列中所有等待的请求
 * @param {string|null} newToken 新的 accessToken，null 表示刷新失败
 */
function processRefreshQueue(newToken) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (newToken) {
      resolve(newToken);
    } else {
      reject(new Error("Token 刷新失败"));
    }
  });
  refreshQueue = [];
}

// 请求拦截器：自动附加 accessToken
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    console.error("请求错误:", error);
    return Promise.reject(error);
  },
);

// 响应拦截器：处理 401 自动刷新
service.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res.code !== 200) {
      console.error("响应错误:", res.message);
      return Promise.reject(new Error(res.message || "Error"));
    }
    return res;
  },
  async (error) => {
    const originalRequest = error.config;

    // 处理 401 未授权错误 — 尝试自动刷新 Token
    if (error.response && error.response.status === 401 && !originalRequest._retried) {
      // 刷新接口本身返回 401，说明 refreshToken 也已失效
      if (originalRequest.url === "/user/refresh-token") {
        forceLogout();
        return Promise.reject(error);
      }

      // 标记为已重试，防止无限循环
      originalRequest._retried = true;

      const refreshTokenValue = localStorage.getItem("refreshToken");
      if (!refreshTokenValue) {
        forceLogout();
        return Promise.reject(error);
      }

      // 如果当前正在刷新中，将请求加入队列等待
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          addToRefreshQueue(resolve, reject);
        }).then((newToken) => {
          originalRequest.headers.Authorization = `Bearer ${newToken}`;
          return service(originalRequest);
        });
      }

      // 开始刷新 Token
      isRefreshing = true;

      try {
        // 直接使用 axios（不经过 service 拦截器）调用刷新接口
        const refreshResponse = await axios.post("/api/user/refresh-token", {
          refreshToken: refreshTokenValue,
        });

        const refreshData = refreshResponse.data;
        if (refreshData.code === 200 && refreshData.data) {
          const { accessToken, refreshToken: newRefreshToken } = refreshData.data;

          // 更新 localStorage
          localStorage.setItem("token", accessToken);
          localStorage.setItem("refreshToken", newRefreshToken);

          // 更新 Pinia store（延迟导入避免循环依赖）
          import("../stores/user").then(({ useUserStore }) => {
            const userStore = useUserStore();
            userStore.updateTokens(refreshData.data);
          });

          // 通知队列中等待的请求
          processRefreshQueue(accessToken);

          // 用新 Token 重试原始请求
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          return service(originalRequest);
        } else {
          // 刷新失败（业务层面）
          processRefreshQueue(null);
          forceLogout();
          return Promise.reject(new Error("Token 刷新失败"));
        }
      } catch (refreshError) {
        // 刷新请求网络异常
        processRefreshQueue(null);
        forceLogout();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // 处理网络错误
    if (!error.response) {
      console.error("网络错误: 无法连接到服务器");
      return Promise.reject(
        new Error("网络错误: 无法连接到服务器，请检查后端服务是否正在运行"),
      );
    }

    return Promise.reject(error);
  },
);

/**
 * 强制登出：清除本地状态并跳转登录页
 */
function forceLogout() {
  import("../stores/user").then(({ useUserStore }) => {
    const userStore = useUserStore();
    userStore.logout();
    window.location.href = "/login";
  });
}

export { service };

// 导出请求方法
export default {
  get(url, params = {}) {
    return service({ url, method: "get", params });
  },

  post(url, data = {}) {
    return service({ url, method: "post", data });
  },

  put(url, data = {}) {
    return service({ url, method: "put", data });
  },

  delete(url, params = {}) {
    return service({ url, method: "delete", params });
  },

  upload(url, formData) {
    return service({
      url,
      method: "post",
      data: formData,
      headers: { "Content-Type": "multipart/form-data" },
    });
  },
};
