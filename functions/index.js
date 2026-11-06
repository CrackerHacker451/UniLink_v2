'use strict';
const { onRequest } = require('firebase-functions/v2/https');
const { defineSecret, defineString } = require('firebase-functions/params');
const admin = require('firebase-admin');
const { signRequest } = require('@worldcoin/idkit-core/signing');
const { hashSignal } = require('@worldcoin/idkit-core/hashing');
const { Nullifier } = require('@worldcoin/idkit-core/nullifier');
const { createGate } = require('./gate');

admin.initializeApp();

// Set these once (see functions/README in the PR description):
//   firebase functions:secrets:set WORLD_RP_SIGNING_KEY
//   WORLD_APP_ID and WORLD_RP_ID in functions/.env.<project>
const WORLD_RP_SIGNING_KEY = defineSecret('WORLD_RP_SIGNING_KEY');
const WORLD_APP_ID = defineString('WORLD_APP_ID');
const WORLD_RP_ID = defineString('WORLD_RP_ID');
const WORLD_ENVIRONMENT = defineString('WORLD_ENVIRONMENT', { default: 'production' });

function gate() {
  return createGate({
    db: admin.firestore(),
    auth: admin.auth(),
    fetchFn: fetch,
    signRequest, hashSignal, Nullifier,
    serverTimestamp: () => admin.firestore.FieldValue.serverTimestamp(),
    config: {
      signingKeyHex: WORLD_RP_SIGNING_KEY.value(),
      appId: WORLD_APP_ID.value(),
      rpId: WORLD_RP_ID.value(),
      environment: WORLD_ENVIRONMENT.value(),
    },
  });
}

// The app calls this with its Firebase ID token (no browser CORS involved).
exports.startVerification = onRequest({ secrets: [WORLD_RP_SIGNING_KEY], region: 'us-central1' }, (req, res) => gate().startVerification(req, res));
// The hosted verification page calls these two. They hold no cookies or ambient credentials: access is
// controlled by the single-use, 10-minute pairingId, so open CORS is not a security boundary here.
exports.rpSignature = onRequest({ secrets: [WORLD_RP_SIGNING_KEY], region: 'us-central1', cors: true }, (req, res) => gate().rpSignature(req, res));
exports.verifyProof = onRequest({ secrets: [WORLD_RP_SIGNING_KEY], region: 'us-central1', cors: true }, (req, res) => gate().verifyProof(req, res));
