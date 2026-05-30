<script>
  // Page d'accueil attendee (Phase 0) : champ PIN + sonde de santé.
  // Le flow de join réel (POST /api/rooms/join) arrivera en Phase 1.
  let pin = $state('');
  let health = $state('…');

  const pinDigits = $derived(pin.replace(/\D/g, '').slice(0, 6));

  async function probeHealth() {
    try {
      const res = await fetch('/api/health');
      const body = await res.json();
      health = body.status ?? (res.ok ? 'UP' : 'DOWN');
    } catch {
      health = 'DOWN';
    }
  }

  $effect(() => {
    probeHealth();
  });
</script>

<main>
  <header>
    <h1>Arago</h1>
    <p class="tagline">Speaker &amp; Lab Companion</p>
  </header>

  <section class="join-card">
    <label for="pin">Code de la room</label>
    <input
      id="pin"
      inputmode="numeric"
      autocomplete="off"
      placeholder="123456"
      maxlength="6"
      bind:value={pin}
    />
    <button disabled={pinDigits.length !== 6}>Rejoindre</button>
    <p class="hint">Pas de compte, pas d'installation — juste le PIN affiché à l'écran.</p>
  </section>

  <footer>
    <span class="pill" class:up={health === 'UP'} class:down={health === 'DOWN'}>
      backend&nbsp;: {health}
    </span>
    <a href="/privacy.html">Confidentialité</a>
  </footer>
</main>

<style>
  main {
    max-width: 28rem;
    margin: 0 auto;
    padding: 3rem 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 2rem;
    min-height: 100vh;
  }

  header {
    text-align: center;
  }

  h1 {
    margin: 0;
    font-size: 3rem;
    letter-spacing: 0.04em;
    color: var(--arago-bordeaux);
  }

  .tagline {
    margin: 0.25rem 0 0;
    color: var(--arago-gold);
    font-style: italic;
  }

  .join-card {
    background: rgba(255, 255, 255, 0.45);
    border: 1px solid var(--arago-gold);
    border-radius: 0.75rem;
    padding: 1.75rem 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.85rem;
    box-shadow: 0 8px 24px rgba(26, 20, 16, 0.12);
  }

  label {
    font-weight: 600;
  }

  input {
    font: inherit;
    font-size: 2rem;
    letter-spacing: 0.5em;
    text-align: center;
    padding: 0.6rem 0.4rem;
    border: 2px solid var(--arago-bordeaux);
    border-radius: 0.5rem;
    background: var(--arago-cream);
    color: var(--arago-ink);
  }

  button {
    font: inherit;
    font-weight: 700;
    padding: 0.75rem 1rem;
    border: none;
    border-radius: 0.5rem;
    background: var(--arago-bordeaux);
    color: var(--arago-cream);
    cursor: pointer;
  }

  button:disabled {
    background: var(--arago-paper);
    color: rgba(26, 20, 16, 0.4);
    cursor: not-allowed;
  }

  .hint {
    margin: 0;
    font-size: 0.85rem;
    color: rgba(26, 20, 16, 0.7);
  }

  footer {
    margin-top: auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.85rem;
  }

  footer a {
    color: var(--arago-bordeaux);
  }

  .pill {
    padding: 0.15rem 0.6rem;
    border-radius: 999px;
    background: var(--arago-paper);
    font-variant-numeric: tabular-nums;
  }

  .pill.up {
    background: var(--arago-success);
    color: var(--arago-cream);
  }

  .pill.down {
    background: var(--arago-danger);
    color: var(--arago-cream);
  }
</style>
