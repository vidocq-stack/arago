<script>
  // Superadmin console (arago-spec §4.8). Logs in via POST /api/admin/login, then manages the
  // speaker allowlist. The superadmin token rides the X-Arago-Admin header (NOT Authorization:
  // Bearer — that scheme belongs to cervantes/OIDC; cf. ARAGO-004).
  let token = $state(null);
  let username = $state('');
  let password = $state('');
  let loginError = $state('');

  let speakers = $state([]);
  let newEmail = $state('');
  let newRole = $state('SPEAKER');
  let error = $state('');

  const authed = () => ({ 'X-Arago-Admin': token });

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
      token = null; // token expired
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
      body: JSON.stringify({ email, role: newRole }),
    });
    if (res.ok) {
      newEmail = '';
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

  async function removeSpeaker(s) {
    await fetch(`/api/admin/speakers/${s.id}`, { method: 'DELETE', headers: authed() });
    await loadSpeakers();
  }

  function logout() {
    token = null;
    speakers = [];
    username = '';
    password = '';
  }
</script>

<main class="admin">
  <header>
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
        <button onclick={logout} data-testid="logout">Déconnexion</button>
      </div>

      <form class="new-speaker" onsubmit={createSpeaker}>
        <input placeholder="email@exemple.org" data-testid="new-email" bind:value={newEmail} />
        <select bind:value={newRole} data-testid="new-role">
          <option value="SPEAKER">SPEAKER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
        <button type="submit" data-testid="new-speaker-submit">Inviter</button>
      </form>

      {#if error}<p class="error" data-testid="error">{error}</p>{/if}

      <table data-testid="speakers-table">
        <thead>
          <tr><th>Email</th><th>Rôle</th><th>Actif</th><th></th></tr>
        </thead>
        <tbody>
          {#each speakers as s (s.id)}
            <tr data-testid="speaker-row" data-email={s.email}>
              <td>{s.email}</td>
              <td>{s.role}</td>
              <td>{s.enabled ? 'oui' : 'non'}</td>
              <td class="actions">
                <button onclick={() => toggleEnabled(s)}>{s.enabled ? 'Désactiver' : 'Activer'}</button>
                <button class="danger" onclick={() => removeSpeaker(s)}>Supprimer</button>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </section>
  {/if}
</main>

<style>
  .admin { max-width: 880px; margin: 2rem auto; padding: 0 1rem; }
  h1 { color: var(--arago-bordeaux, #7A1F2B); }
  .login { display: grid; gap: .5rem; max-width: 320px; }
  .bar { display: flex; align-items: center; justify-content: space-between; }
  .new-speaker { display: flex; gap: .5rem; margin: 1rem 0; }
  table { width: 100%; border-collapse: collapse; }
  th, td { text-align: left; padding: .4rem .6rem; border-bottom: 1px solid #ddd; }
  .actions { display: flex; gap: .4rem; }
  .danger { color: #7A1F2B; }
  .error { color: #b00020; }
</style>
