import { defineStore } from 'pinia'
import { ref } from 'vue'

const PIN_KEY = 'app_sidebar_pinned'

function loadPinned(): boolean {
  return localStorage.getItem(PIN_KEY) !== '0'
}

/** 应用布局状态 */
export const useAppStore = defineStore('app', () => {
  /** 侧边栏固定显示；false = 自动隐藏（悬停左边缘临时展开） */
  const sidebarPinned = ref(loadPinned())
  /** 自动隐藏模式下，鼠标悬停临时展开（浮层） */
  const sidebarHovered = ref(false)

  /** 固定 ⇄ 自动隐藏（选择持久化） */
  function toggleSidebarPinned(): void {
    sidebarPinned.value = !sidebarPinned.value
    sidebarHovered.value = false
    localStorage.setItem(PIN_KEY, sidebarPinned.value ? '1' : '0')
  }

  function setSidebarHovered(value: boolean): void {
    sidebarHovered.value = value
  }

  return { sidebarPinned, sidebarHovered, toggleSidebarPinned, setSidebarHovered }
})
