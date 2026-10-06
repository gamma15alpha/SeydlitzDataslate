<script setup lang="ts">
const { t } = useI18n()
const route = useRoute()
const auth = useAuth()
const { section, crumbs, parent, title, tabsVisible } = useNavigation()
useHead({ title })

const under = (prefix: string) => route.path === prefix || route.path.startsWith(`${prefix}/`)
</script>

<template>
  <div class="shell">
    <header class="app-bar">
      <NuxtLink v-if="parent" :to="parent" class="icon-button" :aria-label="t('nav.back')">
        <AppIcon name="back" />
      </NuxtLink>
      <div class="title">
        <nav v-if="crumbs.length > 1" :aria-label="t('nav.breadcrumbs')">
          <ol class="crumbs">
            <li v-for="c in crumbs.slice(0, -1)" :key="c.path">
              <NuxtLink :to="c.path">{{ t(c.titleKey) }}</NuxtLink>
            </li>
          </ol>
        </nav>
        <h1 v-if="title">{{ title }}</h1>
      </div>
      <NuxtLink to="/profile" class="shortcut" :class="{ active: under('/profile') }" :aria-label="t('nav.profile')">
        <AppIcon name="user" />
        <span class="shortcut-label">{{ auth.user.value?.displayName }}</span>
      </NuxtLink>
      <NuxtLink to="/settings" class="icon-button" :class="{ active: under('/settings') }" :aria-label="t('nav.settings')">
        <AppIcon name="settings" />
      </NuxtLink>
    </header>

    <nav v-if="tabsVisible" class="tabs" :aria-label="t('nav.sections')">
      <NuxtLink
        v-for="s in SECTIONS" :key="s.key" :to="s.path"
        class="tab" :class="{ active: section?.key === s.key }"
        :aria-current="section?.key === s.key ? 'page' : undefined"
      >
        <AppIcon :name="s.icon" />
        <span class="tab-label">{{ t(s.titleKey) }}</span>
      </NuxtLink>
    </nav>

    <main class="content">
      <slot />
    </main>
  </div>
</template>

<style scoped>
/* Телефон: вкладки снизу, под большим пальцем */
.shell {
  display: grid;
  height: 100%;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr) auto;
  grid-template-areas:
    "bar"
    "content"
    "tabs";
}

.app-bar {
  grid-area: bar;
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 48px;
  padding: 2px 6px;
  border-bottom: 1px solid var(--panel-border);
  background: var(--panel);
}

.title {
  display: grid;
  flex: 1;
  min-width: 0;
  padding: 0 6px;
}

h1 {
  overflow: hidden;
  margin: 0;
  font-size: var(--text-m);
  line-height: 1.3;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.crumbs {
  display: flex;
  overflow: hidden;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-label);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.crumbs li::after {
  content: '›';
  margin: 0 0.5em;
  color: var(--phosphor-dim);
}

.crumbs a {
  color: var(--phosphor-secondary);
  text-decoration: none;
}

.crumbs a:hover {
  color: var(--phosphor);
  text-decoration: underline;
}

.icon-button,
.shortcut {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 40px;
  height: 40px;
  padding: 0 8px;
  box-sizing: border-box;
  border: 1px solid transparent;
  color: var(--phosphor-secondary);
  text-decoration: none;
}

.icon-button:hover,
.shortcut:hover {
  border-color: var(--phosphor-dim);
  background: var(--screen);
  color: var(--phosphor);
}

.icon-button.active,
.shortcut.active {
  border-color: var(--phosphor-dim);
  color: var(--phosphor);
}

.shortcut-label {
  display: none;
  max-width: 16ch;
  overflow: hidden;
  font-size: var(--text-s);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.tabs {
  grid-area: tabs;
  display: flex;
  border-top: 1px solid var(--panel-border);
  background: var(--panel);
}

.tab {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-height: 52px;
  padding: 4px;
  font-size: var(--text-xs);
  color: var(--phosphor-secondary);
  text-decoration: none;
}

.tab:hover {
  color: var(--phosphor);
}

.tab.active {
  color: var(--phosphor);
  box-shadow: inset 0 2px 0 var(--phosphor);
  background: color-mix(in srgb, var(--phosphor) 8%, var(--screen));
}

.content {
  grid-area: content;
  overflow: auto;
  overscroll-behavior: contain;
  /* cqh у страниц — от области контента */
  container: content / size;
}

@container screen (min-width: 500px) {
  .shell {
    grid-template-rows: auto auto minmax(0, 1fr);
    grid-template-areas:
      "bar"
      "tabs"
      "content";
  }

  .app-bar {
    padding: 4px 10px;
  }

  h1 {
    font-size: var(--text-l);
  }

  .crumbs {
    font-size: var(--text-xs);
  }

  .shortcut-label {
    display: block;
  }

  .tabs {
    gap: 4px;
    padding: 0 10px;
    border-top: 0;
    border-bottom: 1px solid var(--panel-border);
    background: transparent;
  }

  .tab {
    flex: none;
    flex-direction: row;
    gap: 8px;
    min-height: 40px;
    padding: 0 16px;
    font-size: var(--text-s);
    letter-spacing: 0.06em;
  }

  .tab.active {
    box-shadow: inset 0 -2px 0 var(--phosphor);
  }
}

/* Телефон в альбоме: вкладки колонкой слева, чтобы не съедать высоту */
@container screen (min-width: 500px) and (max-height: 450px) {
  .shell {
    grid-template-columns: auto minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
    grid-template-areas:
      "tabs bar"
      "tabs content";
  }

  .app-bar {
    min-height: 40px;
    padding: 0 6px;
  }

  h1 {
    font-size: var(--text-m);
  }

  .crumbs {
    display: none;
  }

  .tabs {
    flex-direction: column;
    padding: 4px 0;
    border-bottom: 0;
    border-right: 1px solid var(--panel-border);
    background: var(--panel);
  }

  .tab {
    width: 52px;
    min-height: 48px;
    padding: 0;
    justify-content: center;
  }

  .tab-label {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }

  .tab.active {
    box-shadow: inset 2px 0 0 var(--phosphor);
  }
}

@media (pointer: coarse) {
  .icon-button,
  .shortcut {
    min-width: 44px;
    height: 44px;
  }
}
</style>
