<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo">
          <el-icon size="48" color="#1976d2"><ScaleToOriginal /></el-icon>
        </div>
        <h1>LawMind</h1>
        <p>智能法律咨询助手</p>
      </div>

      <el-form
        :model="loginForm"
        :rules="loginRules"
        ref="loginFormRef"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleLogin"
            class="login-btn"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <router-link to="/register" class="register-link">立即注册</router-link>
      </div>
    </div>

    <div class="login-bg">
      <div class="bg-pattern"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { User, Lock, ScaleToOriginal } from "@element-plus/icons-vue";
import request from "../utils/axios";
import { useUserStore } from "../stores/user";

const router = useRouter();
const userStore = useUserStore();
const loginFormRef = ref(null);
const loading = ref(false);

const loginForm = reactive({
  username: "",
  password: "",
});

const loginRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    {
      min: 3,
      max: 20,
      message: "用户名长度应在3-20个字符之间",
      trigger: "blur",
    },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度应在6-20个字符之间", trigger: "blur" },
  ],
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  try {
    await loginFormRef.value.validate();
    loading.value = true;

    const res = await request.post("/user/login", loginForm);

    if (res.code === 200) {
      // 使用 store 存储 token 并解析 userId
      userStore.login(res.data);

      ElMessage.success("登录成功");
      router.push("/home/consultation");
    } else {
      ElMessage.error(res.message || "登录失败");
    }
  } catch (error) {
    console.error("登录失败:", error);
    ElMessage.error(error.message || "登录失败，请检查网络连接");
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-container {
  width: 100%;
  height: 100%;
  display: flex;
  background: linear-gradient(135deg, #1976d2 0%, #0d47a1 100%);
  position: relative;
  overflow: hidden;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.login-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  z-index: 0;
}

.bg-pattern {
  position: absolute;
  top: -50%;
  left: -50%;
  right: -50%;
  bottom: -50%;
  background-image:
    radial-gradient(
      circle at 20% 80%,
      rgba(255, 255, 255, 0.15) 0%,
      transparent 50%
    ),
    radial-gradient(
      circle at 80% 20%,
      rgba(255, 255, 255, 0.15) 0%,
      transparent 50%
    ),
    radial-gradient(
      circle at 40% 40%,
      rgba(255, 255, 255, 0.1) 0%,
      transparent 50%
    );
  animation: rotate 25s linear infinite;
}

@keyframes rotate {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.login-box {
  width: 440px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 56px 48px;
  margin: auto;
  box-shadow: 0 24px 72px rgba(0, 0, 0, 0.35);
  position: relative;
  z-index: 1;
  transition: all 0.3s ease;
}

.login-box:hover {
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.4);
  transform: translateY(-4px);
}

.login-header {
  text-align: center;
  margin-bottom: 48px;
}

.logo {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  border-radius: 50%;
  margin-left: auto;
  margin-right: auto;
  box-shadow: 0 8px 24px rgba(25, 118, 210, 0.4);
  transition: all 0.3s ease;
}

.logo:hover {
  transform: scale(1.05);
  box-shadow: 0 10px 28px rgba(25, 118, 210, 0.5);
}

.login-header h1 {
  font-size: 36px;
  font-weight: 700;
  color: #1976d2;
  margin: 0 0 12px 0;
  letter-spacing: 1px;
}

.login-header p {
  font-size: 16px;
  color: #666;
  margin: 0;
  line-height: 1.5;
}

.login-form {
  margin-bottom: 32px;
}

:deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(25, 118, 210, 0.2);
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 6px 20px rgba(25, 118, 210, 0.15);
  border-color: rgba(25, 118, 210, 0.4);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 3px rgba(25, 118, 210, 0.1);
  border-color: #1976d2;
}

:deep(.el-input__inner) {
  height: 52px;
  font-size: 16px;
  padding: 0 20px;
}

:deep(.el-input__prefix) {
  margin-left: 12px;
}

:deep(.el-input__prefix-inner) {
  color: #1976d2;
  font-size: 18px;
}

.login-btn {
  width: 100%;
  height: 52px;
  font-size: 18px;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
  border: none;
  box-shadow: 0 6px 20px rgba(25, 118, 210, 0.4);
  transition: all 0.3s ease;
  color: white;
  letter-spacing: 1px;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(25, 118, 210, 0.5);
  background: linear-gradient(135deg, #1565c0 0%, #0d47a1 100%);
}

.login-btn:active:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(25, 118, 210, 0.4);
}

.login-btn:disabled {
  background: #e0e0e0;
  box-shadow: none;
  cursor: not-allowed;
}

.login-footer {
  text-align: center;
  font-size: 15px;
  color: #666;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

.register-link {
  color: #1976d2;
  text-decoration: none;
  font-weight: 600;
  margin-left: 6px;
  transition: all 0.3s ease;
  position: relative;
  padding-bottom: 2px;
}

.register-link::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 0;
  width: 0;
  height: 2px;
  background-color: #1976d2;
  transition: width 0.3s ease;
}

.register-link:hover {
  color: #1565c0;
}

.register-link:hover::after {
  width: 100%;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-box {
    width: 90%;
    max-width: 400px;
    padding: 48px 36px;
  }

  .login-header h1 {
    font-size: 32px;
  }

  :deep(.el-input__inner) {
    height: 48px;
  }

  .login-btn {
    height: 48px;
  }
}
</style>
