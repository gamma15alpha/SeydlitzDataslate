export type ContentLang = 'ui' | 'ru' | 'en'

const STORAGE_KEY = 'dataslate:content-lang'

export function useContentLocale() {
  const { locale } = useI18n()
  const setting = useState<ContentLang>('content-lang', () => (localStorage.getItem(STORAGE_KEY) as ContentLang | null) ?? 'ui')
  const lang = computed(() => (setting.value === 'ui' ? locale.value : setting.value))

  function setContentLang(value: ContentLang) {
    setting.value = value
    localStorage.setItem(STORAGE_KEY, value)
  }

  return { setting, lang, setContentLang, text: (t: LocalizedText) => pickText(t, lang.value) }
}
