// 文件职责：提供「页面可见时周期轮询 + 切回页面立即刷新」的轻量自动刷新能力。
// 接口：useAutoRefresh(task, intervalMs)，在 setup 中调用，组件卸载时自动清理。
import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 适合接「静默」的数据重拉函数（不触发 loading 遮罩）。
 * - 组件挂载后启动周期轮询，卸载时清理；
 * - 页面不可见（document.hidden）时跳过本轮请求，不给后端打无效流量；
 * - 从其他标签页切回时立即刷新一次，避免看到陈旧数据。
 */
export function useAutoRefresh(task: () => unknown | Promise<unknown>, intervalMs: number) {
  let timer: number | null = null
  let running = false

  async function run() {
    // 上一轮还没返回就不叠加；页面在后台时不打接口
    if (running || document.hidden) return
    running = true
    try {
      await task()
    } catch {
      // 后台轮询失败保持安静：页面首次加载路径已有各自的错误提示
    } finally {
      running = false
    }
  }

  function onVisibilityChange() {
    if (!document.hidden) void run()
  }

  onMounted(() => {
    timer = window.setInterval(() => void run(), intervalMs)
    document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onBeforeUnmount(() => {
    if (timer !== null) window.clearInterval(timer)
    timer = null
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })
}
