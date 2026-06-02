// Light/dark theme store (arago-spec §11 Phase 6 — themes). The choice is mirrored onto
// <html data-theme> so the CSS custom properties in app.css resolve per theme across every page,
// and persisted in localStorage. Default follows the OS prefers-color-scheme on first visit.

const STORAGE_KEY = 'arago.theme';

function detect() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch { /* private mode / no storage */ }
  try {
    if (typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }
  } catch { /* ignore */ }
  return 'light';
}

let theme = $state(detect());

/** Applies the current theme to <html data-theme> (call once on mount so the attribute is never absent). */
export function applyTheme() {
  if (typeof document !== 'undefined') document.documentElement.dataset.theme = theme;
}

export function getTheme() {
  return theme;
}

export function setTheme(next) {
  if (next !== 'light' && next !== 'dark') return;
  theme = next;
  try { localStorage.setItem(STORAGE_KEY, next); } catch { /* ignore */ }
  applyTheme();
}

export function toggleTheme() {
  setTheme(theme === 'dark' ? 'light' : 'dark');
}
