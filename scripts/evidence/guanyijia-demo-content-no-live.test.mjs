import assert from 'node:assert/strict';
import childProcess from 'node:child_process';
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import tls from 'node:tls';
import { syncBuiltinESMExports } from 'node:module';
import test from 'node:test';

const blockedCall = (name) => (...args) => {
  throw new Error(`normal V6 publication path attempted ${name}`);
};

test('the normal V6 browser publication path is local-only and does not call Codex or network APIs', async () => {
  const originals = {
    spawn: childProcess.spawn,
    execFile: childProcess.execFile,
    exec: childProcess.exec,
    fork: childProcess.fork,
    httpRequest: http.request,
    httpGet: http.get,
    httpsRequest: https.request,
    httpsGet: https.get,
    netConnect: net.connect,
    netCreateConnection: net.createConnection,
    tlsConnect: tls.connect,
    fetch: globalThis.fetch,
  };

  childProcess.spawn = blockedCall('child_process.spawn');
  childProcess.execFile = blockedCall('child_process.execFile');
  childProcess.exec = blockedCall('child_process.exec');
  childProcess.fork = blockedCall('child_process.fork');
  http.request = blockedCall('http.request');
  http.get = blockedCall('http.get');
  https.request = blockedCall('https.request');
  https.get = blockedCall('https.get');
  net.connect = blockedCall('net.connect');
  net.createConnection = blockedCall('net.createConnection');
  tls.connect = blockedCall('tls.connect');
  globalThis.fetch = blockedCall('fetch');
  syncBuiltinESMExports();

  try {
    const packageModule = await import(`./guanyijia-demo-content-package.mjs?local-only=${Date.now()}`);
    const publication = await packageModule.buildDemoContentPublication();

    assert.equal(publication.contentSnapshotId, 'guanyijia-demo-content-v6-20260826');
    assert.equal(publication.sources.length, 5);
  } finally {
    childProcess.spawn = originals.spawn;
    childProcess.execFile = originals.execFile;
    childProcess.exec = originals.exec;
    childProcess.fork = originals.fork;
    http.request = originals.httpRequest;
    http.get = originals.httpGet;
    https.request = originals.httpsRequest;
    https.get = originals.httpsGet;
    net.connect = originals.netConnect;
    net.createConnection = originals.netCreateConnection;
    tls.connect = originals.tlsConnect;
    globalThis.fetch = originals.fetch;
    syncBuiltinESMExports();
  }
});
