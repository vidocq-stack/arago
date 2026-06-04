// Tiny zero-dependency i18n for the public attendee page (arago-spec §11 Phase 6 — i18n FR/EN).
// Svelte 5 universal reactivity: the module-level $state `lang` is tracked by any component that
// reads it through t(), so a single setLang() re-renders every label. Locale is persisted in
// localStorage and mirrored onto <html lang> for accessibility. No external i18n library.

const DICT = {
  fr: {
    'tagline': 'Speaker & Lab Companion',
    'join.pin': 'Code de la room',
    'join.pseudo': 'Votre pseudo',
    'join.submit': 'Rejoindre',
    'join.hint': "Pas de compte, pas d'installation — juste le PIN affiché à l'écran.",
    'join.notfound': 'Room introuvable ou fermée',
    'join.failed': 'Impossible de rejoindre',
    'speaker.connected': 'Connecté : {email} ({role})',
    'speaker.are-you': 'Vous êtes speaker ?',
    'speaker.login': 'Se connecter (Keycloak)',
    'oidc.speaker_not_provisioned': 'Compte non provisionné — demandez à l’organisateur de vous inviter.',
    'oidc.invalid_state': 'Session de connexion expirée, réessayez.',
    'oidc.exchange_failed': 'Échec de la connexion OIDC, réessayez.',
    'oidc.not_configured': 'La connexion par identité n’est pas configurée sur ce serveur.',
    'oidc.generic': 'Connexion impossible.',
    'room.workshop': 'Atelier',
    'room.conference': 'Conférence',
    'room.seat': 'Place : R{row} · {block} · S{seat}',
    'room.pick-seat': 'Choisissez une place',
    'room.map-aria': 'Plan de la salle',
    'room.stage': 'Scène',
    'room.no-layout': "Cette salle n'a pas de plan (mode conférence).",
    'room.reveal-follow': 'Slide en cours : {slide}',
    'seat.taken': 'Cette place vient d’être prise.',
    'seat.invalid': 'Place invalide.',
    'seat.unavailable': 'Place indisponible.',
    'legend.free': 'libre',
    'legend.occupied': 'occupé',
    'legend.mine': 'vous',
    'legend.blocked': 'indispo.',
    'help.coming': 'Un speaker arrive…',
    'help.requested': 'Aide demandée',
    'help.cancel': 'Annuler',
    'help.need': "Besoin d'aide",
    'footer.privacy': 'Confidentialité',
    'prefs.language': 'Langue',
    'prefs.theme': 'Thème',
    'prefs.theme-toggle': 'Basculer le thème clair / sombre',
    'display.pin': 'Code de la room',
    'display.attendees': 'personnes connectées',
    'display.ended': 'Session terminée',
    'display.notfound': 'Room introuvable',
    'chat.title': 'Chat',
    'chat.empty': 'Aucun message pour l’instant.',
    'chat.placeholder': 'Votre message…',
    'chat.send': 'Envoyer',
  },
  en: {
    'tagline': 'Speaker & Lab Companion',
    'join.pin': 'Room code',
    'join.pseudo': 'Your nickname',
    'join.submit': 'Join',
    'join.hint': 'No account, no install — just the PIN shown on screen.',
    'join.notfound': 'Room not found or closed',
    'join.failed': 'Could not join',
    'speaker.connected': 'Signed in: {email} ({role})',
    'speaker.are-you': 'Are you a speaker?',
    'speaker.login': 'Sign in (Keycloak)',
    'oidc.speaker_not_provisioned': 'Account not provisioned — ask the organiser to invite you.',
    'oidc.invalid_state': 'Login session expired, please retry.',
    'oidc.exchange_failed': 'OIDC sign-in failed, please retry.',
    'oidc.not_configured': 'Identity login is not configured on this server.',
    'oidc.generic': 'Sign-in failed.',
    'room.workshop': 'Workshop',
    'room.conference': 'Conference',
    'room.seat': 'Seat: R{row} · {block} · S{seat}',
    'room.pick-seat': 'Pick a seat',
    'room.map-aria': 'Room map',
    'room.stage': 'Stage',
    'room.no-layout': 'This room has no map (conference mode).',
    'room.reveal-follow': 'Current slide: {slide}',
    'seat.taken': 'That seat was just taken.',
    'seat.invalid': 'Invalid seat.',
    'seat.unavailable': 'Seat unavailable.',
    'legend.free': 'free',
    'legend.occupied': 'occupied',
    'legend.mine': 'you',
    'legend.blocked': 'n/a',
    'help.coming': 'A speaker is on the way…',
    'help.requested': 'Help requested',
    'help.cancel': 'Cancel',
    'help.need': 'Need help',
    'footer.privacy': 'Privacy',
    'prefs.language': 'Language',
    'prefs.theme': 'Theme',
    'prefs.theme-toggle': 'Toggle light / dark theme',
    'display.pin': 'Room code',
    'display.attendees': 'people connected',
    'display.ended': 'Session ended',
    'display.notfound': 'Room not found',
    'chat.title': 'Chat',
    'chat.empty': 'No messages yet.',
    'chat.placeholder': 'Your message…',
    'chat.send': 'Send',
  },
};

const STORAGE_KEY = 'arago.lang';

function detect() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'fr' || saved === 'en') return saved;
  } catch { /* private mode / no storage */ }
  const nav = (typeof navigator !== 'undefined' && navigator.language) || 'fr';
  return nav.toLowerCase().startsWith('en') ? 'en' : 'fr';
}

let lang = $state(detect());

/** Applies the current locale to <html lang> (call once on mount). */
export function applyLang() {
  if (typeof document !== 'undefined') document.documentElement.lang = lang;
}

export function getLang() {
  return lang;
}

export function setLang(next) {
  if (next !== 'fr' && next !== 'en') return;
  lang = next;
  try { localStorage.setItem(STORAGE_KEY, next); } catch { /* ignore */ }
  applyLang();
}

/** Translate `key` for the current locale, interpolating {name} placeholders from `params`. */
export function t(key, params) {
  let s = (DICT[lang] && DICT[lang][key]) ?? DICT.fr[key] ?? key;
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      s = s.replaceAll('{' + k + '}', String(v));
    }
  }
  return s;
}
