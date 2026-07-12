import { defineStore } from "pinia";
import { ref, computed } from "vue";
import request from "../utils/axios";

/**
 * 用户认证 Store
 * 支持双 Token 机制：accessToken（短期）+ refreshToken（长期）
 */
export const useUserStore = defineStore("user", () => {
  const token = ref(localStorage.getItem("token") || "");
  const refreshToken = ref(localStorage.getItem("refreshToken") || "");
  const userId = ref(null);
  const userInfo = ref(null);

  /**
   * 从 JWT payload 解析数据（Base64URL 解码）
   */
  function parseTokenPayload(jwt) {
    try {
      const parts = jwt.split(".");
      if (parts.length !== 3) return null;
      let base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
      const padding = base64.length % 4;
      if (padding) {
        base64 += "=".repeat(4 - padding);
      }
      const payload = JSON.parse(atob(base64));
      return payload;
    } catch (e) {
      console.error("JWT payload 解析失败:", e);
      return null;
    }
  }

  /**
   * 登录：存储双 Token 并解析 userId
   * @param {Object} tokenData 后端返回的 { accessToken, refreshToken, expiresIn }
   */
  function login(tokenData) {
    // 兼容旧格式（直接传字符串 token）
    if (typeof tokenData === "string") {
      token.value = tokenData;
      localStorage.setItem("token", tokenData);
      const payload = parseTokenPayload(tokenData);
      if (payload && payload.sub) {
        userId.value = Number(payload.sub);
        localStorage.setItem("userId", String(userId.value));
      }
      return;
    }

    // 新格式：{ accessToken, refreshToken, expiresIn }
    token.value = tokenData.accessToken;
    refreshToken.value = tokenData.refreshToken;
    localStorage.setItem("token", tokenData.accessToken);
    localStorage.setItem("refreshToken", tokenData.refreshToken);

    const payload = parseTokenPayload(tokenData.accessToken);
    if (payload && payload.sub) {
      userId.value = Number(payload.sub);
      localStorage.setItem("userId", String(userId.value));
    }
  }

  /**
   * 更新 Token（刷新成功后调用）
   * @param {Object} tokenData { accessToken, refreshToken, expiresIn }
   */
  function updateTokens(tokenData) {
    token.value = tokenData.accessToken;
    refreshToken.value = tokenData.refreshToken;
    localStorage.setItem("token", tokenData.accessToken);
    localStorage.setItem("refreshToken", tokenData.refreshToken);
  }

  /**
   * 登出：先调用后端接口清除 Redis token，再清除本地状态
   */
  async function logout() {
    try {
      // 调用后端登出接口，清除 Redis 中的 token
      if (token.value) {
        await request.post("/user/logout");
      }
    } catch (error) {
      console.warn("登出接口调用失败，但仍会清除本地状态:", error);
    } finally {
      // 清除本地状态
      token.value = "";
      refreshToken.value = "";
      userId.value = null;
      userInfo.value = null;
      localStorage.removeItem("token");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("userId");
    }
  }

  /**
   * 从 localStorage 恢复状态（应用初始化时调用）
   * 优先检查 accessToken，过期时检查 refreshToken 是否仍有效
   */
  function restoreState() {
    const savedToken = localStorage.getItem("token");
    const savedRefreshToken = localStorage.getItem("refreshToken");

    if (!savedToken && !savedRefreshToken) return false;

    // 检查 accessToken 是否有效
    if (savedToken) {
      const payload = parseTokenPayload(savedToken);
      if (payload && payload.exp) {
        const now = Math.floor(Date.now() / 1000);
        if (payload.exp >= now) {
          // accessToken 仍有效
          token.value = savedToken;
          refreshToken.value = savedRefreshToken || "";
          userId.value = payload.sub ? Number(payload.sub) : null;
          return true;
        }
      }
    }

    // accessToken 已过期，检查 refreshToken 是否仍可用
    if (savedRefreshToken) {
      const refreshPayload = parseTokenPayload(savedRefreshToken);
      if (refreshPayload && refreshPayload.exp) {
        const now = Math.floor(Date.now() / 1000);
        if (refreshPayload.exp >= now) {
          // refreshToken 仍有效，保留状态等待自动刷新
          token.value = savedToken || "";
          refreshToken.value = savedRefreshToken;
          userId.value = refreshPayload.sub ? Number(refreshPayload.sub) : null;
          return true;
        }
      }
    }

    // 双 Token 均已过期
    logout();
    return false;
  }

  const isLoggedIn = computed(() => !!token.value || !!refreshToken.value);
  const getUserId = computed(() => {
    if (userId.value) return userId.value;
    const saved = localStorage.getItem("userId");
    if (saved) {
      userId.value = Number(saved);
      return userId.value;
    }
    if (token.value) {
      const payload = parseTokenPayload(token.value);
      if (payload && payload.sub) {
        userId.value = Number(payload.sub);
        return userId.value;
      }
    }
    return null;
  });

  return {
    token,
    refreshToken,
    userId,
    userInfo,
    isLoggedIn,
    getUserId,
    login,
    updateTokens,
    logout,
    restoreState,
    parseTokenPayload,
  };
});
