<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { notificationsApi, formatDateTime } from '../api'
import type { NotificationView } from '../api'

const drawerVisible = ref(false)
const loading = ref(false)
const unread = ref(0)
const list = ref<NotificationView[]>([])
let timer: number | undefined

const typeText: Record<string, string> = {
  ATTENDEE_ADDED: '加入会议',
  ATTENDEE_REMOVED: '移出会议',
  MEETING_REMINDER: '开始提醒',
  NO_SHOW_MARKED: '缺席记录',
}

async function refreshUnread() {
  try {
    unread.value = await notificationsApi.unreadCount()
  } catch {
    /* 轮询失败静默，不打扰用户 */
  }
}

async function openDrawer() {
  drawerVisible.value = true
  await load()
}

async function load() {
  loading.value = true
  try {
    list.value = await notificationsApi.list()
    unread.value = list.value.filter((n) => !n.read).length
  } catch (error) {
    ElMessage.error((error as Error).message || '通知加载失败')
  } finally {
    loading.value = false
  }
}

async function markRead(item: NotificationView) {
  if (item.read) return
  try {
    await notificationsApi.markRead(item.id)
    item.read = true
    unread.value = Math.max(0, unread.value - 1)
  } catch (error) {
    ElMessage.error((error as Error).message || '操作失败')
  }
}

async function markAllRead() {
  try {
    await notificationsApi.markAllRead()
    list.value = list.value.map((n) => ({ ...n, read: true }))
    unread.value = 0
  } catch (error) {
    ElMessage.error((error as Error).message || '操作失败')
  }
}

onMounted(() => {
  void refreshUnread()
  timer = window.setInterval(() => void refreshUnread(), 60_000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})

defineExpose({ refreshUnread })
</script>

<template>
  <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-badge">
    <el-button :icon="Bell" circle text @click="openDrawer" />
  </el-badge>

  <el-drawer v-model="drawerVisible" title="个人通知" size="400px">
    <template #header>
      <div class="drawer-head">
        <span>个人通知</span>
        <el-button link type="primary" size="small" :disabled="unread === 0" @click="markAllRead">
          全部已读
        </el-button>
      </div>
    </template>

    <el-empty v-if="!loading && list.length === 0" description="暂无通知" />

    <div v-loading="loading" class="notice-list">
      <div
        v-for="item in list"
        :key="item.id"
        class="notice-item"
        :class="{ unread: !item.read }"
        @click="markRead(item)"
      >
        <div class="notice-top">
          <el-tag size="small" effect="light" :type="item.read ? 'info' : 'warning'">
            {{ typeText[item.type] ?? item.type }}
          </el-tag>
          <span class="notice-time">{{ formatDateTime(item.createdAt) }}</span>
        </div>
        <div class="notice-title">{{ item.title }}</div>
        <div class="notice-content">{{ item.content }}</div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.bell-badge {
  margin-right: 12px;
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
}

.notice-item {
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
}

.notice-item.unread {
  background: var(--el-color-primary-light-9);
}

.notice-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.notice-time {
  font-size: 12px;
  color: var(--text-muted);
}

.notice-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}

.notice-content {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 18px;
}
</style>
