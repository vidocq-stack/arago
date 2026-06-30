<script>
  import { onMount } from 'svelte';
  import Footer from './lib/Footer.svelte';
  import { t, applyLang } from './lib/i18n.svelte.js';

  // Public projector / lobby screen (arago-spec §4.1): no auth, opened from the speaker console
  // ("Afficher"). Shows the room title + a big PIN for attendees to join, plus a live headcount the
  // page polls every few seconds (GET /api/rooms/lobby/{pin}). The PIN is the public join credential.
  const pin = new URLSearchParams(location.search).get('pin') || '';
  let title = $state('');
  let attendees = $state(0);
  let status = $state('');
  let error = $state('');     // '' | 'notfound'

  async function refresh() {
    if (!pin) { error = 'notfound'; return; }
    try {
      const res = await fetch(`/api/rooms/lobby/${encodeURIComponent(pin)}`);
      if (res.status === 404) { error = 'notfound'; return; }
      if (!res.ok) return; // transient server hiccup: keep the last good values on screen
      const d = await res.json();
      title = d.title; attendees = d.attendees; status = d.status; error = '';
    } catch {
      /* transient network error: keep the last good values rather than blanking the projection */
    }
  }

  onMount(() => {
    applyLang();
    refresh();
    const id = setInterval(refresh, 3000);
    return () => clearInterval(id);
  });
</script>

<main class="display">
  {#if error === 'notfound'}
    <p class="msg" data-testid="display-error">{t('display.notfound')}</p>
  {:else}
    <h1 class="title" data-testid="display-title">{title}</h1>
    {#if status === 'ENDED'}
      <p class="msg" data-testid="display-ended">{t('display.ended')}</p>
    {/if}
    <div class="pin-block">
      <span class="pin-label">{t('display.pin')}</span>
      <span class="pin" data-testid="display-pin">{pin}</span>
    </div>
    <p class="count">
      <span class="num" data-testid="display-count">{attendees}</span>
      <span class="count-label">{t('display.attendees')}</span>
    </p>
  {/if}
  <Footer />
</main>

<style>
  .display {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3vh;
    text-align: center;
    padding: 4vh 4vw;
    color: var(--arago-ink);
  }
  .title {
    font-size: clamp(2rem, 6vw, 5.5rem);
    margin: 0;
    max-width: 90vw;
  }
  .pin-block {
    display: flex;
    flex-direction: column;
    gap: 1vh;
  }
  .pin-label {
    font-size: clamp(1rem, 2.5vw, 2rem);
    color: var(--arago-muted);
    text-transform: uppercase;
    letter-spacing: 0.25em;
  }
  .pin {
    font-size: clamp(4rem, 20vw, 18rem);
    font-weight: 800;
    letter-spacing: 0.12em;
    line-height: 1;
    color: var(--arago-bordeaux);
    font-variant-numeric: tabular-nums;
  }
  .count {
    font-size: clamp(1.5rem, 4vw, 3.5rem);
    margin: 0;
    color: var(--arago-muted);
  }
  .count .num {
    font-weight: 800;
    color: var(--arago-ink);
    font-variant-numeric: tabular-nums;
  }
  .msg {
    font-size: clamp(1.2rem, 3.5vw, 2.5rem);
    color: var(--arago-muted);
  }
</style>
