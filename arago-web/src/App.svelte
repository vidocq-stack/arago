<script>
  import { onMount } from 'svelte';
  import Prefs from './lib/Prefs.svelte';
  import { t } from './lib/i18n.svelte.js';
  // Attendee SPA (arago-spec §4.2/§4.5): join a room by PIN + pseudo, then — in a LAB room — see the
  // top-down seating plan, tap a free seat to sit (first-come-first-serve, server-authoritative), and
  // raise a "need help" request. All real-time state flows over the room WebSocket (RoomSocket);
  // the browser can't set the Authorization header on a handshake, so the attendee token rides ?token=.
  let view = $state('join'); // 'join' | 'room'

  // --- join inputs ---
  let pin = $state('');
  let pseudo = $state('');
  let joinError = $state('');
  const pinDigits = $derived(pin.replace(/\D/g, '').slice(0, 6));
  const canJoin = $derived(pinDigits.length === 6 && pseudo.trim().length > 0);

  // --- speaker OIDC (front-channel, arago-spec §7/§8) ---
  // The speaker logs in through Keycloak (Authorization Code + PKCE, server-driven). The callback
  // hands back a one-time ticket cookie; we exchange it once for the Keycloak access token, kept in
  // memory and sent as Authorization: Bearer (cervantes validates it). The token never rides a URL.
  let speaker = $state(null);        // { email, role } once logged in
  let speakerToken = $state(null);   // Keycloak access token (in-memory only)
  let oidcError = $state('');

  // OIDC callback error codes mapped to translation keys (resolved at render time so a late
  // language switch still re-labels the message).
  const OIDC_ERROR_KEYS = {
    speaker_not_provisioned: 'oidc.speaker_not_provisioned',
    invalid_state: 'oidc.invalid_state',
    exchange_failed: 'oidc.exchange_failed',
    oidc_not_configured: 'oidc.not_configured',
  };

  function loginAsSpeaker() {
    // Speakers belong on the speaker console, not back on the attendee/join page: land there after the
    // Keycloak round-trip (Speaker.svelte then exchanges the one-time ticket and shows the console).
    window.location.assign('/api/oidc/login?return=/speaker');
  }

  async function completeOidcLogin() {
    const params = new URLSearchParams(window.location.search);
    const loggedIn = params.get('login') === 'ok';
    const err = params.get('oidc_error');
    if (!loggedIn && !err) return;
    // Strip the OIDC query params so a refresh doesn't replay them.
    window.history.replaceState({}, '', window.location.pathname);
    if (err) {
      oidcError = OIDC_ERROR_KEYS[err] || 'oidc.generic';
      return;
    }
    try {
      const res = await fetch('/api/oidc/token', { method: 'POST' });
      if (!res.ok) { oidcError = 'oidc.generic'; return; }
      const session = await res.json();
      speakerToken = session.accessToken;
      // Prove the token is accepted end-to-end (and resolve the identity) via /api/oidc/me.
      const me = await fetch('/api/oidc/me', { headers: { Authorization: `Bearer ${speakerToken}` } });
      if (!me.ok) { oidcError = 'oidc.generic'; return; }
      speaker = await me.json();
    } catch {
      oidcError = 'oidc.generic';
    }
  }

  onMount(completeOidcLogin);

  // --- room state ---
  let token = $state(null);
  let roomMode = $state(null);
  let myPseudo = $state('');
  let ws = $state(null);
  let layout = $state(null);          // {rows, blocks:[{size,label}], stagePos, rowLabels, blockedSeats:[{row,block,seat}]}
  let seats = $state({});             // "r-b-s" -> occupant pseudo
  let mySeat = $state(null);          // {row,block,seat} | null
  let myHelp = $state(null);          // null | 'PENDING' | 'CLAIMED' | 'RESOLVED' | 'CANCELLED'
  let notice = $state('');            // transient message (e.g. seat taken)
  let revealFollow = $state(null);    // "H.V" current slide when the speaker drives a reveal deck (§4.6)

  // --- chat (§4.3): all room peers exchange messages over the same WebSocket. ---
  let chat = $state([]);              // [{id, author, body, fromSpeaker, mine}]
  let chatInput = $state('');
  let chatBox = $state(null);         // scroll container ref (auto-scroll to newest)

  const seatKey = (r, b, s) => `${r}-${b}-${s}`;

  async function join(e) {
    e?.preventDefault();
    joinError = '';
    try {
      const res = await fetch('/api/rooms/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin: pinDigits, pseudo: pseudo.trim() }),
      });
      if (!res.ok) {
        joinError = res.status === 404 ? 'join.notfound' : 'join.failed';
        return;
      }
      const data = await res.json();
      token = data.token;
      roomMode = data.mode;
      myPseudo = pseudo.trim();
      connect();
      view = 'room';
    } catch {
      joinError = 'join.failed';
    }
  }

  function connect() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const url = `${proto}://${location.host}/ws/rooms/${pinDigits}?token=${encodeURIComponent(token)}`;
    const socket = new WebSocket(url);
    socket.onmessage = (ev) => onFrame(ev.data);
    ws = socket;
  }

  function onFrame(raw) {
    let m;
    try { m = JSON.parse(raw); } catch { return; }
    switch (m.type) {
      case 'layout': layout = m.layout; break;
      case 'seat': onSeat(m); break;
      case 'help': onHelp(m); break;
      case 'reveal.state': revealFollow = `${m.indexh}.${m.indexv}`; break;
      case 'chat': onChat(m); break;
      // pin frames are not surfaced in the attendee view.
    }
  }

  function onChat(m) {
    chat = [...chat, {
      id: m.id || `${Date.now()}-${chat.length}`,
      author: m.author || '?',
      body: m.body || '',
      fromSpeaker: !!m.fromSpeaker,
      mine: !m.fromSpeaker && m.author === myPseudo,
    }].slice(-200); // keep the tail bounded for a long-running room
  }

  function sendChat(e) {
    e?.preventDefault();
    const body = chatInput.trim();
    if (!body || !ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(JSON.stringify({ body }));
    chatInput = '';
  }

  // Auto-scroll the chat to the newest message whenever the list grows.
  $effect(() => {
    chat.length;
    if (chatBox) chatBox.scrollTop = chatBox.scrollHeight;
  });

  // Short zero-dependency notification beep (WebAudio). The browser unlocks audio after the user's
  // join click, so the context can resume by the time an event fires.
  let audioCtx = null;
  function beep(freq = 880, ms = 180) {
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

  function onSeat(m) {
    const key = seatKey(m.row, m.block, m.seat);
    if (m.action === 'taken') {
      seats = { ...seats, [key]: m.attendee };
      if (m.attendee === myPseudo) {
        mySeat = { row: m.row, block: m.block, seat: m.seat };
        notice = '';
      }
    } else if (m.action === 'free') {
      const next = { ...seats };
      delete next[key];
      seats = next;
      if (mySeat && key === seatKey(mySeat.row, mySeat.block, mySeat.seat)) {
        mySeat = null;
      }
    } else if (m.action === 'rejected' && m.attendee === myPseudo) {
      notice = m.reason === 'seat-taken' ? 'seat.taken'
             : m.reason === 'invalid-seat' ? 'seat.invalid'
             : 'seat.unavailable';
    }
  }

  function onHelp(m) {
    if (m.attendee === myPseudo) {
      const was = myHelp;
      myHelp = m.status;
      // The speaker is on the way: chime so the attendee notices even if looking away.
      if (m.status === 'CLAIMED' && was !== 'CLAIMED') beep(660, 220);
    }
  }

  function claim(r, b, s) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    if (isOccupied(r, b, s) || isBlocked(r, b, s)) return;
    notice = '';
    ws.send(JSON.stringify({ type: 'seat', row: r, block: b, seat: s }));
  }

  function raiseHelp() {
    ws?.send(JSON.stringify({ type: 'help' }));
  }

  function cancelHelp() {
    ws?.send(JSON.stringify({ type: 'help-cancel' }));
  }

  const isBlocked = (r, b, s) =>
    !!layout?.blockedSeats?.some((x) => x.row === r && x.block === b && x.seat === s);
  const isOccupied = (r, b, s) => !!seats[seatKey(r, b, s)];
  const isMine = (r, b, s) =>
    mySeat && mySeat.row === r && mySeat.block === b && mySeat.seat === s;

  const helpActive = $derived(myHelp === 'PENDING' || myHelp === 'CLAIMED');

  function rowLabel(r) {
    if (layout?.rowLabels === 'ALPHA') {
      return String.fromCharCode(65 + r);
    }
    return String(r + 1);
  }

  // --- top-down geometry (cf. arago-spec §4.5) ---
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
    const rowYs = Array.from({ length: layout.rows }, (_, r) => top + r * (SEAT + RGAP));
    const blockLabels = blocks.map((b, bi) => ({
      label: b.label || `B${bi + 1}`, x: blockX[bi] + blockWidth(b) / 2,
    }));
    return { width, height, cells, rowYs, blockLabels, stageW: width - 2 * PAD };
  });

  function seatState(r, b, s) {
    if (isMine(r, b, s)) return helpActive ? 'mine-help' : 'mine';
    if (isBlocked(r, b, s)) return 'blocked';
    if (isOccupied(r, b, s)) return 'occupied';
    return 'free';
  }
