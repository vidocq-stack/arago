<script>
  // Shared preferences bar: language (FR/EN) + light/dark theme. Mounted in every page header so the
  // choice is reachable everywhere; the attendee page is fully translated, the operator consoles inherit
  // the theme (CSS custom properties) and the bar. Buttons carry explicit data-testid + aria-label (a11y AA).
  import { onMount } from 'svelte';
  import { t, getLang, setLang, applyLang } from './i18n.svelte.js';
  import { getTheme, toggleTheme, applyTheme } from './theme.svelte.js';

  // Ensure <html lang> / <html data-theme> reflect the persisted choice as soon as the page mounts.
  onMount(() => { applyLang(); applyTheme(); });
</script>

<nav class="prefs" aria-label={t('prefs.language')}>
  <div class="langs" role="group" aria-label={t('prefs.language')}>
    <button type="button" data-testid="lang-fr" aria-pressed={getLang() === 'fr'}
            class:active={getLang() === 'fr'} onclick={() => setLang('fr')} lang="fr">FR</button>
    <button type="button" data-testid="lang-en" aria-pressed={getLang() === 'en'}
            class:active={getLang() === 'en'} onclick={() => setLang('en')} lang="en">EN</button>
  </div>
  <button type="button" class="theme" data-testid="theme-toggle"
          aria-label={t('prefs.theme-toggle')} title={t('prefs.theme-toggle')}
          onclick={toggleTheme}>{getTheme() === 'dark' ? '☀' : '☾'}</button>
</nav>

<style>
  .prefs {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    justify-content: flex-end;
  }
  .langs {
    display: inline-flex;
    border: 1px solid var(--arago-bordeaux);
    border-radius: 999px;
    overflow: hidden;
  }
  .langs button {
    font: inherit;
    font-size: 0.75rem;
    font-weight: 700;
    padding: 0.2rem 0.6rem;
    border: none;
    background: transparent;
    color: var(--arago-bordeaux);
    cursor: pointer;
  }
  .langs button.active {
    background: var(--arago-bordeaux);
    color: var(--arago-cream);
  }
  .theme {
    font: inherit;
    font-size: 0.95rem;
    line-height: 1;
    padding: 0.25rem 0.5rem;
    border: 1px solid var(--arago-bordeaux);
    border-radius: 999px;
    background: transparent;
    color: var(--arago-bordeaux);
    cursor: pointer;
  }
</style>
