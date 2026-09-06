/**
 * 列表筛选条件记忆：按页面键名存取 localStorage
 * 用法：
 *   const query = reactive({ ... })
 *   restoreQuery('contract', query)          // 进入页面时恢复
 *   watch(query, () => saveQuery('contract', query), { deep: true })
 */
export function restoreQuery<T extends object>(key: string, target: T): void {
  try {
    const raw = localStorage.getItem(`query:${key}`)
    if (raw) {
      Object.assign(target, JSON.parse(raw))
    }
  } catch {
    // 忽略损坏的缓存
  }
}

export function saveQuery(key: string, query: unknown): void {
  try {
    localStorage.setItem(`query:${key}`, JSON.stringify(query))
  } catch {
    // 存储满等异常忽略
  }
}

export function clearQuery(key: string): void {
  localStorage.removeItem(`query:${key}`)
}
