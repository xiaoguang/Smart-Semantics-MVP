import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

test('informational Ant message layers never intercept business controls', () => {
  const styles = readFileSync(new URL('../../App.css', import.meta.url), 'utf8');
  const messageLayer = styles.match(/\.ant-message,\s*\.ant-message \.ant-message-notice,\s*\.ant-message \.ant-message-notice-content\s*\{(?<body>[^}]*)\}/u)?.groups?.body ?? '';

  assert.notEqual(messageLayer, '', 'message layer selector must cover container, notice, and content');
  assert.match(messageLayer, /pointer-events:\s*none/u);
});

test('App startup config limits informational messages to one visible notice', () => {
  const app = readFileSync(new URL('../../App.tsx', import.meta.url), 'utf8');
  const config = app.match(/message\.config\(\{(?<body>[\s\S]*?)\}\)/u)?.groups?.body ?? '';

  assert.notEqual(config, '', 'App must configure Ant message at the startup boundary');
  assert.match(config, /maxCount\s*:\s*1/u);
  assert.match(config, /duration\s*:\s*[1-9][0-9]?(?:\.[0-9]+)?/u);
});
