export interface ThemeDef {
  key: string
  name: string
  primary: string
  sidebar: string
  pageBg: string
  dark: boolean
}

/** 内置主题目录（顺序即选择器中的展示顺序） */
export const THEMES: ThemeDef[] = [
  { key: 'default', name: '经典蓝', primary: '#2563eb', sidebar: '#1f2937', pageBg: '#f3f4f6', dark: false },
  { key: 'indigo', name: '靛青商务', primary: '#4f46e5', sidebar: '#1e1b4b', pageBg: '#f5f6ff', dark: false },
  { key: 'emerald', name: '墨绿典雅', primary: '#0d9488', sidebar: '#06342b', pageBg: '#f2f7f5', dark: false },
  { key: 'sunset', name: '暖阳橙', primary: '#ea580c', sidebar: '#4a2312', pageBg: '#faf6f2', dark: false },
  { key: 'rose', name: '绛红雅致', primary: '#be123c', sidebar: '#3f0d1c', pageBg: '#faf3f5', dark: false },
  { key: 'dark', name: '暗夜模式', primary: '#3b82f6', sidebar: '#0f172a', pageBg: '#0b1220', dark: true },
]

const THEME_KEY = 'app_theme'

/** 应用主题：设置 data-theme 与暗色类，并持久化 */
export function applyTheme(key: string): void {
  const theme = THEMES.find((t) => t.key === key) ?? THEMES[0]
  const root = document.documentElement
  root.dataset.theme = theme.key
  root.classList.toggle('dark', theme.dark)
  localStorage.setItem(THEME_KEY, theme.key)
}

/** 启动时恢复上次选择的主题 */
export function initTheme(): void {
  applyTheme(localStorage.getItem(THEME_KEY) || 'default')
}
