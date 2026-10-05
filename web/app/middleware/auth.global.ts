export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuth()
  await auth.restore()

  if (to.meta.public) {
    if (auth.user.value) return navigateTo(safeRedirect(to.query.redirect))
    return
  }
  if (!auth.user.value) {
    return navigateTo({ path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } })
  }
})
