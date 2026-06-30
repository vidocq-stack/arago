<script>
  import { onMount } from 'svelte';
  import Prefs from './lib/Prefs.svelte';
  import Footer from './lib/Footer.svelte';
  // Superadmin console (arago-spec §4.8). Logs in via POST /api/admin/login, then manages speaker
  // accounts (email + role + password). The superadmin token rides the X-Arago-Admin header (a
  // distinct header from the speakers' Authorization: Bearer). The token is kept in sessionStorage:
  // per-tab + survives a page reload, like the speaker/attendee sessions.
  const ADMIN_KEY = 'arago.admin';

  let token = $state(null);
  let username = $state('');
  let password = $state('');
  let loginError = $state('');

  let speakers = $state([]);
  let newEmail = $state('');
  let newRole = $state('SPEAKER');
  let newPassword = $state('');
  let error = $state('');
  let resetId = $state(null);     // speaker id whose password is being reset inline
  let resetPassword = $state('');

  const authed = () => ({ 'X-Arago-Admin': token });

  function persist(tk) { try { sessionStorage.setItem(ADMIN_KEY, tk); } catch { /* ignore */ } }
  function forget() { try { sessionStorage.removeItem(ADMIN_KEY); } catch { /* ignore */ } }

  onMount(async () => {
    let tk = null;
    try { tk = sessionStorage.getItem(ADMIN_KEY); } catch { tk = null; }
    if (!tk) return;
    token = tk;
    await loadSpeakers(); // clears token on 401
  });

  async function login(e) {
    e?.preventDefault();
    loginError = '';
    try {
      const res = await fetch('/api/admin/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      if (!res.ok) {
        loginError = 'Invalid credentials';
        return;
      }
      token = (await res.json()).token;
      persist(token);
      password = '';
      await loadSpeakers();
    } catch {
      loginError = 'Login failed';
    }
  }

  async function loadSpeakers() {
    error = '';
    const res = await fetch('/api/admin/speakers', { headers: authed() });
    if (res.ok) {
      speakers = await res.json();
    } else if (res.status === 401) {
      logout(); // token expired/invalid
    } else {
      error = 'Could not load speakers';
    }
  }

  async function createSpeaker(e) {
    e?.preventDefault();
    error = '';
    const email = newEmail.trim();
    if (!email) return;
    const res = await fetch('/api/admin/speakers', {
      method: 'POST',
      headers: { ...authed(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, role: newRole, password: newPassword || null }),
    });
    if (res.ok) {
      newEmail = ''; newPassword = '';
      await loadSpeakers();
    } else if (res.status === 409) {
      error = 'A speaker with that email already exists';
    } else {
      error = 'Could not create speaker';
    }
  }

  async function toggleEnabled(s) {
    await fetch(`/api/admin/speakers/${s.id}`, {
      method: 'PATCH',
      headers: { ...authed(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled: !s.enabled }),
    });
    await loadSpeakers();
  }

  function startReset(s) { resetId = s.id; resetPassword = ''; }

  async function saveReset(s) {
    const pw = resetPassword.trim();
    if (!pw) { resetId = null; return; }
    await fetch(`/api/admin/speakers/${s.id}`, {
      method: 'PATCH',
      headers: { ...authed(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: pw }),
    });
    resetId = null; resetPassword = '';
    await loadSpeakers();
  }

  async function removeSpeaker(s) {
    await fetch(`/api/admin/speakers/${s.id}`, { method: 'DELETE', headers: authed() });
    await loadSpeakers();
  }

  function logout() {
    forget();
    token = null;
    speakers = [];
    username = '';
    password = '';
  }
</script>

<main class="admin">
  <header>
    <Prefs />
    <h1>Arago — Console admin</h1>
  </header>

  {#if !token}
    <form class="login" onsubmit={login} data-testid="login-form">
      <h2>Connexion superadmin</h2>
      <label for="u">Identifiant</label>
      <input id="u" data-testid="login-username" bind:value={username} autocomplete="username" />
      <label for="p">Mot de passe</label>
      <input id="p" type="password" data-testid="login-password" bind:value={password}
             autocomplete="current-password" />
      <button type="submit" data-testid="login-submit">Se connecter</button>
      {#if loginError}<p class="error" data-testid="login-error">{loginError}</p>{/if}
    </form>
  {:else}
    <section class="speakers" data-testid="speakers-section">
      <div class="bar">
        <h2>Speakers ({speakers.length})</h2>
        <span class="bar-actions">
          <button onclick={loadSpeakers} data-testid="admin-refresh">↻ Rafraîchir</button>
          <button onclick={logout} data-testid="logout">Déconnexion</button>
        </span>
      </div>

      <form class="new-speaker" onsubmit={createSpeaker}>
        <input placeholder="email@exemple.org" data-testid="new-email" bind:value={newEmail} />
        <select bind:value={newRole} data-testid="new-role">
          <option value="SPEAKER">SPEAKER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
        <input type="password" placeholder="mot de passe initial" data-testid="new-password"
               autocomplete="new-password" bind:value={newPassword} />
        <button type="submit" data-testid="new-speaker-submit">Créer</button>
      </form>

      {#if error}<p class="error" data-testid="error">{error}</p>{/if}

      <table data-testid="speakers-table">
        <thead>
          <tr><th>Email</th><th>Pseudo</th><th>Rôle</th><th>Actif</th><th>Mot de passe</th><th></th></tr>
        </thead>
        <tbody>
          {#each speakers as s (s.id)}
            <tr data-testid="speaker-row" data-email={s.email}>
              <td>{s.email}</td>
              <td>{s.pseudo || '—'}</td>
              <td>{s.role}</td>
              <td>{s.enabled ? 'oui' : 'non'}</td>
              <td>{s.hasPassword ? 'défini' : '—'}</td>
              <td class="actions">
                {#if resetId === s.id}
                  <input type="password" data-testid="reset-password" placeholder="nouveau mdp"
                         autocomplete="new-password" bind:value={resetPassword} />
                  <button onclick={() => saveReset(s)} data-testid="reset-save">OK</button>
                  <button onclick={() => (resetId = null)}>Annuler</button>
                {:else}
                  <button onclick={() => toggleEnabled(s)}>{s.enabled ? 'Désactiver' : 'Activer'}</button>
                  <button onclick={() => startReset(s)} data-testid="reset-password-btn">Mot de passe</button>
                  <button class="danger" onclick={() => removeSpeaker(s)}>Supprimer</button>
                {/if}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </section>
  {/if}
  <Footer />
</main>

<style>
  .admin { max-width: 880px; margin: 2rem auto; padding: 0 1rem; }
  h1 { color: var(--arago-bordeaux, #7A1F2B); }
  .login { display: grid; gap: .5rem; max-width: 320px; }
  .bar { display: flex; align-items: center; justify-content: space-between; }
  .bar-actions { display: inline-flex; gap: .5rem; }
  .new-speaker { display: flex; gap: .5rem; margin: 1rem 0; flex-wrap: wrap; }
  table { width: 100%; border-collapse: collapse; }
  th, td { text-align: left; padding: .4rem .6rem; border-bottom: 1px solid #ddd; }
  .actions { display: flex; gap: .4rem; flex-wrap: wrap; }
  .danger { color: #7A1F2B; }
  .error { color: #b00020; }
</style>
