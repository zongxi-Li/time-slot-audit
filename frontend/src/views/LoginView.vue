<!--
  文件职责：实现 LoginView 页面，负责展示、交互和表单状态。
  接口：通过 Pinia store 或 shared/api 调用后端；管理员页面使用 /api/admin/*。
-->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/shared/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: 'zhangsan', password: '123456' })

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/board')
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '登录失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-orb login-orb--large" aria-hidden="true" />
    <div class="login-orb login-orb--small" aria-hidden="true" />

    <main class="login-shell">
      <section class="login-intro" aria-label="TimeSlot 产品介绍">
        <div class="intro-mark">智</div>
        <span class="intro-kicker">TIME / SLOT WORKSPACE</span>
        <h1>把时间留给真正重要的事。</h1>
        <p>清晰查看空闲时间，轻松找到适合每个人的会议室。</p>
        <div class="intro-rule" aria-hidden="true" />
        <span class="intro-footnote">Meet with clarity.</span>
      </section>

      <section class="login-card panel">
        <div class="login-card-head">
          <span class="card-kicker">WELCOME BACK</span>
          <h2>登录 TimeSlot</h2>
          <p>进入你的会议工作台</p>
        </div>

        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="login-button">
            登录工作台
          </el-button>
        </el-form>

        <div class="login-hint">
          <span>测试账号</span>
          <code>zhangsan / 123456</code>
          <span class="hint-separator">·</span>
          <span>管理员</span>
          <code>admin / 123456</code>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  display: grid;
  min-height: 100vh;
  place-items: center;
  overflow: hidden;
  padding: 32px;
  background:
    radial-gradient(circle at 16% 80%, rgba(0, 113, 227, 0.1), transparent 30%),
    #f5f5f7;
}

.login-orb {
  position: absolute;
  pointer-events: none;
  border: 1px solid rgba(0, 113, 227, 0.12);
  border-radius: 50%;
}

.login-orb--large {
  width: 580px;
  height: 580px;
  right: -180px;
  top: -230px;
  box-shadow: 0 0 0 44px rgba(0, 113, 227, 0.025), 0 0 0 90px rgba(0, 113, 227, 0.018);
}

.login-orb--small {
  width: 220px;
  height: 220px;
  bottom: -110px;
  left: 11%;
  background: rgba(255, 255, 255, 0.42);
}

.login-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(240px, 0.9fr) minmax(340px, 420px);
  align-items: center;
  gap: clamp(48px, 8vw, 132px);
  width: min(100%, 1000px);
}

.login-intro {
  padding: 24px 0;
}

.intro-mark {
  display: grid;
  width: 44px;
  height: 44px;
  margin-bottom: 34px;
  place-items: center;
  border-radius: 15px;
  color: #fff;
  background: linear-gradient(145deg, #1687f5, #0066cc);
  box-shadow: 0 10px 22px rgba(0, 113, 227, 0.22);
  font-size: 20px;
  font-weight: 700;
}

.intro-kicker,
.card-kicker {
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.login-intro h1 {
  max-width: 440px;
  margin: 18px 0 16px;
  font-size: clamp(38px, 5vw, 64px);
  font-weight: 700;
  letter-spacing: -0.07em;
  line-height: 1.05;
  text-wrap: pretty;
}

.login-intro p {
  max-width: 330px;
  margin: 0;
  color: var(--text-secondary);
  font-size: 16px;
  line-height: 26px;
  text-wrap: pretty;
}

.intro-rule {
  width: 64px;
  height: 1px;
  margin: 44px 0 16px;
  background: rgba(29, 29, 31, 0.18);
}

.intro-footnote {
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.login-card {
  padding: 34px;
  background: rgba(255, 255, 255, 0.78);
  border-color: rgba(255, 255, 255, 0.9);
  box-shadow: 0 30px 80px rgba(29, 29, 31, 0.1), 0 4px 18px rgba(29, 29, 31, 0.04);
}

.login-card-head {
  margin-bottom: 28px;
}

.login-card h2 {
  margin: 11px 0 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.045em;
}

.login-card-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.login-card :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-card :deep(.el-form-item__label) {
  padding-bottom: 8px;
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 600;
}

.login-button {
  width: 100%;
  height: 44px;
  margin-top: 8px;
  border-radius: 13px;
}

.login-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin-top: 24px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 18px;
}

.login-hint code {
  padding: 2px 6px;
  border-radius: 6px;
  color: var(--text-secondary);
  background: rgba(29, 29, 31, 0.055);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
}

.hint-separator {
  margin: 0 2px;
  color: rgba(29, 29, 31, 0.28);
}

@media (max-width: 760px) {
  .login-page {
    padding: 18px;
  }

  .login-shell {
    grid-template-columns: 1fr;
    gap: 22px;
    max-width: 430px;
  }

  .login-intro {
    padding: 12px 6px 0;
  }

  .intro-mark {
    width: 38px;
    height: 38px;
    margin-bottom: 22px;
    border-radius: 13px;
    font-size: 18px;
  }

  .login-intro h1 {
    margin: 13px 0 10px;
    font-size: 36px;
  }

  .login-intro p,
  .intro-rule,
  .intro-footnote {
    display: none;
  }

  .login-card {
    padding: 26px 22px;
  }
}
</style>
