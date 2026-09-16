<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import BrandLogo from '@/components/BrandLogo.vue'
import LoginMapScene from '@/components/LoginMapScene.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const remember = ref(true)
const form = reactive({ username: '', password: '' })

async function submit() {
  if (submitting.value) return
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  submitting.value = true
  try {
    await authStore.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } finally {
    submitting.value = false
  }
}

function fillDemo(username, password) {
  form.username = username
  form.password = password
}
</script>

<template>
  <div class="login-page">
    <LoginMapScene />

    <div class="login-panel">
      <a class="access-brand" href="/login" aria-label="Weather Data Hub 登录页">
        <BrandLogo :size="48" with-wordmark tone="light" />
      </a>

      <p class="top-motto">用数据，感知更大的世界</p>

      <div class="access-stack">
        <section class="access-card" aria-labelledby="login-title">
          <div class="card-head">
            <p class="panel-kicker">WEATHER DATA HUB</p>
            <h1 id="login-title">登录工作台</h1>
            <p class="form-lead">让数据连接更多可能</p>
            <div class="status-row">
              <span class="status-pill">
                <i class="live-dot" aria-hidden="true" />
                系统在线
              </span>
              <span class="status-pill">加密通道</span>
              <span class="status-pill">权限就绪</span>
            </div>
          </div>

          <el-form class="access-form" @submit.prevent="submit">
            <el-input
              id="login-username"
              v-model="form.username"
              :prefix-icon="User"
              maxlength="64"
              autocomplete="username"
              placeholder="请输入账号"
              size="large"
            />
            <el-input
              id="login-password"
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              maxlength="64"
              autocomplete="current-password"
              placeholder="请输入密码"
              size="large"
            />
            <div class="form-extra">
              <label class="remember">
                <input v-model="remember" type="checkbox" />
                记住本次登录
              </label>
              <span class="secure-hint">
                <el-icon><Lock /></el-icon>
                安全登录
              </span>
            </div>
            <el-button class="access-submit" :loading="submitting" native-type="submit">
              {{ submitting ? '正在登录' : '进入工作台' }}
              <el-icon v-if="!submitting"><ArrowRight /></el-icon>
            </el-button>
          </el-form>

          <div class="demo-row">
            <button type="button" @click="fillDemo('admin', 'admin123')">
              <el-icon><User /></el-icon>
              管理员演示
            </button>
            <button type="button" @click="fillDemo('user', 'user123')">
              <el-icon><User /></el-icon>
              普通用户演示
            </button>
          </div>
        </section>

        <p class="foot-note">气象 · 地理 · 海洋 · 灾害 · 让世界更安全</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  --mint: #58dfff;
  --ui: 1;
  --ui: min(calc(100vw / 1920px), calc(100vh / 1080px));
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  color: #e8f6f3;
}

.login-panel {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
}

.access-brand {
  position: absolute;
  top: calc(0px * var(--ui));
  left: calc(0px * var(--ui));
  z-index: 2;
  text-decoration: none;
  color: inherit;
}

.access-brand :deep(.mark) {
  width: calc(48px * var(--ui)) !important;
  height: calc(48px * var(--ui)) !important;
}

.access-brand :deep(.wordmark) {
  gap: calc(6px * var(--ui));
}

.access-brand :deep(.wordmark strong) {
  font-size: calc(22px * var(--ui));
  letter-spacing: 0.08em;
}

.access-brand :deep(.wordmark small) {
  font-size: calc(12px * var(--ui));
  letter-spacing: 0.22em;
}

.access-brand :deep(.wordmark small)::before {
  content: '— ';
}

.access-brand :deep(.wordmark small)::after {
  content: ' —';
}

.top-motto {
  position: absolute;
  top: calc(46px * var(--ui));
  right: calc(64px * var(--ui));
  z-index: 2;
  margin: 0;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: calc(14px * var(--ui));
  font-weight: 300;
  letter-spacing: 0.22em;
  color: rgba(214, 236, 232, 0.86);
  white-space: nowrap;
  line-height: 1;
}

.top-motto::before {
  content: '';
  display: inline-block;
  width: calc(18px * var(--ui));
  height: 1px;
  margin-right: calc(10px * var(--ui));
  vertical-align: middle;
  background: rgba(127, 232, 203, 0.85);
}

