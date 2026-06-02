<script>
  import { onMount } from 'svelte';
  // RGPD "my data" page (arago-spec §4.7). Landing of the magic link: ?token= is the credential
  // (aud=arago-profile, short-lived). Reads the attendee's profile + persistent messages, lets them
  // export (JSON) or erase everything. No password, ever — the token is the only auth.
  const token = new URLSearchParams(window.location.search).get('token');

  let loading = $state(true);
  let data = $state(null);     // { email, pseudo, consentAt, consentTextVersion, messages:[{roomId,body,at}] }
  let error = $state('');
  let confirming = $state(false);
  let deleted = $state(false);

  const authQuery = () => `token=${encodeURIComponent(token)}`;

  onMount(async () => {
    if (!token) { error = 'Lien expiré ou invalide.'; loading = false; return; }
    try {
      const res = await fetch(`/api/profile/me?${authQuery()}`);
      if (res.status === 401) { error = 'Lien expiré ou invalide.'; }
      else if (res.status === 404) { error = 'Aucune donnée (déjà supprimée ?).'; }
      else if (!res.ok) { error = 'Impossible de charger vos données.'; }
      else { data = await res.json(); }
    } catch {
      error = 'Impossible de charger vos données.';
    } finally {
      loading = false;
    }
  });

  function exportData() {
    // Content-Disposition: attachment → the browser downloads the JSON.
    window.location.assign(`/api/profile/me/export?${authQuery()}`);
  }

  async function confirmDelete() {
    try {
      const res = await fetch(`/api/profile/me?${authQuery()}`, { method: 'DELETE' });
      if (res.ok) { deleted = true; data = null; confirming = false; }
      else { error = 'La suppression a échoué.'; }
    } catch {
      error = 'La suppression a échoué.';
    }
  }
</script>

<main>
  <header>
    <h1>Arago</h1>
    <p class="tagline">Mes données</p>
  </header>

  {#if loading}
    <p class="hint" data-testid="mydata-loading">Chargement…</p>
  {:else if deleted}
    <p class="ok" data-testid="mydata-deleted">Vos données ont été supprimées.</p>
  {:else if error}
    <p class="error" data-testid="mydata-error">{error}</p>
  {:else if data}
    <section class="card">
      <p data-testid="mydata-email"><strong>Email :</strong> {data.email}</p>
      <p><strong>Pseudo :</strong> {data.pseudo}</p>
      {#if data.consentAt}<p class="hint">Consentement le {data.consentAt} (version {data.consentTextVersion})</p>{/if}

      <h2>Mes questions persistantes</h2>
      {#if data.messages && data.messages.length}
        <ul class="messages">
          {#each data.messages as m, i}
            <li data-testid="mydata-message" data-i={i}>{m.body}</li>
          {/each}
        </ul>
      {:else}
        <p class="hint" data-testid="mydata-no-messages">Aucune question persistante.</p>
      {/if}

      <div class="actions">
        <button type="button" data-testid="export" onclick={exportData}>Exporter mes données (JSON)</button>
        {#if confirming}
          <span class="confirm">
            Confirmer la suppression définitive ?
            <button type="button" class="danger" data-testid="delete-confirm" onclick={confirmDelete}>Oui, tout supprimer</button>
            <button type="button" class="ghost" data-testid="delete-cancel" onclick={() => (confirming = false)}>Annuler</button>
          </span>
        {:else}
          <button type="button" class="danger" data-testid="delete-all" onclick={() => (confirming = true)}>Supprimer toutes mes données</button>
        {/if}
      </div>
    </section>
  {/if}

  <footer><a href="/privacy.html">Confidentialité</a></footer>
</main>

<style>
  main {
    max-width: 40rem; margin: 0 auto; padding: 2.5rem 1.25rem;
    display: flex; flex-direction: column; gap: 1.5rem; min-height: 100vh;
  }
  header { text-align: center; }
  h1 { margin: 0; font-size: 2.6rem; letter-spacing: 0.04em; color: var(--arago-bordeaux); }
  .tagline { margin: 0.2rem 0 0; color: var(--arago-gold); font-style: italic; }
  h2 { font-size: 1.1rem; color: var(--arago-bordeaux); margin: 1rem 0 0.4rem; }

  .card {
    background: rgba(255, 255, 255, 0.45); border: 1px solid var(--arago-gold);
    border-radius: 0.75rem; padding: 1.5rem; display: flex; flex-direction: column; gap: 0.5rem;
    box-shadow: 0 8px 24px rgba(26, 20, 16, 0.12);
  }
  .messages { margin: 0; padding-left: 1.2rem; display: flex; flex-direction: column; gap: 0.3rem; }
  .actions { display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: center; margin-top: 1rem; }
  .confirm { display: inline-flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; font-weight: 600; }

  button {
    font: inherit; font-weight: 700; padding: 0.6rem 0.9rem; border: none;
    border-radius: 0.5rem; background: var(--arago-bordeaux); color: var(--arago-cream); cursor: pointer;
  }
  button.danger { background: var(--arago-danger); }
  button.ghost { background: transparent; color: var(--arago-bordeaux); border: 1px solid var(--arago-bordeaux); }

  .hint { margin: 0; font-size: 0.85rem; color: rgba(26, 20, 16, 0.7); }
  .error { margin: 0; color: var(--arago-danger); font-weight: 600; }
  .ok { margin: 0; color: var(--arago-bordeaux); font-weight: 700; }

  footer { margin-top: auto; text-align: center; font-size: 0.85rem; }
  footer a { color: var(--arago-bordeaux); }
</style>
