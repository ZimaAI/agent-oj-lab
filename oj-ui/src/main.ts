import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useToast } from './composables/useToast'

// Browser compatibility check
function checkBrowserCompatibility() {
  const ua = navigator.userAgent
  const isIE = ua.indexOf('MSIE') > -1 || ua.indexOf('Trident/') > -1
  const isOldEdge = ua.indexOf('Edge/') > -1 && ua.indexOf('Edg/') === -1

  if (isIE || isOldEdge) {
    const message = '您的浏览器版本过低，可能无法正常使用本网站。建议使用 Chrome、Firefox、Safari 或新版 Edge 浏览器。'
    alert(message)
    return false
  }

  // Check for required features
  const hasRequiredFeatures =
    typeof Promise !== 'undefined' &&
    typeof fetch !== 'undefined' &&
    typeof localStorage !== 'undefined'

  if (!hasRequiredFeatures) {
    const message = '您的浏览器不支持本网站所需的功能，请升级到最新版本的现代浏览器。'
    alert(message)
    return false
  }

  return true
}

// Only proceed if browser is compatible
if (checkBrowserCompatibility()) {
  const app = createApp(App)

  // Global error handler
  app.config.errorHandler = (err, instance, info) => {
    console.error('Global error:', err, info)
    const { error } = useToast()
    error('发生了一个错误，请稍后重试', 3000)
  }

  // Handle unhandled promise rejections
  window.addEventListener('unhandledrejection', (event) => {
    console.error('Unhandled promise rejection:', event.reason)
    const { error } = useToast()
    error('操作失败，请稍后重试', 3000)
    event.preventDefault()
  })

  app.use(createPinia())
  app.use(router)

  app.mount('#app')
}
