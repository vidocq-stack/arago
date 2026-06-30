<script>
  import { onMount } from 'svelte';
  import Footer from './lib/Footer.svelte';
  import { t, applyLang } from './lib/i18n.svelte.js';

  // Public projector / lobby screen (arago-spec §4.1): no auth, opened from the speaker console
  // ("Afficher"). Shows the room title + PIN to join + live headcount, the pinned content (small), and
  // the global chat (auto-scrolling). Polls GET /api/rooms/lobby/{pin} (headcount) and …/feed
  // (chat + pins, public, no private DM, no SECRET pin). The PIN is the public join credential.
  const pin = new URLSearchParams(location.search).get('pin') || '';
  let title = $state('');
  let attendees = $state(0);
  let status = $state('');
  let error = $state('');     // '' | 'notfound'
  let chat = $state([]);      // [{id, author, fromSpeaker, body, at}]
  let pins = $state([]);      // [{id, type, content, previewTitle, previewImage}]
  let chatBox = $state(null);

  async function refresh() {
    if (!pin) { error = 'notfound'; return; }
    try {
      const res = await fetch(`/api/rooms/lobby/${encodeURIComponent(pin)}`);
      if (res.status === 404) { error = 'notfound'; return; }
      if (res.ok) {
        const d = await res.json();
        title = d.title; attendees = d.attendees; status = d.status; error = '';
      }
      const fr = await fetch(`/api/rooms/lobby/${encodeURIComponent(pin)}/feed`);
      if (fr.ok) {
        const f = await fr.json();
        chat = f.chat || [];
        pins = f.pins || [];
      }
    } catch {
      /* transient network error: keep the last good values rather than blanking the projection */
    }
  }

  // Auto-scroll the chat to the newest message on each refresh.
  $effect(() => {
    chat.length;
    if (chatBox) chatBox.scrollTop = chatBox.scrollHeight;
  });

  onMount(() => {
    applyLang();
    refresh();
    const id = setInterval(refresh, 2500);
    return () => clearInterval(id);
  });
</script>

<main class="display">
  {#if error === 'notfound'}
    <p class="msg" data-testid="display-error">{t('display.notfound')}</p>
  {:else}
    <div class="head">
      <h1 class="title" data-testid="display-title">{title}</h1>
      {#if status === 'ENDED'}
        <p class="msg" data-testid="display-ended">{t('display.ended')}</p>
      {/if}
      <div class="meta">
        <span class="pin-label">{t('display.pin')}</span>
        <span class="pin" data-testid="display-pin">{pin}</span>
        <span class="count"><span class="num" data-testid="display-count">{attendees}</span> {t('display.attendees')}</span>
      </div>
    </div>

    {#if pins.length}
      <ul class="pins" data-testid="display-pins">
        {#each pins as p (p.id)}
          <li class="pin-item">
            {#if p.previewImage}<img class="pin-thumb" src={p.previewImage} alt="" />{/if}
            <span class="pin-text">{p.previewTitle || p.content}</span>
          </li>
        {/each}
      </ul>
    {/if}

    <ul class="chatfeed" data-testid="display-chat" bind:this={chatBox}>
      {#each chat as m (m.id)}
        <li class="dmsg" class:from-speaker={m.fromSpeaker}>
          <span class="who">{m.fromSpeaker ? '★ ' : ''}{m.author}</span>
          <span class="text">{m.body}</span>
        </li>
      {/each}
    </ul>
  {/if}
  <Footer />
</main>

<style>
  .display {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 2vh;
    text-align: center;
    padding: 3vh 4vw;
    color: var(--arago-ink);
  }
  .head { display: flex; flex-direction: column; align-items: center; gap: 1vh; }
  .title { font-size: clamp(1.8rem, 4.5vw, 4rem); margin: 0; max-width: 92vw; }
  .meta { display: flex; align-items: baseline; gap: 1.2rem; flex-wrap: wrap; justify-content: center; }
  .pin-label {
    font-size: clamp(0.8rem, 1.6vw, 1.4rem); color: var(--arago-muted);
    text-transform: uppercase; letter-spacing: 0.25em;
  }
  /* PIN code: smaller than before so the chat fits below. */
  .pin {
    font-size: clamp(2.5rem, 9vw, 7rem); font-weight: 800; letter-spacing: 0.12em; line-height: 1;
    color: var(--arago-bordeaux); font-variant-numeric: tabular-nums;
  }
  .count { font-size: clamp(1rem, 2.2vw, 1.8rem); color: var(--arago-muted); }
  .count .num { font-weight: 800; color: var(--arago-ink); font-variant-numeric: tabular-nums; }

  /* Pinned content, compact row. */
  .pins { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 0.6rem; justify-content: center; }
  .pin-item {
    display: flex; align-items: center; gap: 0.5rem; max-width: 26vw;
    background: var(--arago-surface); border: 1px solid var(--arago-gold);
    border-radius: 0.5rem; padding: 0.4rem 0.7rem; font-size: clamp(0.85rem, 1.6vw, 1.2rem);
  }
  .pin-thumb { width: 2.2rem; height: 2.2rem; object-fit: cover; border-radius: 0.3rem; flex-shrink: 0; }
  .pin-text { font-weight: 600; color: var(--arago-bordeaux); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

  /* Global chat feed: fills the remaining height, scrolls to newest. */
  .chatfeed {
    list-style: none; margin: 0; padding: 1rem; flex: 1 1 auto; overflow-y: auto; text-align: left;
    display: flex; flex-direction: column; gap: 0.5rem;
    background: var(--arago-surface); border: 1px solid var(--arago-line); border-radius: 0.6rem;
  }
  .dmsg { font-size: clamp(1rem, 2vw, 1.6rem); }
  .dmsg .who { font-weight: 800; color: var(--arago-bordeaux); margin-right: 0.5rem; }
  .dmsg.from-speaker .who { color: var(--arago-gold); }
  .dmsg .text { white-space: pre-wrap; word-break: break-word; }
  .msg { font-size: clamp(1.2rem, 3.5vw, 2.5rem); color: var(--arago-muted); }
</style>
