import { api } from '~/api/client'

// До монтирования — экран не мелькает цветом по умолчанию.
export default defineNuxtPlugin((nuxtApp) => {
  const { phosphor, scanlines, navigating, requests, fault } = useCasing()
  watchEffect(() => {
    document.documentElement.dataset.phosphor = phosphor.value
    document.documentElement.classList.toggle('no-scanlines', !scanlines.value)
  })
  nuxtApp.hook('page:start', () => {
    navigating.value = true
  })
  nuxtApp.hook('page:finish', () => {
    navigating.value = false
  })
  nuxtApp.hook('vue:error', () => {
    fault.value = true
  })
  startServerLink()
  api.use({
    onRequest() {
      requests.value++
    },
    onResponse({ response }) {
      requests.value--
      reportApiResponse(response.status)
    },
    onError() {
      requests.value--
      void checkServer()
    },
  })
})
