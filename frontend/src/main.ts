import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import permissionDirective from './directives/permission'
import './styles/index.scss'
import './styles/themes.css'
import { initTheme } from './styles/theme'

const app = createApp(App)

// 恢复用户选择的主题（在挂载前应用，避免闪烁）
initTheme()

// 状态管理
app.use(createPinia())

// 路由
app.use(router)

// Element Plus（中文）
app.use(ElementPlus, { locale: zhCn })

// 全局注册 Element Plus 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 按钮级权限指令
app.directive('permission', permissionDirective)

app.mount('#app')

// PWA：生产环境注册 Service Worker（添加到主屏幕 + 离线壳）
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* 注册失败不影响使用 */
    })
  })
}
