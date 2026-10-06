import MarkdownIt from 'markdown-it'

const REF_SCHEME = 'dataslate:'

interface RenderEnv {
  asset?: (path: string) => string | undefined
}

// Пакет контента не должен вставлять свою разметку и скрипты.
const md = new MarkdownIt({ html: false, linkify: false })

md.renderer.rules.link_open = (tokens, i, options, _env, self) => {
  const token = tokens[i]!
  const href = String(token.attrGet('href') ?? '')
  if (href.startsWith(REF_SCHEME)) {
    token.attrSet('href', '#')
    token.attrSet('data-ref', href.slice(REF_SCHEME.length))
  } else {
    token.attrSet('target', '_blank')
    token.attrSet('rel', 'noopener noreferrer')
  }
  return self.renderToken(tokens, i, options)
}

// Широкая таблица не растягивает страницу на телефоне.
md.renderer.rules.table_open = () => '<div class="table-scroll"><table>\n'
md.renderer.rules.table_close = () => '</table></div>\n'

const defaultImage = md.renderer.rules.image!
md.renderer.rules.image = (tokens, i, options, env, self) => {
  const token = tokens[i]!
  const src = String(token.attrGet('src') ?? '')
  if (src.startsWith('assets/')) {
    const url = (env as RenderEnv).asset?.(src)
    if (!url) return `<span class="missing-image">[${md.utils.escapeHtml(token.content)}]</span>`
    token.attrSet('src', url)
  }
  return defaultImage(tokens, i, options, env, self)
}

export function renderMarkdown(source: string, asset?: RenderEnv['asset']): string {
  return md.render(source, { asset } satisfies RenderEnv)
}
