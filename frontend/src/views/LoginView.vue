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
    <div class="login-card panel">
      <div class="brand-logo">智</div>
      <h1>智会会议室预约系统</h1>
      <p>TimeSlot Team-Ready Baseline v0.2</p>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="login-button">登录</el-button>
      </el-form>
      <div class="login-hint">测试账号：zhangsan / 123456；管理员：admin / 123456</div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; background: #f4f7fb; }
.login-card { width: min(420px, calc(100vw - 32px)); padding: 32px; text-align: center; }
.brand-logo { width: 44px; height: 44px; margin: 0 auto 12px; border-radius: 12px; display: grid; place-items: center; background: #2563eb; color: white; font-size: 22px; font-weight: 700; }
h1 { margin: 0; font-size: 22px; }
p { margin: 8px 0 24px; color: #667085; font-size: 13px; }
.login-button { width: 100%; }
.login-hint { margin-top: 18px; color: #98a2b3; font-size: 12px; line-height: 20px; }
</style>
