// SPDX-License-Identifier: GPL-3.0-or-later
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../reviews.js'), 'utf8');
const begin = source.indexOf('const REVIEW_ACCESS_STORAGE_KEY');
const end = source.indexOf('let receipt;', begin);
assert.ok(begin >= 0 && end > begin, 'Only browser-local credential helpers are tested');

function fixture() {
  const map = new Map();
  const localStorage = {
    getItem: (key) => map.get(key) || null,
    setItem: (key, value) => map.set(key, value),
    removeItem: (key) => map.delete(key),
  };
  const scope = { localStorage, URL, location: { href: 'https://wizestream.org/reviews/' } };
  vm.runInNewContext(source.slice(begin, end) + '\n;globalThis.reviewHelpers = {savedReviewAccess, saveReviewAccess, forgetReviewAccess, reviewAccessLink, REVIEW_ACCESS_STORAGE_KEY};', scope);
  return { map, storage: localStorage, ...scope.reviewHelpers };
}
const id = '123e4567-e89b-42d3-a456-426614174000';
const token = 'a'.repeat(43);

test('no reviewer credential is stored by default, and saving is explicit', () => {
  const f = fixture();
  assert.equal(f.map.size, 0);
  assert.equal(f.savedReviewAccess().length, 0);
  assert.equal(f.map.size, 0);
  f.saveReviewAccess(id, token);
  assert.equal(f.savedReviewAccess().length, 1);
  assert.equal(f.savedReviewAccess()[0].token, token);
  f.saveReviewAccess(id, token);
  assert.equal(f.savedReviewAccess().length, 1);
  f.forgetReviewAccess(id);
  assert.equal(f.savedReviewAccess().length, 0);
  assert.equal(f.map.size, 0);
});

test('malformed or tampered local entries cannot create management links', () => {
  const f = fixture();
  f.storage.setItem(f.REVIEW_ACCESS_STORAGE_KEY, 'not json');
  assert.equal(f.savedReviewAccess().length, 0);
  f.storage.setItem(f.REVIEW_ACCESS_STORAGE_KEY, JSON.stringify([{ id: 'wrong', token, savedAt: 1 }]));
  assert.equal(f.savedReviewAccess().length, 0);
  assert.throws(() => f.saveReviewAccess(id, 'bad'), /Invalid private management link/);
  assert.throws(() => f.reviewAccessLink('wrong', token), /Invalid private management link/);
  const link = f.reviewAccessLink(id, token);
  assert.equal(new URL(link).search, '?view=manage');
  assert.equal(new URL(link).hash, '#' + id + ':' + token);
});

test('storage failures do not silently claim that access was remembered', () => {
  const f = fixture();
  f.storage.setItem = () => { throw new Error('quota'); };
  assert.throws(() => f.saveReviewAccess(id, token), /Browser storage is unavailable/);
});
