// Speaker session — local email + password auth (replaces the former Keycloak/OIDC flow).
//
// The token + resolved identity live in sessionStorage, NOT localStorage: sessionStorage is scoped to
// a single tab and survives a page reload, so a page refresh keeps the speaker logged in while several
// tabs can hold DIFFERENT identities at once (handy to test 2 speakers + 2 attendees side by side).
const KEY = 'arago.speaker';

/** The persisted session ({ token, me }) for this tab, or null. */
export function loadSpeakerSession() {
  try {
    const s = JSON.parse(sessionStorage.getItem(KEY));
    return (s && s.token && s.me) ? s : null;
  } catch {
    return null;
  }
}

export function saveSpeakerSession(token, me) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ token, me }));
  } catch {
    /* ignore (private mode / quota) */
  }
}

export function clearSpeakerSession() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

/**
 * POST /api/speaker/login → resolves to { token, me } and persists the session, or throws an Error
 * whose message is a code ('not_configured' | 'rate_limited' | 'invalid_credentials').
 */
export async function loginSpeaker(email, password) {
  const res = await fetch('/api/speaker/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (res.status === 503) throw new Error('not_configured');
  if (res.status === 429) throw new Error('rate_limited');
  if (!res.ok) throw new Error('invalid_credentials');
  const data = await res.json(); // { token, me }
  saveSpeakerSession(data.token, data.me);
  return data;
}
