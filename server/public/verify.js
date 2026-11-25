(function () {
  'use strict';
  var msgEl = document.getElementById('msg');
  var openBtn = document.getElementById('open');
  var retryBtn = document.getElementById('retry');
  var bridge = window.UniLinkBridge;

  // The single-use session id arrives in the URL fragment; remove it from the address bar right away.
  var pairingId = new URLSearchParams(location.hash.slice(1)).get('p');
  history.replaceState(null, '', location.pathname);

  function msg(t) { msgEl.textContent = t; }
  function fail(t) {
    msg(t); openBtn.hidden = true; retryBtn.hidden = false;
    if (bridge && bridge.onFailed) bridge.onFailed(t);
  }
  function openExternal(url) {
    if (bridge && bridge.openWorldApp) bridge.openWorldApp(url); else window.location.href = url;
  }

  function api(path, body) {
    return fetch(path, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) })
      .then(function (r) {
        return r.json().catch(function () { return null; }).then(function (j) {
          if (!r.ok || !j || j.success !== true) throw new Error((j && j.error) || 'request_failed');
          return j;
        });
      });
  }

  var friendly = {
    human_already_linked: 'This person is already verified on another UniLink account.',
    invalid_or_expired_session: 'This verification session expired. Go back to the app and try again.',
    verification_unavailable: 'The World ID service could not be reached. Please try again.'
  };

  async function run() {
    if (!pairingId) { fail('Open this page from the UniLink app.'); return; }
    try {
      msg('Requesting a secure challenge…');
      var s = await api('/api/rpSignature', { pairingId: pairingId });

      var request = await window.IDKit.request({
        app_id: s.appId,
        action: s.action,
        rp_context: s.rpContext,
        allow_legacy_proofs: false,
        environment: s.environment,
        return_to: 'unilink://human-verified'
      }).preset(window.IDKit.proofOfHuman({ signal: s.signal }));

      openBtn.onclick = function () { openExternal(request.connectorURI); };
      openBtn.hidden = false;
      msg('Open World App and complete the selfie check.');
      openExternal(request.connectorURI);

      var completion = await request.pollUntilCompletion({ pollInterval: 2000, timeout: 600000 });
      if (!completion || !completion.success) { fail('Verification was not completed.'); return; }

      msg('Checking your proof…');
      // The proof goes to OUR server, which asks World ID itself. This page never decides the result.
      await api('/api/verifyProof', { pairingId: pairingId, idkitResult: completion.result });

      msg('Verified. Returning to UniLink…');
      openBtn.hidden = true;
      if (bridge && bridge.onVerified) bridge.onVerified(); else window.location.href = 'unilink://human-verified';
    } catch (e) {
      fail(friendly[e && e.message] || ('Verification failed. Please try again. [' + (e && e.message) + ']'));
    }
  }

  retryBtn.onclick = function () { location.reload(); };
  run();
})();
