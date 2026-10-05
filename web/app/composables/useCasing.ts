// Состояние корпуса датаслейта: люминофор и сканлайны (D9, D11), лампы и ЖК-статус.
// Настройки — в localStorage под теми же ключами, что в версии на Avalonia.
export type Phosphor = 'green' | 'amber' | 'white'

export const PHOSPHORS: { name: Phosphor; mark: string; label: string; color: string; angle: number }[] = [
  { name: 'green', mark: 'P1', label: 'P1, green', color: '#8dff7a', angle: -55 },
  { name: 'amber', mark: 'P3', label: 'P3, amber', color: '#ffb000', angle: 0 },
  { name: 'white', mark: 'P4', label: 'P4, white', color: '#e8f1ff', angle: 55 },
]

const PHOSPHOR_KEY = 'seydlitz.phosphor'
const SCANLINES_KEY = 'seydlitz.scanlines'

export function useCasing() {
  const phosphor = useState<Phosphor>('casing:phosphor', () => PHOSPHORS.find(p => p.name === readSetting(PHOSPHOR_KEY))?.name ?? 'green')
  const scanlines = useState('casing:scanlines', () => readSetting(SCANLINES_KEY) !== 'off')
  const navigating = useState('casing:navigating', () => false)
  const requests = useState('casing:requests', () => 0)
  const busy = computed(() => navigating.value || requests.value > 0)
  const fault = useState('casing:fault', () => false)

  function setPhosphor(value: Phosphor) {
    phosphor.value = value
    writeSetting(PHOSPHOR_KEY, value)
  }

  function toggleScanlines() {
    scanlines.value = !scanlines.value
    writeSetting(SCANLINES_KEY, scanlines.value ? 'on' : 'off')
  }

  return { phosphor, scanlines, navigating, requests, busy, fault, setPhosphor, toggleScanlines }
}
