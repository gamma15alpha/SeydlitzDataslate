declare module '#app' {
  interface PageMeta {
    /** Доступна без входа (вход, регистрация). */
    public?: boolean
    /** Ключ i18n названия страницы: заголовок в строке приложения, хлебные крошки, title вкладки браузера. */
    titleKey?: string
  }
}

export {}
