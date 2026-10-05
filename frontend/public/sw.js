// 最小化 Service Worker：支持“添加到主屏幕”，页面请求网络优先、离线回退缓存壳
const CACHE = 'asm-shell-v1'
const SHELL = ['/', '/m/home', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png']

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) {
    return
  }
  // API 请求直连，不缓存（保证数据实时与登录态）
  if (req.url.includes('/api/')) {
    return
  }
  event.respondWith(
    fetch(req)
      .then((res) => {
        const copy = res.clone()
        caches.open(CACHE).then((c) => c.put(req, copy))
        return res
      })
      .catch(() => caches.match(req).then((hit) => hit || caches.match('/m/home'))),
  )
})
