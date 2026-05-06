import { ref, onMounted, watch } from 'vue'

export function useTheme() {
  const theme = ref<'light' | 'dark'>('light')

  const updateTheme = () => {
    const currentTheme = document.documentElement.getAttribute('data-theme')
    theme.value = (currentTheme === 'light' ? 'light' : 'dark') as 'light' | 'dark'
  }

  onMounted(() => {
    updateTheme()

    // Watch for theme changes
    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'attributes' && mutation.attributeName === 'data-theme') {
          updateTheme()
        }
      })
    })

    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme'],
    })

    // Cleanup
    return () => observer.disconnect()
  })

  return {
    theme,
  }
}
