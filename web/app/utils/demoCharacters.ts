import pack from '../../../schemas/examples/dh1-mock.content.json'
import character from '../../../schemas/examples/dh1.character.json'

export type Character = typeof character
export const demoContent = pack.content as Dh1Content
export const demoCharacters: Character[] = [character]

export function findDemoCharacter(id: string): Character | undefined {
  return demoCharacters.find(c => c.id === id)
}

export const SYSTEM_NAMES: Record<string, string> = { dh1: 'Dark Heresy 1e' }
