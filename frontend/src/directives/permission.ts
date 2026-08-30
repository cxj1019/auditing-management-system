import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission 按钮级权限指令
 *
 * 用法：v-permission="'system:user:add'"
 * 当前用户不具备该权限标识时，元素从 DOM 中移除。
 */
const permission: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const perm = binding.value
    if (perm && !userStore.hasPermission(perm)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export default permission