.access-stack {
  position: absolute;
  top: 50%;
  right: calc(56px * var(--ui));
  width: calc(560px * var(--ui));
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.access-card {
  width: 100%;
  min-height: max(400px, calc(580px * var(--ui)));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: max(16px, calc(18px * var(--ui)));
  padding: max(28px, calc(40px * var(--ui))) calc(36px * var(--ui)) max(22px, calc(28px * var(--ui)));
  border-radius: calc(32px * var(--ui));
  background: linear-gradient(145deg, rgba(11, 28, 49, 0.72), rgba(9, 17, 34, 0.58));
  border: 1px solid rgba(104, 210, 255, 0.26);
  box-shadow: 0 24px 60px rgba(2, 10, 20, 0.28);
  backdrop-filter: blur(22px);
}

.foot-note {
  margin: calc(18px * var(--ui)) 0 0;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: calc(12px * var(--ui));
  font-weight: 300;
  letter-spacing: 0.28em;
  color: rgba(186, 214, 208, 0.52);
  text-align: center;
  white-space: nowrap;
}

.panel-kicker {
  margin: 0 0 calc(10px * var(--ui));
  font-family: Orbitron, sans-serif;
  font-size: calc(11px * var(--ui));
  letter-spacing: 0.28em;
  color: #85e7ff;
}

.access-card h1 {
  margin: 0;
  font-family: 'Noto Sans SC', 'PingFang SC', sans-serif;
  font-size: calc(40px * var(--ui));
  font-weight: 500;
  letter-spacing: 0.12em;
  color: #f4fffb;
}

.form-lead {
  margin: calc(8px * var(--ui)) 0 0;
  font-family: 'Noto Sans SC', sans-serif;
  font-size: max(12px, calc(15px * var(--ui)));
  letter-spacing: 0.08em;
  color: rgba(196, 224, 218, 0.78);
}

.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: max(12px, calc(16px * var(--ui)));
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(61, 148, 206, 0.09);
  border: 1px solid rgba(104, 210, 255, 0.2);
  font-size: 11px;
  letter-spacing: 0.08em;
  color: rgba(211, 229, 245, 0.88);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #73f4c7;
  box-shadow: 0 0 8px rgba(94, 241, 212, 0.9);
}

.access-form {
  display: grid;
  gap: calc(14px * var(--ui));
}

.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 24px;
}

.remember {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: max(12px, calc(13px * var(--ui)));
  color: rgba(202, 221, 239, 0.88);
  cursor: pointer;
  user-select: none;
}

.remember input {
  width: 14px;
  height: 14px;
  accent-color: #58dfff;
  cursor: pointer;
}

.secure-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: rgba(122, 223, 255, 0.9);
}

.access-form :deep(.el-input__wrapper) {
  padding: calc(6px * var(--ui)) calc(18px * var(--ui));
  min-height: max(48px, calc(56px * var(--ui)));
  border-radius: 999px;
  background: rgba(8, 20, 37, 0.76);
  box-shadow: 0 0 0 1px rgba(117, 197, 243, 0.25) inset;
}

.access-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(79, 219, 255, 0.82) inset, 0 0 18px rgba(69, 191, 255, 0.14);
}

.access-form :deep(.el-input__inner) {
  color: #f2fffb;
}

.access-form :deep(.el-input__inner::placeholder) {
  color: rgba(174, 195, 218, 0.65);
}

.access-form :deep(.el-input__prefix),
.access-form :deep(.el-input__suffix) {
  color: rgba(151, 190, 220, 0.82);
}

.access-submit.el-button {
  display: flex;
  width: 100%;
  height: max(48px, calc(56px * var(--ui)));
  margin-top: calc(12px * var(--ui));
  border: none;
  border-radius: 999px;
  background: linear-gradient(100deg, #21b6e5, #6784ff);
  color: #041b30;
  box-shadow: 0 0 26px rgba(52, 190, 255, 0.25);
  font-family: 'Noto Sans SC', sans-serif;
  font-size: calc(16px * var(--ui));
  font-weight: 600;
  letter-spacing: 0.12em;
}

.access-submit.el-button:hover {
  background: linear-gradient(100deg, #43cff4, #8b9aff);
}

.access-submit :deep(span) {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: calc(8px * var(--ui));
}

.demo-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  gap: 8px;
}

.demo-row button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 12px;
  border: 1px solid rgba(104, 210, 255, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  color: #d7efea;
  cursor: pointer;
  font-size: max(12px, calc(13px * var(--ui)));
}

.demo-row button:hover {
  background: rgba(255, 255, 255, 0.1);
}

@media (max-width: 900px) {
  .login-page {
    --ui: min(calc(100vw / 900px), calc(100vh / 700px));
  }

  .top-motto,
  .foot-note {
    white-space: normal;
    max-width: 90%;
  }

  .access-stack {
    right: 16px;
    width: min(560px, calc(100vw - 32px));
  }
}
</style>
