const TOKEN_KEY = 'accounting_firm_token'

/** 读取 Token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 保存 Token */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 移除 Token */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