</script>

<main>
  <header>
    <Prefs />
    <h1>Arago</h1>
    <p class="tagline">{t('tagline')}</p>
  </header>

  {#if view === 'join'}
    <form class="join-card" onsubmit={join} data-testid="join-form">
      <label for="pin">{t('join.pin')}</label>
      <input id="pin" data-testid="join-pin" inputmode="numeric" autocomplete="off"
             placeholder="123456" maxlength="6" bind:value={pin} />
      <label for="pseudo">{t('join.pseudo')}</label>
      <input id="pseudo" data-testid="join-pseudo" autocomplete="off" placeholder="Ada"
             bind:value={pseudo} />
      <button type="submit" data-testid="join-submit" disabled={!canJoin}>{t('join.submit')}</button>
      {#if joinError}<p class="error" data-testid="join-error">{t(joinError)}</p>{/if}
      <p class="hint">{t('join.hint')}</p>
    </form>

    <div class="speaker-box" data-testid="speaker-box">
      {#if speaker}
        <p class="speaker-id" data-testid="speaker-identity">
          {t('speaker.connected', { email: speaker.email, role: speaker.role })}
        </p>
      {:else}
        <p class="hint">{t('speaker.are-you')}</p>
        <button type="button" class="ghost" data-testid="speaker-login" onclick={loginAsSpeaker}>
          {t('speaker.login')}
        </button>
        {#if oidcError}<p class="error" data-testid="oidc-error">{t(oidcError)}</p>{/if}
      {/if}
    </div>
  {:else}
    <section class="room" data-testid="room">
      <div class="bar">
        <span class="mode">{roomMode === 'LAB' || roomMode === 'HYBRID' ? t('room.workshop') : t('room.conference')}</span>
        {#if mySeat}
          <span class="seatlbl" data-testid="my-seat-label">
            {t('room.seat', {
              row: mySeat.row + 1,
              block: layout?.blocks?.[mySeat.block]?.label || 'B' + (mySeat.block + 1),
              seat: mySeat.seat + 1,
            })}
          </span>
        {:else if geom}
          <span class="seatlbl" data-testid="my-seat-label">{t('room.pick-seat')}</span>
        {/if}
      </div>

      {#if notice}<p class="notice" data-testid="notice">{t(notice)}</p>{/if}
      {#if revealFollow}<p class="follow" data-testid="reveal-follow">{t('room.reveal-follow', { slide: revealFollow })}</p>{/if}

      <div class="room-body" class:two-col={!!geom}>
        {#if geom}
          <div class="seat-col">
            <svg class="map" data-testid="seating-map" viewBox={`0 0 ${geom.width} ${geom.height}`}
                 width={geom.width} height={geom.height} role="group" aria-label={t('room.map-aria')}>
              <defs>
                <pattern id="hatch" width="7" height="7" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
                  <rect width="7" height="7" fill="var(--arago-paper)" />
                  <line x1="0" y1="0" x2="0" y2="7" stroke="var(--arago-bordeaux)" stroke-width="2.5" opacity="0.6" />
                </pattern>
              </defs>
              <rect class="stage" x={PAD} y={PAD} width={geom.stageW} height={STAGE} rx="4" />
              <text class="stage-txt" x={geom.width / 2} y={PAD + STAGE / 2 + 4} text-anchor="middle">{t('room.stage')}</text>

              {#each geom.blockLabels as bl}
                <text class="block-lbl" x={bl.x} y={PAD + STAGE + 8} text-anchor="middle">{bl.label}</text>
              {/each}
              {#each geom.rowYs as y, r}
                <text class="row-lbl" x={PAD} y={y + SEAT / 2 + 4}>{rowLabel(r)}</text>
              {/each}

              {#each geom.cells as c (c.r + '-' + c.b + '-' + c.s)}
                {@const st = seatState(c.r, c.b, c.s)}
                {@const who = seats[seatKey(c.r, c.b, c.s)]}
                <rect class={`seat ${st}`} data-testid={`seat-${c.r}-${c.b}-${c.s}`} data-state={st}
                      x={c.x} y={c.y} width={SEAT} height={SEAT} rx="5"
                      role="button" tabindex="0"
                      aria-label={who ? `R${c.r + 1} S${c.s + 1} — ${who}` : `R${c.r + 1} S${c.s + 1}`}
                      onclick={() => claim(c.r, c.b, c.s)}
                      onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && claim(c.r, c.b, c.s)}>
                  {#if who}<title>{who}</title>{/if}
                </rect>
              {/each}
            </svg>

            <div class="legend">
              <span><i class="sw free"></i> {t('legend.free')}</span>
              <span><i class="sw occupied"></i> {t('legend.occupied')}</span>
              <span><i class="sw mine"></i> {t('legend.mine')}</span>
              <span><i class="sw blocked"></i> {t('legend.blocked')}</span>
            </div>

            <div class="help">
              {#if helpActive}
                <span class="help-status" data-testid="help-status">
                  {myHelp === 'CLAIMED' ? t('help.coming') : t('help.requested')}
                </span>
                <button class="ghost" data-testid="help-cancel" onclick={cancelHelp}>{t('help.cancel')}</button>
              {:else}
                <button class="help-btn" data-testid="help-button" onclick={raiseHelp}>{t('help.need')}</button>
              {/if}
            </div>
          </div>
        {/if}

        <div class="chat-col">
          <h2 class="chat-title">{t('chat.title')}</h2>
          <ul class="msgs" data-testid="chat-messages" bind:this={chatBox}>
            {#each chat as m (m.id)}
              <li class="msg" class:mine={m.mine} class:from-speaker={m.fromSpeaker}>
                <span class="who">{m.fromSpeaker ? '★ ' : ''}{m.author}</span>
                <span class="text">{m.body}</span>
              </li>
            {/each}
            {#if !chat.length}<li class="empty hint">{t('chat.empty')}</li>{/if}
          </ul>
          <form class="chat-form" onsubmit={sendChat}>
            <input data-testid="chat-input" autocomplete="off" maxlength="500"
                   placeholder={t('chat.placeholder')} bind:value={chatInput} />
            <button type="submit" data-testid="chat-send" disabled={!chatInput.trim()}>{t('chat.send')}</button>
          </form>
        </div>
      </div>
    </section>
  {/if}

  <footer>
    <a href="/privacy.html">{t('footer.privacy')}</a>
  </footer>
</main>

<style>
  main {
    max-width: 64rem;
    margin: 0 auto;
    padding: 2.5rem 1.25rem;
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
    min-height: 100vh;
  }
  header { text-align: center; }
  h1 { margin: 0; font-size: 2.6rem; letter-spacing: 0.04em; color: var(--arago-bordeaux); }
  .tagline { margin: 0.2rem 0 0; color: var(--arago-gold); font-style: italic; }

  .join-card {
    background: var(--arago-surface);
    border: 1px solid var(--arago-gold);
    border-radius: 0.75rem;
    padding: 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.7rem;
    box-shadow: 0 8px 24px rgba(26, 20, 16, 0.12);
    width: 100%;
    max-width: 26rem;
    align-self: center;
  }
  label { font-weight: 600; }
  input {
    font: inherit; padding: 0.55rem 0.6rem;
    border: 2px solid var(--arago-bordeaux); border-radius: 0.5rem;
    background: var(--arago-cream); color: var(--arago-ink);
  }
  #pin { font-size: 1.8rem; letter-spacing: 0.4em; text-align: center; }
  button {
    font: inherit; font-weight: 700; padding: 0.7rem 1rem; border: none;
    border-radius: 0.5rem; background: var(--arago-bordeaux); color: var(--arago-cream); cursor: pointer;
  }
  button:disabled { background: var(--arago-paper); color: var(--arago-muted); cursor: not-allowed; }
  .hint { margin: 0; font-size: 0.85rem; color: var(--arago-muted); }
  .error { margin: 0; color: var(--arago-danger); font-weight: 600; }

  .speaker-box {
    display: flex; flex-direction: column; gap: 0.5rem; align-items: center;
    text-align: center; padding-top: 0.5rem;
  }
  .speaker-id { margin: 0; font-weight: 700; color: var(--arago-bordeaux); }

  .room { display: flex; flex-direction: column; gap: 0.9rem; align-items: stretch; }
  .bar { width: 100%; display: flex; justify-content: space-between; align-items: center; }
  .mode {
    background: var(--arago-bordeaux); color: var(--arago-cream);
    padding: 0.15rem 0.7rem; border-radius: 999px; font-size: 0.85rem;
  }
  .seatlbl { font-weight: 600; }
  .notice {
    margin: 0; align-self: stretch; text-align: center;
    background: var(--arago-warn); color: var(--arago-cream);
    padding: 0.4rem 0.7rem; border-radius: 0.4rem;
  }

  .map {
    max-width: 100%; height: auto;
    background: var(--arago-surface);
    border: 1px solid var(--arago-gold); border-radius: 0.6rem;
  }
  .stage { fill: var(--arago-ink); opacity: 0.85; }
  .stage-txt { fill: var(--arago-cream); font-size: 12px; letter-spacing: 0.2em; }
  .block-lbl { fill: var(--arago-bordeaux); font-size: 11px; font-weight: 700; }
  .row-lbl { fill: var(--arago-muted); font-size: 12px; }

  .seat { stroke: var(--arago-line); stroke-width: 1; cursor: pointer; }
  .seat.free { fill: var(--arago-cream); }
  .seat.free:hover { fill: var(--arago-paper); }
  .seat.occupied { fill: url(#hatch); stroke: var(--arago-bordeaux); cursor: not-allowed; }
  .seat.blocked { fill: #b9b2a3; cursor: not-allowed; }
  .seat.mine { fill: var(--arago-bordeaux); stroke: var(--arago-ink); }
  .seat.mine-help { fill: var(--arago-gold); stroke: var(--arago-bordeaux); stroke-width: 2; }

  .legend { display: flex; gap: 1rem; font-size: 0.8rem; flex-wrap: wrap; }
  .legend span { display: inline-flex; align-items: center; gap: 0.35rem; }
  .sw { width: 0.9rem; height: 0.9rem; border-radius: 3px; border: 1px solid var(--arago-line); }
  .sw.free { background: var(--arago-cream); }
  .sw.occupied {
    background: repeating-linear-gradient(45deg,
      var(--arago-paper), var(--arago-paper) 3px, var(--arago-bordeaux) 3px, var(--arago-bordeaux) 4px);
  }
  .sw.mine { background: var(--arago-bordeaux); }
  .sw.blocked { background: #b9b2a3; }

  .help { display: flex; gap: 0.6rem; align-items: center; }
  .help-btn { background: var(--arago-gold); }
  .help-status { font-weight: 700; color: var(--arago-bordeaux); }
  .ghost { background: transparent; color: var(--arago-bordeaux); border: 1px solid var(--arago-bordeaux); }

  /* Room layout: seat plan aside, chat as the main panel (single column on mobile). */
  .room-body { width: 100%; display: flex; flex-direction: column; gap: 1rem; }
  .room-body.two-col {
    display: grid;
    grid-template-columns: minmax(0, 22rem) 1fr;
    gap: 1.2rem;
    align-items: start;
  }
  @media (max-width: 760px) {
    .room-body.two-col { grid-template-columns: 1fr; }
  }
  .seat-col { display: flex; flex-direction: column; gap: 0.8rem; align-items: center; }

  .chat-col { display: flex; flex-direction: column; gap: 0.5rem; min-width: 0; }
  .chat-title { margin: 0; font-size: 1rem; color: var(--arago-bordeaux); }
  .msgs {
    list-style: none; margin: 0; padding: 0.6rem;
    display: flex; flex-direction: column; gap: 0.4rem;
    background: var(--arago-surface); border: 1px solid var(--arago-line);
    border-radius: 0.6rem; height: clamp(14rem, 48vh, 32rem); overflow-y: auto;
  }
  .msg {
    display: flex; flex-direction: column; max-width: 85%;
    padding: 0.3rem 0.55rem; border-radius: 0.5rem;
    background: var(--arago-paper); align-self: flex-start;
  }
  .msg.mine { align-self: flex-end; background: var(--arago-bordeaux); color: var(--arago-cream); }
  .msg.from-speaker { align-self: center; background: var(--arago-gold); color: var(--arago-ink); }
  .msg .who { font-size: 0.7rem; font-weight: 700; opacity: 0.85; }
  .msg .text { white-space: pre-wrap; word-break: break-word; }
  .empty { background: none; color: var(--arago-muted); align-self: center; }
  .chat-form { display: flex; gap: 0.5rem; }
  .chat-form input { flex: 1; min-width: 0; }

  footer { margin-top: auto; text-align: center; font-size: 0.85rem; }
  footer a { color: var(--arago-bordeaux); }
</style>
