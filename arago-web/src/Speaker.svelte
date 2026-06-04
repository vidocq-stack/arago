<script>
  import { onMount } from 'svelte';
  import { generate as qrGenerate, toSvg as qrToSvg } from './lib/qrcode.js';

  /** Scannable QR SVG for `text`, or null when it does not fit (too long). */
  function qrSvg(text) {
    const m = text ? qrGenerate(text) : null;
    return m ? qrToSvg(m, { size: 150 }) : null;
  }
  import Prefs from './lib/Prefs.svelte';
  // Speaker console (arago-spec §9). OIDC login (return=/speaker), "my rooms" list + create, and per
  // room: a LIVE top-down view (observer token → room WebSocket), the help queue (claim/resolve), a
  // layout editor (toggle blocked seats → PUT layout), pins (add/list/reorder DnD/delete), and
  // moderation (mute/kick). Reuses the attendee WebSocket path read-only via an observer token.

  // --- auth ---
  let token = $state(null);          // Keycloak access token (in memory)
  let me = $state(null);             // { email, role }
  let authError = $state('');

  // --- rooms ---
  let rooms = $state([]);
  let newTitle = $state('');
  let newMode = $state('CONF');
  let newRows = $state(3);
  let newBlocks = $state('4+4');
  let roomError = $state('');

  // --- selected room (live) ---
  let room = $state(null);           // RoomView of the open room
  let ws = $state(null);
  let layout = $state(null);
  let seats = $state({});            // "r-b-s" -> pseudo
  let helps = $state([]);            // active help requests
  let pins = $state([]);
  let muted = $state({});            // pseudo -> true
  let people = $state({});           // pseudo -> true (seen via seats/chat)
  let toast = $state('');
  let newPinType = $state('TEXT');
  let newPinContent = $state('');
  let newPinFile = $state(null);     // selected file for an IMAGE/FILE pin
  let dragFrom = $state(null);
  let revealInfo = $state(null);     // { secret, pin } after enabling reveal
  let revealState = $state(null);    // "H.V" current slide, from reveal.state frames
  let stats = $state(null);          // { messages, persistentMessages, helpTotal, helpResolved, attendees }
  let chatLog = $state([]);          // [{id, author, body, fromSpeaker}] live + replayed chat
  let chatInput = $state('');
  let chatBox = $state(null);        // chat scroll container ref
  let managersList = $state([]);     // co-speakers of the open room (owner view)
  let pendingDelete = $state(null);  // room awaiting delete confirmation (custom dialog)
  let invitePseudo = $state('');     // pseudo to invite as a co-speaker (§17.3)
  // Speaker pseudo: server-side identity (chat author name + co-speaker invite key). Editable.
  let pseudoInput = $state('');

  async function savePseudo(e) {
    e?.preventDefault();
    const base = pseudoInput.trim();
    if (!base) return;
    const res = await fetch('/api/oidc/me/pseudo', {
      method: 'PUT', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ pseudo: base }),
    });
    if (res.ok) { me = await res.json(); pseudoInput = me.pseudo || ''; }
  }

  /** The pseudo to expose on the room WebSocket (their chat author), or a neutral fallback. */
  function speakerPseudo() {
    return (me && me.pseudo) ? me.pseudo : 'Speaker';
  }

  const seatKey = (r, b, s) => `${r}-${b}-${s}`;
  const authHeaders = () => ({ Authorization: `Bearer ${token}` });

  // ---------- auth ----------
  function login() {
    window.location.assign('/api/oidc/login?return=/speaker');
  }

  // Opens the public attendee/projector screen for a room (title + PIN + live headcount) in a new tab,
  // so the speaker can project it. Public page (no auth) keyed by the room PIN — see Display.svelte.
  function openDisplay(r) {
    window.open(`/display?pin=${encodeURIComponent(r.pin)}`, '_blank', 'noopener');
  }

  // Auto-refresh "Mes rooms" so an invitation to co-manage appears without a manual reload (§17.3).
  onMount(() => {
    const id = setInterval(() => { if (token && !room) loadRooms(); }, 8000);
    return () => clearInterval(id);
  });

  onMount(async () => {
    const params = new URLSearchParams(window.location.search);
    const ok = params.get('login') === 'ok';
    const err = params.get('oidc_error');
    if (ok || err) window.history.replaceState({}, '', window.location.pathname);
    if (err) {
      authError =
        err === 'speaker_not_provisioned' ? 'Compte non provisionné — demandez à un administrateur.'
        : err === 'oidc_not_configured' ? "La connexion par identité n'est pas configurée sur ce serveur."
        : 'Connexion impossible.';
      return;
    }
    if (!ok) return;
    try {
      const res = await fetch('/api/oidc/token', { method: 'POST' });
      if (!res.ok) { authError = 'Connexion impossible.'; return; }
      token = (await res.json()).accessToken;
      const meRes = await fetch('/api/oidc/me', { headers: authHeaders() });
      if (!meRes.ok) { authError = 'Connexion impossible.'; return; }
      me = await meRes.json();
      pseudoInput = me.pseudo || '';
      await loadRooms();
    } catch { authError = 'Connexion impossible.'; }
  });

  // ---------- rooms ----------
  async function loadRooms() {
    const res = await fetch('/api/rooms', { headers: authHeaders() });
    if (res.ok) rooms = await res.json();
  }

  function buildLayout() {
    const sizes = newBlocks.split('+').map((s) => parseInt(s.trim(), 10)).filter((n) => n > 0);
    return {
      rows: Number(newRows),
      blocks: sizes.map((size, i) => ({ size, label: String.fromCharCode(65 + i) })),
      stagePos: 'TOP', rowLabels: 'NUMERIC', blockedSeats: [],
    };
  }

  async function createRoom(e) {
    e?.preventDefault();
    roomError = '';
    const body = { title: newTitle.trim(), mode: newMode };
    if (newMode === 'LAB' || newMode === 'HYBRID') body.layout = buildLayout();
    const res = await fetch('/api/rooms', {
      method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (res.ok) { newTitle = ''; await loadRooms(); }
    else roomError = 'Création impossible.';
  }

  async function endRoom(id) {
    await fetch(`/api/rooms/${id}/end`, { method: 'POST', headers: authHeaders() });
    if (room && room.id === id) closeRoom();
    await loadRooms();
  }

  function deleteRoom(r) {
    pendingDelete = r; // open the confirmation dialog
  }

  async function confirmDelete() {
    const r = pendingDelete;
    pendingDelete = null;
    if (!r) return;
    const res = await fetch(`/api/rooms/${r.id}`, { method: 'DELETE', headers: authHeaders() });
    if (res.ok) {
      if (room && room.id === r.id) closeRoom();
      await loadRooms();
    } else {
      roomError = 'Suppression impossible.';
    }
  }

  // ---------- open a room (live observer) ----------
  async function openRoom(r) {
    closeRoom();
    room = r;
    layout = r.layout || null;
    seats = {}; helps = []; pins = []; muted = {}; people = {};
    revealInfo = null; revealState = null; stats = null; chatLog = []; chatInput = '';
    managersList = []; invitePseudo = '';
    const tk = await fetch(`/api/rooms/${r.id}/observer-token?name=${encodeURIComponent(speakerPseudo())}`,
      { method: 'POST', headers: authHeaders() });
    if (!tk.ok) { roomError = 'Connexion à la room impossible.'; return; }
    const { token: obsToken, pin } = await tk.json();
    await loadHelp();
    await loadPins();
    await loadStats();
    if (r.owned) await loadManagers();
    connect(pin, obsToken);
  }

  function closeRoom() {
    if (ws) { try { ws.close(); } catch { /* ignore */ } ws = null; }
    room = null;
  }

  function connect(pin, obsToken) {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const sock = new WebSocket(`${proto}://${location.host}/ws/rooms/${pin}?token=${encodeURIComponent(obsToken)}`);
    sock.onmessage = (ev) => onFrame(ev.data);
    ws = sock;
  }

  function onFrame(raw) {
    let m;
    try { m = JSON.parse(raw); } catch { return; }
    switch (m.type) {
      case 'layout': layout = m.layout; break;
      case 'seat': onSeat(m); break;
      case 'help': onHelp(m); break;
      case 'pin': onPin(m); break;
      case 'chat': onChat(m); break;
      case 'reveal.state': revealState = `${m.indexh}.${m.indexv}`; break;
      default: break;
    }
  }

  function onSeat(m) {
    const key = seatKey(m.row, m.block, m.seat);
    if (m.action === 'taken') {
      seats = { ...seats, [key]: m.attendee };
      if (m.attendee) people = { ...people, [m.attendee]: true };
    } else if (m.action === 'free') {
      const next = { ...seats }; delete next[key]; seats = next;
    }
  }

  function onHelp(m) {
    const known = helps.some((h) => h.id === m.id);
    const active = m.status === 'PENDING' || m.status === 'CLAIMED';
    const others = helps.filter((h) => h.id !== m.id);
    helps = active ? [...others, m] : others;
    if (m.attendee) people = { ...people, [m.attendee]: true };
    // A brand-new pending request (not part of the initial replay) — chime for the speaker.
    if (m.status === 'PENDING' && !known) beep(880, 220);
  }

  function onPin(m) {
    if (m.action === 'reorder') return; // we drive reorder locally
    // WS frames carry the kind as `pinType`; REST (loadPins) as `type`. Normalise to `type`.
    if (m.action === 'add' && m.pin) pins = [...pins.filter((p) => p.id !== m.pin.id), { ...m.pin, type: m.pin.pinType }];
    else if (m.action === 'remove' && m.pin) pins = pins.filter((p) => p.id !== m.pin.id);
  }

  // ---------- chat ----------
  function onChat(m) {
    if (m.author) people = { ...people, [m.author]: true };
    chatLog = [...chatLog, {
      id: m.id || `${Date.now()}-${chatLog.length}`,
      author: m.author || '?',
      body: m.body || '',
      fromSpeaker: !!m.fromSpeaker,
      mine: !!m.fromSpeaker, // this console is the speaker; its own messages are flagged fromSpeaker
      attachmentId: m.attachmentId || null,
      attachmentKind: m.attachmentKind || null,
      attachmentName: m.attachmentName || null,
    }].slice(-200);
  }

  function sendChat(e) {
    e?.preventDefault();
    const body = chatInput.trim();
    if (!body || !ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(JSON.stringify({ body }));
    chatInput = '';
  }

  // Upload a file as a room attachment (speaker authorizes with the Bearer), then post it to the chat.
  async function pickChatFile(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !room) return;
    const kind = file.type.startsWith('image/') ? 'image' : 'file';
    try {
      const res = await fetch(
        `/api/rooms/${room.id}/attachments?kind=${kind}&filename=${encodeURIComponent(file.name)}`,
        { method: 'POST', headers: { ...authHeaders(), 'Content-Type': file.type || 'application/octet-stream' }, body: file });
      if (!res.ok) { roomError = 'Envoi du fichier impossible.'; return; }
      const a = await res.json();
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          body: chatInput.trim(), attachmentId: a.id, attachmentKind: a.kind, attachmentName: a.filename,
        }));
        chatInput = '';
      }
    } catch { roomError = 'Envoi du fichier impossible.'; }
  }

  $effect(() => {
    chatLog.length;
    if (chatBox) chatBox.scrollTop = chatBox.scrollHeight;
  });

  // Short zero-dependency notification beep (WebAudio) — fires when a new help request arrives.
  let audioCtx = null;
  function beep(freq = 880, ms = 200) {
    try {
      audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      if (audioCtx.state === 'suspended') audioCtx.resume();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      gain.gain.value = 0.07;
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      osc.stop(audioCtx.currentTime + ms / 1000);
    } catch { /* audio unavailable */ }
  }

  // ---------- help queue ----------
  async function loadHelp() {
    const res = await fetch(`/api/rooms/${room.id}/help`, { headers: authHeaders() });
    if (res.ok) {
      const all = await res.json();
      helps = all.filter((h) => h.status === 'PENDING' || h.status === 'CLAIMED');
    }
  }

  async function claimHelp(h) {
    await fetch(`/api/rooms/${room.id}/help/${h.id}/claim`, { method: 'POST', headers: authHeaders() });
  }

  async function resolveHelp(h) {
    const res = await fetch(`/api/rooms/${room.id}/help/${h.id}/resolve`, { method: 'POST', headers: authHeaders() });
    if (res.ok) {
      helps = helps.filter((x) => x.id !== h.id);
      flash('Demande résolue.');
    }
  }

  function flash(msg) { toast = msg; setTimeout(() => { if (toast === msg) toast = ''; }, 3000); }

  // ---------- layout editor ----------
  function isBlocked(r, b, s) {
    return !!layout?.blockedSeats?.some((x) => x.row === r && x.block === b && x.seat === s);
  }

  // Right-click a seat (§17.4): occupied -> force-release the occupant; otherwise toggle availability.
  function onSeatAction(e, r, b, s) {
    e.preventDefault(); // suppress the browser context menu
    const occupant = seats[seatKey(r, b, s)];
    if (occupant) {
      forceRelease(r, b, s, occupant);
    } else {
      toggleBlocked(r, b, s);
    }
  }

  async function forceRelease(r, b, s, who) {
    const res = await fetch(`/api/rooms/${room.id}/seats/release`, {
      method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ row: r, block: b, seat: s }),
    });
    // The seat "free" arrives over the WebSocket and updates the map live.
    if (res.ok) toast = `Place libérée (${who}).`;
  }

  async function toggleBlocked(r, b, s) {
    if (!layout) return;
    const blocked = layout.blockedSeats || [];
    const exists = blocked.some((x) => x.row === r && x.block === b && x.seat === s);
    const next = exists
      ? blocked.filter((x) => !(x.row === r && x.block === b && x.seat === s))
      : [...blocked, { row: r, block: b, seat: s }];
    const res = await fetch(`/api/rooms/${room.id}/layout`, {
      method: 'PUT', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...layout, blockedSeats: next }),
    });
    if (res.ok) layout = (await res.json()).layout;
  }

  // ---------- pins ----------
  async function loadPins() {
    const res = await fetch(`/api/rooms/${room.id}/pins`, { headers: authHeaders() });
    if (res.ok) pins = await res.json();
  }

  async function addPin(e) {
    e?.preventDefault();
    let content;
    if (newPinType === 'IMAGE' || newPinType === 'FILE') {
      if (!newPinFile) return;
      const kind = newPinType === 'IMAGE' ? 'image' : 'file';
      const up = await fetch(
        `/api/rooms/${room.id}/attachments?kind=${kind}&filename=${encodeURIComponent(newPinFile.name)}`,
        { method: 'POST', headers: { ...authHeaders(), 'Content-Type': newPinFile.type || 'application/octet-stream' }, body: newPinFile });
      if (!up.ok) { roomError = 'Envoi du fichier impossible.'; return; }
      content = (await up.json()).id; // pin the attachment id
    } else {
      if (!newPinContent.trim()) return;
      content = newPinContent.trim();
    }
    const res = await fetch(`/api/rooms/${room.id}/pins`, {
      method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: newPinType, content }),
    });
    if (res.ok) { newPinContent = ''; newPinFile = null; await loadPins(); }
  }

  async function deletePin(id) {
    await fetch(`/api/pins/${id}`, { method: 'DELETE', headers: authHeaders() });
    pins = pins.filter((p) => p.id !== id);
  }

  function onDragStart(i) { dragFrom = i; }
  async function onDrop(i) {
    if (dragFrom === null || dragFrom === i) { dragFrom = null; return; }
    const next = [...pins];
    const [moved] = next.splice(dragFrom, 1);
    next.splice(i, 0, moved);
    pins = next;
    dragFrom = null;
    await fetch(`/api/rooms/${room.id}/pins/order`, {
      method: 'PUT', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids: pins.map((p) => p.id) }),
    });
  }

  // ---------- moderation ----------
  async function mod(action, pseudo) {
    await fetch(`/api/rooms/${room.id}/moderation/${action}`, {
      method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ pseudo }),
    });
    if (action === 'mute') muted = { ...muted, [pseudo]: true };
    if (action === 'unmute') { const n = { ...muted }; delete n[pseudo]; muted = n; }
    if (action === 'kick') { const n = { ...people }; delete n[pseudo]; people = n; }
  }

  // ---------- reveal remote control (§4.6) ----------
  async function enableReveal() {
    const res = await fetch(`/api/rooms/${room.id}/reveal/enable`, { method: 'POST', headers: authHeaders() });
    if (res.ok) revealInfo = await res.json();
  }

  async function revealCmd(cmd) {
    await fetch(`/api/rooms/${room.id}/reveal/cmd`, {
      method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ cmd }),
    });
  }

  // ---------- co-speakers (§17.3, owner only) ----------
  async function loadManagers() {
    const res = await fetch(`/api/rooms/${room.id}/managers`, { headers: authHeaders() });
    managersList = res.ok ? await res.json() : [];
  }

  async function addManager(e) {
    e?.preventDefault();
    const pseudo = invitePseudo.trim();
    if (!pseudo) return;
    const res = await fetch(`/api/rooms/${room.id}/managers`, {
      method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ pseudo }),
    });
    if (res.ok) { invitePseudo = ''; await loadManagers(); }
    else roomError = 'Invitation impossible (pseudo introuvable ?).';
  }

  async function removeManager(email) {
    await fetch(`/api/rooms/${room.id}/managers/${encodeURIComponent(email)}`,
      { method: 'DELETE', headers: authHeaders() });
    await loadManagers();
  }

  // ---------- history & exports (§11 Phase 5) ----------
  async function loadStats() {
    const res = await fetch(`/api/rooms/${room.id}/stats`, { headers: authHeaders() });
    if (res.ok) stats = await res.json();
  }

  async function download(path, filename) {
    const res = await fetch(path, { headers: authHeaders() });
    if (!res.ok) return;
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  // ---------- top-down geometry (mirrors App.svelte §4.5) ----------
  const SEAT = 26, SGAP = 5, BGAP = 22, RGAP = 16, PAD = 18, STAGE = 22, ROWLBL = 22;
  const geom = $derived.by(() => {
    if (!layout || !layout.blocks?.length) return null;
    const blocks = layout.blocks;
    const blockWidth = (b) => b.size * SEAT + (b.size - 1) * SGAP;
    const blockX = [];
    let x = PAD + ROWLBL;
    for (const b of blocks) { blockX.push(x); x += blockWidth(b) + BGAP; }
    const width = x - BGAP + PAD;
    const top = PAD + STAGE + 10;
    const height = top + layout.rows * SEAT + (layout.rows - 1) * RGAP + PAD;
    const cells = [];
    for (let r = 0; r < layout.rows; r++) {
      const y = top + r * (SEAT + RGAP);
      for (let bi = 0; bi < blocks.length; bi++) {
        for (let s = 0; s < blocks[bi].size; s++) {
          cells.push({ r, b: bi, s, x: blockX[bi] + s * (SEAT + SGAP), y });
        }
      }
    }
    return { width, height, cells, stageW: width - 2 * PAD };
  });

  const helpSeats = $derived(new Set(helps.map((h) => `${h.seatRow ?? h.row}-${h.seatBlockIndex ?? h.block}-${h.seatInBlock ?? h.seat}`)));

  function seatCls(r, b, s) {
    if (isBlocked(r, b, s)) return 'blocked';
    if (helpSeats.has(seatKey(r, b, s))) return 'help';
    if (seats[seatKey(r, b, s)]) return 'occupied';
    return 'free';
  }
</script>

<main>
  <header>
    <Prefs />
    <h1>Arago</h1>
    <p class="tagline">Console speaker</p>
  </header>

  {#if !me}
    <section class="card">
      <p class="hint">Connectez-vous pour gérer vos rooms.</p>
      <button type="button" data-testid="speaker-login" onclick={login}>Se connecter (Keycloak)</button>
      {#if authError}<p class="error" data-testid="speaker-error">{authError}</p>{/if}
    </section>
  {:else}
    <p class="speaker-id" data-testid="speaker-identity">Connecté : {me.email} ({me.role})</p>
    {#if toast}<p class="ok" data-testid="speaker-toast">{toast}</p>{/if}

    {#if !room}
      <section class="card">
        <h2>Mes rooms</h2>
        {#if rooms.length}
          <ul class="rooms">
            {#each rooms as r (r.id)}
              <li>
                <button type="button" class="link" data-testid="open-room" onclick={() => openRoom(r)}>{r.title}</button>
                <span class="meta">{r.mode} · {r.status} · PIN {r.pin}</span>
                {#if !r.owned}<span class="meta owner" data-testid="room-owner">par {r.ownerName}</span>{/if}
                <button type="button" class="ghost" data-testid="display-room" onclick={() => openDisplay(r)}>Afficher</button>
                {#if r.owned}
                  <button type="button" class="ghost" data-testid="end-room" onclick={() => endRoom(r.id)}>Terminer</button>
                  <button type="button" class="ghost danger" data-testid="delete-room" onclick={() => deleteRoom(r)}>Supprimer</button>
                {/if}
              </li>
            {/each}
          </ul>
        {:else}
          <p class="hint" data-testid="no-rooms">Aucune room pour l'instant.</p>
        {/if}
      </section>

      <form class="card" onsubmit={createRoom}>
        <h2>Nouvelle room</h2>
        <input data-testid="room-title" placeholder="Titre" bind:value={newTitle} />
        <select data-testid="room-mode" bind:value={newMode}>
          <option value="CONF">Conférence</option>
          <option value="LAB">Atelier (LAB)</option>
          <option value="HYBRID">Hybride</option>
        </select>
        {#if newMode !== 'CONF'}
          <label>Rangées <input data-testid="room-rows" type="number" min="1" bind:value={newRows} /></label>
          <label>Blocs (ex. 10+10+3) <input data-testid="room-blocks" bind:value={newBlocks} /></label>
        {/if}
        <button type="submit" data-testid="create-room" disabled={!newTitle.trim()}>Créer</button>
        {#if roomError}<p class="error" data-testid="room-error">{roomError}</p>{/if}
      </form>
    {:else}
      <section class="card">
        <div class="bar">
          <strong>{room.title}</strong>
          <span class="meta">{room.mode} · PIN {room.pin}</span>
          <button type="button" class="ghost" data-testid="back-rooms" onclick={closeRoom}>← Mes rooms</button>
        </div>

        {#if geom}
          <div class="maprow">
            <span class="hint">Clic droit sur une place : libre ⇄ indisponible · place occupée → libérer.</span>
            <svg class="map" data-testid="speaker-map" viewBox={`0 0 ${geom.width} ${geom.height}`} width={geom.width} height={geom.height}>
              <defs>
                <pattern id="hatch" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                  <rect width="7" height="7" fill="var(--arago-paper)" />
                  <line x1="0" y1="0" x2="0" y2="7" stroke="var(--arago-bordeaux)" stroke-width="2.5" opacity="0.6" />
                </pattern>
              </defs>
              <rect class="stage" x={PAD} y={PAD} width={geom.stageW} height={STAGE} rx="4" />
              <text class="stage-txt" x={geom.width / 2} y={PAD + STAGE / 2 + 4} text-anchor="middle">Scène</text>
              {#each geom.cells as c (c.r + '-' + c.b + '-' + c.s)}
                {@const cls = seatCls(c.r, c.b, c.s)}
                {@const who = seats[seatKey(c.r, c.b, c.s)]}
                <rect class={`seat ${cls}`} data-testid={`seat-${c.r}-${c.b}-${c.s}`} data-state={cls}
                      x={c.x} y={c.y} width={SEAT} height={SEAT} rx="5"
                      role="button" tabindex="0"
                      oncontextmenu={(e) => onSeatAction(e, c.r, c.b, c.s)}>
                  {#if who}<title>{who ? who + ' — clic droit pour libérer' : ''}</title>{/if}
                </rect>
              {/each}
            </svg>
          </div>
        {/if}

        <div class="panel">
          <h2>Demandes d'aide</h2>
          {#if helps.length}
            <ul class="helps">
              {#each helps.slice().sort((a, b) => (a.id > b.id ? 1 : -1)) as h (h.id)}
                <li data-testid="help-item">
                  <span>{h.attendee} — {h.position || '?'} {#if h.message}« {h.message} »{/if} [{h.status}]</span>
                  {#if h.status === 'PENDING'}<button type="button" data-testid="help-claim" onclick={() => claimHelp(h)}>Je viens</button>{/if}
                  <button type="button" data-testid="help-resolve" onclick={() => resolveHelp(h)}>Résolu</button>
                </li>
              {/each}
            </ul>
          {:else}
            <p class="hint" data-testid="no-help">Aucune demande active.</p>
          {/if}
        </div>

        <div class="panel">
          <h2>Pins</h2>
          <form class="pin-add" onsubmit={addPin}>
            <select data-testid="pin-type" bind:value={newPinType}>
              <option>TEXT</option><option>URL</option><option>CODE</option><option>SECRET</option>
              <option>IMAGE</option><option>FILE</option><option>QR</option>
            </select>
            {#if newPinType === 'IMAGE' || newPinType === 'FILE'}
              <input type="file" data-testid="pin-file"
                     accept={newPinType === 'IMAGE' ? 'image/*' : undefined}
                     onchange={(e) => (newPinFile = e.target.files?.[0] || null)} />
            {:else}
              <input data-testid="pin-content" placeholder="Contenu" bind:value={newPinContent} />
            {/if}
            <button type="submit" data-testid="add-pin">Pin</button>
          </form>
          <ul class="pins">
            {#each pins as p, i (p.id)}
              <li data-testid="pin-item" draggable="true"
                  ondragstart={() => onDragStart(i)} ondragover={(e) => e.preventDefault()} ondrop={() => onDrop(i)}>
                {#if p.type === 'IMAGE'}
                  <img class="pin-thumb" src={`/api/attachments/${p.content}`} alt="" />
                {:else if p.type === 'FILE'}
                  <a href={`/api/attachments/${p.content}`} target="_blank" rel="noopener noreferrer">📎 fichier</a>
                {:else if p.type === 'QR'}
                  {#if qrSvg(p.content)}<span class="pin-qr">{@html qrSvg(p.content)}</span>{/if}
                  <a href={p.content} target="_blank" rel="noopener noreferrer">{p.content}</a>
                {:else}
                  <span>[{p.type}] {p.previewTitle || p.content}</span>
                {/if}
                <button type="button" class="ghost" data-testid="delete-pin" onclick={() => deletePin(p.id)}>×</button>
              </li>
            {/each}
          </ul>
        </div>

        <div class="panel">
          <div class="bar">
            <h2>Chat</h2>
            <form class="speaker-name" onsubmit={savePseudo}>
              Mon pseudo
              <input data-testid="speaker-name" placeholder="Speaker" bind:value={pseudoInput} />
              <button type="submit" data-testid="save-pseudo">OK</button>
              {#if me && me.pseudo}<span class="hint" data-testid="my-speaker-pseudo">{me.pseudo}</span>{/if}
            </form>
          </div>
          <ul class="chat-msgs" data-testid="speaker-chat" bind:this={chatBox}>
            {#each chatLog as m (m.id)}
              <li class="cmsg" class:mine={m.mine} class:from-speaker={m.fromSpeaker}>
                <span class="who">{m.fromSpeaker ? '★ ' : ''}{m.author}</span>
                {#if m.body}<span class="text">{m.body}</span>{/if}
                {#if m.attachmentId}
                  {#if m.attachmentKind === 'image'}
                    <a href={`/api/attachments/${m.attachmentId}`} target="_blank" rel="noopener noreferrer">
                      <img class="chat-img" src={`/api/attachments/${m.attachmentId}`} alt={m.attachmentName || ''} />
                    </a>
                  {:else}
                    <a class="chat-file" href={`/api/attachments/${m.attachmentId}`}
                       target="_blank" rel="noopener noreferrer">📎 {m.attachmentName || 'fichier'}</a>
                  {/if}
                {/if}
              </li>
            {/each}
            {#if !chatLog.length}<li class="hint empty">Aucun message pour l'instant.</li>{/if}
          </ul>
          <form class="chat-form" onsubmit={sendChat}>
            <label class="attach-btn" title="Joindre un fichier" aria-label="Joindre un fichier">📎
              <input type="file" data-testid="speaker-chat-file" onchange={pickChatFile} hidden />
            </label>
            <input data-testid="speaker-chat-input" autocomplete="off" maxlength="500"
                   placeholder="Message au public…" bind:value={chatInput} />
            <button type="submit" data-testid="speaker-chat-send" disabled={!chatInput.trim()}>Envoyer</button>
          </form>
        </div>

        {#if room.owned}
          <div class="panel">
            <h2>Co-speakers</h2>
            <form class="pin-add" onsubmit={addManager}>
              <input data-testid="invite-pseudo" placeholder="pseudo#nnn" bind:value={invitePseudo} />
              <button type="submit" data-testid="invite-manager">Inviter</button>
            </form>
            <ul class="people">
              {#each managersList as mgr (mgr.email)}
                <li data-testid="manager-item">
                  <span>{mgr.displayName || mgr.email}</span>
                  <button type="button" class="ghost danger" data-testid="remove-manager"
                          onclick={() => removeManager(mgr.email)}>Exclure</button>
                </li>
              {/each}
              {#if !managersList.length}<li class="hint">Aucun co-speaker.</li>{/if}
            </ul>
          </div>
        {/if}

        {#if room.mode !== 'LAB'}
          <div class="panel">
            <h2>Slides (reveal)</h2>
            {#if !revealInfo}
              <button type="button" data-testid="reveal-enable" onclick={enableReveal}>Activer le pilotage</button>
            {:else}
              <p class="hint">Deck : <code>/reveal-demo?aragoRoom={room.pin}&amp;aragoSecret={revealInfo.secret}</code></p>
              <div class="bar">
                <button type="button" data-testid="reveal-prev" onclick={() => revealCmd('prev')}>◀ Précédent</button>
                <button type="button" data-testid="reveal-next" onclick={() => revealCmd('next')}>Suivant ▶</button>
                <span data-testid="reveal-state">Slide {revealState ?? '—'}</span>
              </div>
            {/if}
          </div>
        {/if}

        <div class="panel">
          <h2>Historique &amp; export</h2>
          {#if stats}
            <p class="hint" data-testid="room-stats">
              {stats.messages} messages · {stats.persistentMessages} persistants ·
              {stats.helpTotal} aides ({stats.helpResolved} résolues) · {stats.attendees} participants
            </p>
          {/if}
          <div class="bar">
            <button type="button" class="ghost" data-testid="export-chat"
                    onclick={() => download(`/api/rooms/${room.id}/chat/export.md`, `chat-${room.pin}.md`)}>Exporter le chat (.md)</button>
            <button type="button" class="ghost" data-testid="export-help"
                    onclick={() => download(`/api/rooms/${room.id}/help/export.csv`, `help-${room.pin}.csv`)}>Exporter l'aide (.csv)</button>
          </div>
        </div>

        {#if Object.keys(people).length}
          <div class="panel">
            <h2>Participants</h2>
            <ul class="people">
              {#each Object.keys(people) as pseudo}
                <li data-testid="person">
                  <span>{pseudo}{#if muted[pseudo]} (muet){/if}</span>
                  {#if muted[pseudo]}
                    <button type="button" class="ghost" data-testid="unmute" onclick={() => mod('unmute', pseudo)}>Démuter</button>
                  {:else}
                    <button type="button" class="ghost" data-testid="mute" onclick={() => mod('mute', pseudo)}>Muter</button>
                  {/if}
                  <button type="button" class="danger" data-testid="kick" onclick={() => mod('kick', pseudo)}>Exclure</button>
                </li>
              {/each}
            </ul>
          </div>
        {/if}
      </section>
    {/if}
  {/if}

  <footer><a href="/privacy.html">Confidentialité</a></footer>

  {#if pendingDelete}
    <div class="modal-overlay" role="dialog" aria-modal="true" data-testid="delete-dialog">
      <div class="modal">
        <h3>Supprimer la room ?</h3>
        <p>« {pendingDelete.title} » et tout son contenu (chat, pins, demandes d'aide, sièges,
          fichiers) seront <strong>définitivement</strong> effacés.</p>
        <div class="modal-actions">
          <button type="button" class="ghost" data-testid="delete-cancel"
                  onclick={() => (pendingDelete = null)}>Annuler</button>
          <button type="button" class="danger" data-testid="delete-confirm"
                  onclick={confirmDelete}>Supprimer définitivement</button>
        </div>
      </div>
    </div>
  {/if}
</main>

<style>
  main { max-width: 52rem; margin: 0 auto; padding: 2rem 1.25rem; display: flex; flex-direction: column; gap: 1.2rem; min-height: 100vh; }
  header { text-align: center; }
  h1 { margin: 0; font-size: 2.4rem; letter-spacing: 0.04em; color: var(--arago-bordeaux); }
  .tagline { margin: 0.2rem 0 0; color: var(--arago-gold); font-style: italic; }
  h2 { font-size: 1.05rem; color: var(--arago-bordeaux); margin: 0.2rem 0 0.5rem; }
  .card { background: rgba(255,255,255,0.45); border: 1px solid var(--arago-gold); border-radius: 0.75rem; padding: 1.2rem; display: flex; flex-direction: column; gap: 0.6rem; }
  .speaker-id { font-weight: 700; color: var(--arago-bordeaux); text-align: center; margin: 0; }
  .ok { text-align: center; color: var(--arago-bordeaux); font-weight: 700; margin: 0; }
  input, select { font: inherit; padding: 0.5rem 0.6rem; border: 2px solid var(--arago-bordeaux); border-radius: 0.5rem; background: var(--arago-cream); color: var(--arago-ink); }
  label { display: flex; gap: 0.5rem; align-items: center; font-weight: 600; }
  button { font: inherit; font-weight: 700; padding: 0.5rem 0.9rem; border: none; border-radius: 0.5rem; background: var(--arago-bordeaux); color: var(--arago-cream); cursor: pointer; }
  button:disabled { background: var(--arago-paper); color: rgba(26,20,16,0.4); cursor: not-allowed; }
  button.ghost { background: transparent; color: var(--arago-bordeaux); border: 1px solid var(--arago-bordeaux); }
  button.danger { background: var(--arago-danger); }
  button.link { background: transparent; color: var(--arago-bordeaux); border: none; text-decoration: underline; cursor: pointer; padding: 0; font-weight: 700; }
  .rooms, .helps, .pins, .people { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.4rem; }
  .rooms li, .helps li, .pins li, .people li { display: flex; gap: 0.6rem; align-items: center; flex-wrap: wrap; }
  .meta { font-size: 0.8rem; color: rgba(26,20,16,0.6); }
  .bar { display: flex; gap: 0.8rem; align-items: center; flex-wrap: wrap; }
  .panel { border-top: 1px solid var(--arago-gold); padding-top: 0.8rem; }
  .chat-msgs {
    list-style: none; margin: 0 0 0.5rem; padding: 0.5rem;
    display: flex; flex-direction: column; gap: 0.35rem;
    background: var(--arago-surface); border: 1px solid var(--arago-line);
    border-radius: 0.5rem; height: clamp(10rem, 30vh, 22rem); overflow-y: auto;
  }
  .cmsg { display: flex; flex-direction: column; max-width: 85%; padding: 0.25rem 0.5rem;
    border-radius: 0.45rem; background: var(--arago-paper); align-self: flex-start; }
  .cmsg.mine { align-self: flex-end; background: var(--arago-bordeaux); color: var(--arago-cream); }
  .cmsg.from-speaker { align-self: flex-end; background: var(--arago-gold); color: var(--arago-ink); }
  .cmsg .who { font-size: 0.68rem; font-weight: 700; opacity: 0.85; }
  .cmsg .text { white-space: pre-wrap; word-break: break-word; }
  .chat-msgs .empty { background: none; align-self: center; }
  .chat-form { display: flex; gap: 0.5rem; align-items: center; }
  .chat-form > input { flex: 1; min-width: 0; }
  .speaker-name { font-size: 0.8rem; display: flex; gap: 0.3rem; align-items: center; }
  .speaker-name input { width: 8rem; }
  .attach-btn {
    flex-shrink: 0; cursor: pointer; font-size: 1.15rem; line-height: 1;
    padding: 0.3rem 0.45rem; border: 1px solid var(--arago-bordeaux); border-radius: 0.45rem;
  }
  .cmsg .chat-img { max-width: 10rem; max-height: 10rem; border-radius: 0.35rem; display: block; margin-top: 0.2rem; }
  .cmsg .chat-file { color: inherit; text-decoration: underline; word-break: break-word; }
  .pin-add { display: flex; gap: 0.5rem; flex-wrap: wrap; }
  .pins li { cursor: grab; }
  .pin-thumb { width: 3rem; height: 3rem; object-fit: cover; border-radius: 0.3rem; }
  /* Ghost (outline) danger button: transparent bg so the red text stays readable (override button.danger). */
  button.ghost.danger { background: transparent; color: var(--arago-danger); border-color: var(--arago-danger); }
  .pin-qr { flex-shrink: 0; background: #fff; padding: 0.25rem; border-radius: 0.3rem; }
  .pin-qr :global(svg) { display: block; }
  .modal-overlay {
    position: fixed; inset: 0; background: rgba(26,20,16,0.55);
    display: flex; align-items: center; justify-content: center; z-index: 50; padding: 1rem;
  }
  .modal {
    background: var(--arago-cream); color: var(--arago-ink); border: 1px solid var(--arago-gold);
    border-radius: 0.75rem; padding: 1.5rem; max-width: 28rem; width: 100%;
    box-shadow: 0 12px 40px rgba(26,20,16,0.35);
  }
  .modal h3 { margin: 0 0 0.6rem; color: var(--arago-bordeaux); }
  .modal p { margin: 0 0 1.2rem; }
  .modal-actions { display: flex; gap: 0.6rem; justify-content: flex-end; }
  .hint { margin: 0; font-size: 0.85rem; color: rgba(26,20,16,0.7); }
  .error { margin: 0; color: var(--arago-danger); font-weight: 600; }
  .map { max-width: 100%; height: auto; background: rgba(255,255,255,0.4); border: 1px solid var(--arago-gold); border-radius: 0.6rem; }
  .stage { fill: var(--arago-ink); opacity: 0.85; }
  .stage-txt { fill: var(--arago-cream); font-size: 12px; letter-spacing: 0.2em; }
  .seat { stroke: rgba(26,20,16,0.25); stroke-width: 1; cursor: pointer; }
  .seat.free { fill: var(--arago-cream); }
  .seat.occupied { fill: url(#hatch); stroke: var(--arago-bordeaux); }
  .seat.blocked { fill: #b9b2a3; }
  .seat.help { fill: var(--arago-gold); stroke: var(--arago-bordeaux); stroke-width: 2; }
  footer { margin-top: auto; text-align: center; font-size: 0.85rem; }
  footer a { color: var(--arago-bordeaux); }
</style>
