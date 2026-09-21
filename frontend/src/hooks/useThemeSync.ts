import { useEffect } from 'react'
import { resolveDark, useUiStore } from '@/store/uiStore'

/** Applies the persisted theme to <html> and follows the OS setting when theme is "system". */
export function useThemeSync() {
  const theme = useUiStore((state) => state.theme)
  useEffect(() => {
    const apply = () => document.documentElement.classList.toggle('dark', resolveDark(theme))
    apply()
    const media = window.matchMedia?.('(prefers-color-scheme: dark)')
    if (theme !== 'system' || !media) return
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [theme])
}
