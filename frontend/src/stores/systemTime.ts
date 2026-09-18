import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { systemTimeApi } from '@/shared/api'
import type { SystemTimeResponse } from '@/shared/api/types'
import { toDateStr } from '@/utils/datetime'

const SERVER_REFRESH_MS = 30_000

/**
 * One client-side projection of the server's business clock.  All operational
 * UI should consume this store instead of directly calling new Date()/Date.now().
 */
export const useSystemTimeStore = defineStore('systemTime', () => {
  const snapshot = ref<SystemTimeResponse | null>(null)
  const receivedAt = ref(0)
  const pulse = ref(0)
  const revision = ref(0)
  let pulseTimer: number | undefined
  let refreshTimer: number | undefined

  const isFixed = computed(() => snapshot.value?.mode === 'FIXED')
  const now = computed(() => {
    // Keep the real-time projection reactive once per second without making a
    // network request per second. A fixed clock deliberately remains frozen.
    pulse.value
    if (!snapshot.value) return new Date()
    const base = new Date(snapshot.value.currentTime).getTime()
    return new Date(isFixed.value ? base : base + Date.now() - receivedAt.value)
  })
  const date = computed(() => toDateStr(now.value))

  function apply(next: SystemTimeResponse, stateChanged: boolean) {
    snapshot.value = next
    receivedAt.value = Date.now()
    if (stateChanged) revision.value += 1
  }

  async function refresh() {
    const next = await systemTimeApi.current()
    apply(next, snapshot.value === null)
  }

  async function setFixed(currentTime: string) {
    const next = await systemTimeApi.set(currentTime)
    apply(next, true)
    return next
  }

  async function reset() {
    const next = await systemTimeApi.reset()
    apply(next, true)
    return next
  }

  function start() {
    if (pulseTimer !== undefined) return
    void refresh().catch(() => undefined)
    pulseTimer = window.setInterval(() => { pulse.value += 1 }, 1_000)
    refreshTimer = window.setInterval(() => void refresh().catch(() => undefined), SERVER_REFRESH_MS)
  }

  function stop() {
    if (pulseTimer !== undefined) window.clearInterval(pulseTimer)
    if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
    pulseTimer = undefined
    refreshTimer = undefined
  }

  return { snapshot, isFixed, now, date, revision, refresh, setFixed, reset, start, stop }
})
