<script>
  // Shared footer shown at the bottom of every page: "Propulsé par Vidocq <version>". The version is
  // captured at BUILD time — Vite replaces the __VIDOCQ_VERSION__ define with the literal string sourced
  // from the Maven property vidocq.runtime.version (see vite.config.js + arago-web/pom.xml). For a plain
  // `npm run dev` it falls back to "dev".
  import { t } from './i18n.svelte.js';
  const version = __VIDOCQ_VERSION__;
</script>

<footer class="vidocq-footer" data-testid="vidocq-footer">
  {t('footer.powered')} <span class="ver">{version}</span>
</footer>

<style>
  .vidocq-footer {
    margin-top: 2rem;
    padding: 1rem 0 1.5rem;
    text-align: center;
    font-size: 0.8rem;
    color: var(--arago-muted, rgba(26, 20, 16, 0.7));
  }
  .ver { font-variant-numeric: tabular-nums; opacity: 0.85; }
</style>
