import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 对比候选池（spec 005 FR-70）：2~4 个户型，来源三处（列表多选 / 收藏 / 详情页）。
 * 放在 store 而不是组件里，是为了跨页保持（首页勾选 → 详情页追加 → 对比页）。
 */
export const useCompareStore = defineStore('compare', () => {
  const MAX = 4
  const ids = ref<number[]>((JSON.parse(localStorage.getItem('hf-compare') || '[]') as number[]))

  const count = computed(() => ids.value.length)
  const canCompare = computed(() => ids.value.length >= 2)          // AC-40：<2 时按钮禁用

  function persist() {
    localStorage.setItem('hf-compare', JSON.stringify(ids.value))
  }

  function add(id: number): { ok: boolean; reason?: string } {
    if (ids.value.includes(id)) return { ok: false, reason: '该户型已在候选池' }
    if (ids.value.length >= MAX) return { ok: false, reason: `最多对比 ${MAX} 个户型` }   // AC-41
    ids.value.push(id)
    persist()
    return { ok: true }
  }

  function remove(id: number) {
    ids.value = ids.value.filter((x) => x !== id)
    persist()
  }

  function clear() {
    ids.value = []
    persist()
  }

  return { ids, count, canCompare, add, remove, clear }
})
