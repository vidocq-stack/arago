/*
 * Arago reveal.js plugin (arago-spec §4.6).
 *
 * Include in a reveal.js deck:  <script src="/arago-reveal-plugin.js"></script>
 * and open the deck with  ?aragoRoom=<PIN>&aragoSecret=<secret>  (from POST /api/rooms/{id}/reveal/enable).
 *
 * It connects to the room WebSocket with the secret, EMITS the current slide position
 * ({type:"reveal.state", indexh, indexv, fragment}) on every change, and EXECUTES the commands it
 * RECEIVES ({type:"reveal.cmd", cmd:"next|prev|goto|togglePause", slide?}). Vanilla JS, zero deps.
 */
(function () {
  'use strict';

  var params = new URLSearchParams(window.location.search);
  var room = params.get('aragoRoom');
  var secret = params.get('aragoSecret');
  if (!room || !secret) {
    console.info('[arago-reveal-plugin] no aragoRoom/aragoSecret in the URL — idle.');
    return;
  }

  function connect(Reveal) {
    var proto = location.protocol === 'https:' ? 'wss' : 'ws';
    var url = proto + '://' + location.host + '/ws/rooms/' + encodeURIComponent(room)
      + '?secret=' + encodeURIComponent(secret);
    var ws = new WebSocket(url);

    function sendState() {
      if (ws.readyState !== WebSocket.OPEN) return;
      var idx = Reveal.getIndices ? Reveal.getIndices() : { h: 0, v: 0, f: -1 };
      ws.send(JSON.stringify({
        type: 'reveal.state',
        indexh: idx.h || 0,
        indexv: idx.v || 0,
        fragment: (idx.f === undefined || idx.f === null) ? -1 : idx.f,
      }));
    }

    ws.onopen = function () {
      sendState();
      // Lets a host page show a "connected" indicator (and tests await readiness, race-free).
      try { window.dispatchEvent(new CustomEvent('arago-reveal-connected')); } catch (e) { /* ignore */ }
    };
    ws.onmessage = function (ev) {
      var m;
      try { m = JSON.parse(ev.data); } catch (e) { return; }
      if (m.type !== 'reveal.cmd') return;
      switch (m.cmd) {
        case 'next': if (Reveal.next) Reveal.next(); break;
        case 'prev': if (Reveal.prev) Reveal.prev(); break;
        case 'goto': if (Reveal.slide && m.slide != null) Reveal.slide(m.slide); break;
        case 'togglePause': if (Reveal.togglePause) Reveal.togglePause(); break;
        default: break;
      }
    };

    var on = Reveal.on || Reveal.addEventListener;
    if (on) {
      ['slidechanged', 'fragmentshown', 'fragmenthidden'].forEach(function (e) {
        on.call(Reveal, e, sendState);
      });
    }
    console.info('[arago-reveal-plugin] connected to room ' + room);
  }

  function init() {
    if (window.Reveal) connect(window.Reveal);
    else console.info('[arago-reveal-plugin] window.Reveal not found — load reveal.js first.');
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
