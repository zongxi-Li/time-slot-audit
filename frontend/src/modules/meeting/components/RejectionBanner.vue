<!--
  文件职责：登录后展示未读「预约被驳回」通知的顶部横幅提醒。
  接口：挂载于 MainLayout 顶栏下方；关闭横幅时将对应通知标记为已读。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Close, WarningFilled } from '@element-plus/icons-vue'
import { notificationsApi } from '../api'
import type { NotificationView } from '../api'

const visible = ref(false)
const items = ref<NotificationView[]>([])

const latest = computed(() => items.value[0] ?? null)
const extraCount = computed(() => Math.max(0, items.value.length - 1))

onMounted(async () => {
  try {
    const unread = await notificationsApi.list(true)
    items.value = unread.filter((n) => n.type === 'RESERVATION_REJECTED')
    visible.value = items.value.length > 0
  } catch {
    /* 加载失败静默：通知列表里仍可查看，不打扰用户 */
  }
})

/** 用户已通过横幅知晓结果：关闭时把这几条标记为已读，避免下次登录重复弹出 */
async function dismiss() {
  visible.value = false
  await Promise.allSettled(items.value.map((n) => notificationsApi.markRead(n.id)))
}
</script>

<template>
  <!-- 显式 duration：部分环境 transitionend 不触发，靠它保证横幅最终一定被移除 -->
  <Transition name="banner" :duration="{ enter: 220, leave: 200 }">
    <div v-if="visible && latest" class="reject-banner" role="alert">
      <el-icon class="banner-icon"><WarningFilled /></el-icon>
      <div class="banner-text">
        <strong>{{ latest.title }}</strong>
        <span>{{ latest.content }}</span>
        <span v-if="extraCount > 0" class="banner-extra">
          （另有 {{ extraCount }} 条驳回通知，见右上角铃铛）
        </span>
      </div>
      <button
        type="button"
        class="banner-close"
        aria-label="关闭驳回提醒"
        title="关闭驳回提醒"
        @click="dismiss"
      >
        <el-icon><Close /></el-icon>
      </button>
    </div>
  </Transition>
</template>

<style scoped>
.reject-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 20px;
  background: #fdeaea;
  border-bottom: 1px solid rgba(211, 88, 88, 0.45);
  color: #a61b1b;
}

.banner-icon {
  flex-shrink: 0;
  font-size: 18px;
  color: #d54941;
}

.banner-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 19px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.banner-text strong {
  margin-right: 6px;
  font-weight: 700;
}

.banner-extra {
  color: #c26060;
}

.banner-close {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  padding: 0;
  color: #a61b1b;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}

.banner-close:hover {
  background: rgba(213, 73, 65, 0.12);
}

.banner-enter-active,
.banner-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}

.banner-enter-from,
.banner-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
