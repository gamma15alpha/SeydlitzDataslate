// Навигация (D61): разделы — вкладками на экране и кнопками на корпусе; хлебные крошки и «назад» — по адресу.
export type IconName = 'sheet' | 'book' | 'user' | 'settings' | 'back'

export interface Section {
  key: string
  path: string
  titleKey: string
  /** Маркировка кнопки на корпусе — трафаретом, как PWR и PHOSPHOR. */
  stencil: string
  icon: IconName
}

export const SECTIONS: Section[] = [
  { key: 'sheets', path: '/sheets', titleKey: 'nav.sheets', stencil: 'SHEETS', icon: 'sheet' },
  { key: 'reference', path: '/reference', titleKey: 'nav.reference', stencil: 'CODEX', icon: 'book' },
]

const SCREEN_TABS_KEY = 'seydlitz.screenTabs'

// Корпус полный (кнопки разделов на левой стойке видны) — обратное условию «тонкого» корпуса в casing.css.
const FULL_CASING = '(min-width: 901px) and (orientation: portrait), (min-width: 901px) and (min-height: 501px)'

export function sectionOf(path: string): Section | undefined {
  return SECTIONS.find(s => (s.path === '/' ? path === '/' : path === s.path || path.startsWith(`${s.path}/`)))
}

export interface Crumb {
  path: string
  titleKey: string
}

// Заголовок страницы из данных (имя персонажа) вместо titleKey — для строки приложения и title вкладки.
// Действует, пока открыт адрес, на котором его задали.
export function usePageTitle(title: () => string | undefined) {
  const route = useRoute()
  const state = useState<{ path: string; title: string } | null>('nav:pageTitle', () => null)
  const path = route.path
  watchEffect(() => {
    const value = title()
    state.value = value ? { path, title: value } : null
  })
  onScopeDispose(() => {
    if (state.value?.path === path) state.value = null
  })
}

export function useNavigation() {
  const route = useRoute()
  const router = useRouter()
  const { t } = useI18n()

  const screenTabs = useState('nav:screenTabs', () => readSetting(SCREEN_TABS_KEY) !== 'off')
  function setScreenTabs(value: boolean) {
    screenTabs.value = value
    writeSetting(SCREEN_TABS_KEY, value ? 'on' : 'off')
  }
  const fullCasing = useMediaQuery(FULL_CASING)
  // Выключить вкладки можно, только пока разделы есть на корпусе: иначе переключаться было бы нечем.
  const tabsVisible = computed(() => screenTabs.value || !fullCasing.value)

  const section = computed(() => sectionOf(route.path))

  // Крошки — по префиксам адреса: /profile/sessions → Профиль › Сессии. Страница без titleKey в цепочку не входит.
  const crumbs = computed<Crumb[]>(() => {
    const parts = route.path.split('/').filter(Boolean)
    const paths = parts.length ? parts.map((_, i) => `/${parts.slice(0, i + 1).join('/')}`) : ['/']
    return paths.flatMap((path) => {
      const titleKey = router.resolve(path).meta.titleKey
      return titleKey ? [{ path, titleKey }] : []
    })
  })
  const pageTitle = useState<{ path: string; title: string } | null>('nav:pageTitle', () => null)
  // Название текущей страницы: из данных (usePageTitle) или по titleKey.
  const title = computed(() => {
    if (pageTitle.value?.path === route.path) return pageTitle.value.title
    const key = crumbs.value.at(-1)?.titleKey
    return key ? t(key) : ''
  })
  // «Назад» — на уровень выше, а не по истории браузера: предсказуемо и после перехода по ссылке извне.
  const parent = computed(() => crumbs.value.at(-2)?.path ?? null)

  return { section, crumbs, parent, title, screenTabs, setScreenTabs, fullCasing, tabsVisible }
}
