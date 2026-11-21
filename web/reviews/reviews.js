// SPDX-License-Identifier: GPL-3.0-or-later
const $ = (id) => document.getElementById(id);
const node = (tag, text, className) => {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
};

async function api(path, { method = 'GET', data, authorization } = {}) {
  const headers = { Accept: 'application/json' };
  if (data !== undefined) headers['Content-Type'] = 'application/json';
  if (authorization?.startsWith('Bearer ')) headers['X-Moderator-Key'] = authorization.slice(7);
  else if (authorization) headers['X-Review-Authorization'] = authorization;
  const requested = new URL(path, location.origin);
  const endpoint = new URL('api.php', location.href);
  endpoint.search = requested.search;
  endpoint.searchParams.set('action', requested.pathname.split('/').pop());
  if (method === 'DELETE') { method = 'POST'; data = { ...data, operation: 'delete' }; }
  const response = await fetch(endpoint, {
    method, headers, body: data === undefined ? undefined : JSON.stringify(data),
    credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer',
  });
  let result;
  try { result = await response.json(); } catch { throw new Error('The service did not respond. Please try again.'); }
  if (!response.ok) {
    const error = new Error(result.error || 'The request could not be completed.');
    error.status = response.status;
    throw error;
  }
  return result;
}

function reviewCard(review) {
  const article = node('article', undefined, 'review');
  const stars = node('div', '★'.repeat(review.rating) + '☆'.repeat(5 - review.rating), 'review-stars');
  stars.setAttribute('aria-label', `${review.rating} out of 5 stars`);
  article.append(stars, node('p', review.content, 'review-content'));
  const meta = node('div', undefined, 'review-meta');
  meta.append(node('span', review.nickname || 'Anonymous', 'review-author'));
  const date = new Date(`${review.date}T00:00:00Z`);
  meta.append(node('span', date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' })));
  article.append(meta);
  if (review.appVersion) article.append(node('p', `App version: ${review.appVersion}`, 'quiet'));
  if (review.developerReply) {
    const reply = node('section', undefined, 'developer-reply');
    reply.append(node('p', 'Official WizeStream developer response', 'developer-reply-heading'));
    reply.append(node('p', review.developerReply, 'developer-reply-content'));
    if (review.replyDate) reply.append(node('p', `Updated ${review.replyDate}`, 'quiet'));
    article.append(reply);
  }
  return article;
}

function showSummary(data) {
  $('average').replaceChildren(document.createTextNode(data.count ? data.average.toFixed(1) : '—'), node('small', '/ 5'));
  $('rating-count').textContent = data.count ? `Based on ${data.count.toLocaleString()} published ${data.count === 1 ? 'review' : 'reviews'}` : 'No reviews yet. Be the first to share.';
  $('review-total').textContent = data.count ? `${data.count.toLocaleString()} published` : 'All ratings welcome';
  const rows = [];
  for (let rating = 5; rating >= 1; rating--) {
    const count = data.distribution[rating - 1];
    const row = node('div', undefined, 'distribution-row');
    row.append(node('span', `${rating} ★`));
    const bar = node('progress');
    bar.max = Math.max(data.count, 1); bar.value = count;
    bar.setAttribute('aria-label', `${count} published ${rating}-star reviews`);
    row.append(bar, node('span', String(count))); rows.push(row);
  }
  $('distribution').replaceChildren(...rows);
}

let currentPage = 1;
async function loadReviews(page = currentPage) {
  $('previous').disabled = true; $('next').disabled = true;
  try {
    const result = await api(`/api/reviews?page=${page}`);
    currentPage = page; showSummary(result);
    $('reviews').replaceChildren(...result.reviews.map(reviewCard));
    if (!result.reviews.length) $('reviews').append(node('p', result.count ? 'No reviews on this page.' : 'A fresh start. Your review can help the next person discover WizeStream.', 'empty'));
    $('list-status').textContent = '';
    $('page-label').textContent = result.count ? `Page ${page} of ${Math.ceil(result.count / result.pageSize)}` : '';
    $('previous').disabled = page <= 1;
    $('next').disabled = page * result.pageSize >= result.count;
  } catch (error) {
    $('list-status').textContent = error.message;
    $('rating-count').textContent = 'Rating unavailable. Please reload to try again.';
  }
}

let challengePromise;
function getChallenge() {
  if (!challengePromise) {
    challengePromise = api('/api/challenge').then(data => ({ ...data, receivedAt: Date.now() }))
      .catch(error => { challengePromise = null; throw error; });
  }
  return challengePromise;
}
const REVIEW_ACCESS_STORAGE_KEY = 'wizestream-private-review-access-v1';
const reviewIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const reviewTokenPattern = /^[A-Za-z0-9_-]{43}$/;
function validReviewAccess(value) {
  return value && typeof value === 'object' && reviewIdPattern.test(value.id) &&
    reviewTokenPattern.test(value.token) && Number.isFinite(value.savedAt);
}
function savedReviewAccess() {
  try {
    const parsed = JSON.parse(localStorage.getItem(REVIEW_ACCESS_STORAGE_KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter(validReviewAccess).slice(0, 30) : [];
  } catch { return []; }
}
function saveReviewAccess(id, token) {
  const entry = { id, token, savedAt: Date.now() };
  if (!validReviewAccess(entry)) throw new Error('Invalid private management link.');
  try {
    const stored = savedReviewAccess().filter(value => value.id !== id);
    localStorage.setItem(REVIEW_ACCESS_STORAGE_KEY, JSON.stringify([entry, ...stored].slice(0, 30)));
  } catch {
    throw new Error('Browser storage is unavailable. Copy your private management link instead.');
  }
}
function forgetReviewAccess(id) {
  try {
    const retained = savedReviewAccess().filter(value => value.id !== id);
    if (retained.length) localStorage.setItem(REVIEW_ACCESS_STORAGE_KEY, JSON.stringify(retained));
    else localStorage.removeItem(REVIEW_ACCESS_STORAGE_KEY);
  } catch { throw new Error('Could not clear saved access. Check your browser site data.'); }
}
function reviewAccessLink(id, token) {
  if (!validReviewAccess({ id, token, savedAt: Date.now() })) throw new Error('Invalid private management link.');
  const link = new URL('?view=manage', location.href);
  link.hash = `${id}:${token}`;
  return link.toString();
}
function setupMine() {
  const saved = savedReviewAccess();
  const container = $('saved-reviews');
  container.replaceChildren();
  $('forget-all-reviews').hidden = saved.length === 0;
  $('mine-status').textContent = saved.length
    ? `${saved.length} private management ${saved.length === 1 ? 'link' : 'links'} saved only in this browser.`
    : 'No reviews are remembered in this browser. You can still use an original private management link.';
  for (const entry of saved) {
    const card = node('article', undefined, 'review saved-review');
    const date = new Date(entry.savedAt).toLocaleDateString();
    card.append(node('h3', 'Saved review'), node('p', `Saved on ${date}`, 'quiet'));
    const actions = node('div', undefined, 'actions');
    const open = node('a', 'View or edit review', 'button');
    open.href = reviewAccessLink(entry.id, entry.token);
    const forget = node('button', 'Forget on this device', 'danger');
    forget.type = 'button'; forget.onclick = () => {
      try { forgetReviewAccess(entry.id); setupMine(); }
      catch (error) { $('mine-status').textContent = error.message; }
    };
    actions.append(open, forget); card.append(actions); container.append(card);
  }
  $('forget-all-reviews').onclick = () => {
    if (!confirm('Forget all saved review management links from this browser? This will not delete any public reviews.')) return;
    try { localStorage.removeItem(REVIEW_ACCESS_STORAGE_KEY); setupMine(); }
    catch { $('mine-status').textContent = 'Unable to clear browser storage.'; }
  };
}

let receipt;
function createReceipt() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  const token = btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return { id: crypto.randomUUID(), token };
}
function showReceipt(uncertain = false) {
  const link = reviewAccessLink(receipt.id, receipt.token);
  $('receipt-link').value = link; $('open-receipt').href = link;
  $('receipt-title').textContent = uncertain ? 'Keep this link while checking your submission.' : 'Thank you. Save your management link.';
  $('receipt-message').textContent = uncertain
    ? 'The connection ended before receipt could be confirmed. Use this private link to check whether your review arrived before trying again. Anyone with the link can access or remove it.'
    : 'Your review is awaiting moderation. Use this private link to check or remove it, without an account. Anyone with the link can access or remove it.';
  $('receipt').hidden = false;
}
function setupPublic() {
  loadReviews();
  $('previous').onclick = () => loadReviews(currentPage - 1);
  $('next').onclick = () => loadReviews(currentPage + 1);
  $('review-form').addEventListener('focusin', () => { getChallenge().catch(error => { $('submit-status').textContent = error.message; }); }, { once: true });
  $('review-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!$('review-form').reportValidity()) return;
    const button = $('submit-review'); button.disabled = true;
    $('submit-status').textContent = 'Preparing your submission…';
    let attempted = false;
    try {
      const challenge = await getChallenge();
      const remaining = challenge.minimumWaitMs - (Date.now() - challenge.receivedAt);
      if (remaining > 0) await new Promise(resolve => setTimeout(resolve, remaining + 50));
      receipt ||= createReceipt();
      const fields = new FormData($('review-form'));
      attempted = true;
      const result = await api('/api/reviews', { method: 'POST', data: {
        id: receipt.id, deletionToken: receipt.token, challenge: challenge.challenge,
        rating: Number(fields.get('rating')), content: fields.get('content'),
        nickname: fields.get('nickname'), appVersion: fields.get('appVersion'),
        website: fields.get('website'), consent: fields.get('consent') === 'on',
      } });
      showReceipt();
      $('submit-status').textContent = result.alreadyReceived ? 'This review was already received. Use your private link to manage it.' : 'Received. Your rating and review will appear after moderation.';
      if ($('remember-review-on-device').checked) {
        try {
          saveReviewAccess(receipt.id, receipt.token);
          $('remember-receipt').textContent = 'Remembered on this device';
          $('receipt-message').textContent += ' Your management link is also saved in this browser.';
        } catch (error) { $('submit-status').textContent += ' ' + error.message; }
      }
      $('receipt').scrollIntoView({ behavior: 'smooth', block: 'start' });
      button.textContent = 'Review received';
      $('review-form').querySelectorAll('input, textarea').forEach(field => { field.disabled = true; });
    } catch (error) {
      $('submit-status').textContent = error.message;
      if (attempted && (!error.status || error.status >= 500)) showReceipt(true);
      challengePromise = null;
      button.disabled = false;
    }
  });
  $('remember-receipt').onclick = () => {
    try {
      saveReviewAccess(receipt.id, receipt.token);
      $('remember-receipt').textContent = 'Remembered on this device';
    } catch (error) { $('submit-status').textContent = error.message; }
  };
  $('copy-receipt').onclick = async () => {
    try {
      await navigator.clipboard.writeText($('receipt-link').value);
      $('copy-receipt').textContent = 'Copied';
    } catch {
      $('receipt-link').focus(); $('receipt-link').select();
      $('copy-receipt').textContent = 'Select and copy the link';
    }
  };
}

async function setupManage() {
  const match = /^#([0-9a-f-]{36}):([A-Za-z0-9_-]{43})$/.exec(location.hash);
  if (!match) { $('manage-status').textContent = 'Open the complete private management link saved when you submitted your review.'; return; }
  const authorization = `Review ${match[1]}:${match[2]}`;
  try {
    const review = await api('/api/manage', { method: 'POST', data: {}, authorization });
    $('managed-review').replaceChildren(reviewCard(review));
    const remembered = savedReviewAccess().some(entry => entry.id === match[1] && entry.token === match[2]);
    $('remember-managed-review').hidden = false;
    $('forget-managed-review').hidden = !remembered;
    $('remember-managed-review').textContent = remembered ? 'Remembered in this browser' : 'Remember on this device';
    $('remember-managed-review').onclick = () => {
      try {
        saveReviewAccess(match[1], match[2]);
        $('remember-managed-review').textContent = 'Remembered in this browser';
        $('forget-managed-review').hidden = false;
      } catch (error) { $('manage-status').textContent = error.message; }
    };
    $('forget-managed-review').onclick = () => {
      try {
        forgetReviewAccess(match[1]);
        $('forget-managed-review').hidden = true;
        $('remember-managed-review').textContent = 'Remember on this device';
      } catch (error) { $('manage-status').textContent = error.message; }
    };
    $('manage-status').textContent = review.state === 'published' ? 'Your review is published.' : 'Your review is awaiting moderation. Pending reviews expire after 30 days.';
    const editForm = $('edit-review-form');
    const fields = review.pendingEdit || review;
    $('edit-rating').value = String(fields.rating);
    $('edit-content').value = fields.content;
    $('edit-nickname').value = fields.nickname || '';
    $('edit-app-version').value = fields.appVersion || '';
    $('edit-review-status').textContent = review.pendingEdit
      ? 'Your proposed update is awaiting moderation. The previous published version remains visible.'
      : 'Changes to a published review will be checked before replacing the public version.';
    editForm.hidden = false;
    editForm.onsubmit = async (event) => {
      event.preventDefault();
      if (!editForm.reportValidity()) return;
      const submit = $('save-review-edit');
      submit.disabled = true;
      try {
        const data = { operation: 'edit', rating: Number($('edit-rating').value),
          content: $('edit-content').value, nickname: $('edit-nickname').value,
          appVersion: $('edit-app-version').value };
        await api('/api/manage', { method: 'POST', data, authorization });
        await setupManage();
        $('edit-review-status').textContent = review.state === 'published'
          ? 'Update submitted for moderation.' : 'Pending review updated.';
      } catch (error) { $('edit-review-status').textContent = error.message; }
      finally { submit.disabled = false; }
    };
    $('cancel-review-edit').hidden = !review.pendingEdit;
    $('cancel-review-edit').onclick = async () => {
      if (!confirm('Cancel the pending changes? The published review will remain unchanged.')) return;
      try {
        await api('/api/manage', { method: 'POST', data: { operation: 'cancel-edit' }, authorization });
        await setupManage();
      } catch (error) { $('edit-review-status').textContent = error.message; }
    };
    $('delete-review').hidden = false;
    $('delete-review').onclick = async () => {
      if (!confirm('Permanently delete your review and its rating from the live service?')) return;
      $('delete-review').disabled = true;
      try {
        await api('/api/manage', { method: 'DELETE', data: {}, authorization });
        $('managed-review').replaceChildren(); $('delete-review').hidden = true;
        $('edit-review-form').hidden = true; $('cancel-review-edit').hidden = true;
        try { forgetReviewAccess(match[1]); } catch { /* The public deletion has succeeded. */ }
        $('remember-managed-review').hidden = true; $('forget-managed-review').hidden = true;
        $('manage-status').textContent = 'Your review and rating have been deleted.';
        history.replaceState(null, '', new URL('?view=manage', location.href));
      } catch (error) { $('manage-status').textContent = error.message; $('delete-review').disabled = false; }
    };
  } catch (error) { $('manage-status').textContent = error.message; }
}

let moderatorKey = ''; let moderationEpoch = 0; let queueRequest = 0; let moderationState = 'pending'; let moderationPage = 1;
async function loadQueue(page = moderationPage) {
  const epoch = moderationEpoch;
  const requestId = ++queueRequest;
  try {
    const result = await api(`/api/moderation?state=${moderationState}&page=${page}`, { authorization: `Bearer ${moderatorKey}` });
    if (epoch !== moderationEpoch || requestId !== queueRequest || !moderatorKey) return;
    moderationPage = page;
    $('moderator-status').textContent = `${result.count} ${moderationState} ${result.count === 1 ? 'review' : 'reviews'}.`;
    const cards = result.reviews.map(review => {
      const card = reviewCard(review); const actions = node('div', undefined, 'actions');
      if (review.state === 'edit') {
        card.append(node('p', `Currently published: ${review.originalRating}/5 — ${review.originalContent}`, 'quiet'));
      }
      const availableActions = review.state === 'pending' ? ['publish', 'remove']
        : review.state === 'edit' ? ['approve-edit', 'reject-edit'] : ['remove'];
      for (const action of availableActions) {
        const label = { publish: 'Publish', remove: 'Remove',
          'approve-edit': 'Approve update', 'reject-edit': 'Reject update' }[action];
        const button = node('button', label);
        button.type = 'button'; button.onclick = async () => {
          if (action === 'remove' && !confirm('Permanently remove this review? Use this only for a published moderation-policy reason.')) return;
          if (action === 'reject-edit' && !confirm('Reject the pending update? The previous published review stays visible.')) return;
          button.disabled = true;
          try {
            await api('/api/moderation', { method: 'POST', authorization: `Bearer ${moderatorKey}`, data: { id: review.id, action } });
            await loadQueue(1);
          } catch (error) { $('moderator-status').textContent = error.message; button.disabled = false; }
        }; actions.append(button);
      }
      if (review.state === 'published') {
        const editor = node('div', undefined, 'developer-reply-editor');
        const label = node('label', 'Official developer reply');
        const field = node('textarea');
        field.id = `developer-reply-${review.id}`; label.htmlFor = field.id;
        field.rows = 4; field.maxLength = 1500; field.value = review.developerReply || '';
        const replyActions = node('div', undefined, 'actions');
        const save = node('button', review.developerReply ? 'Update reply' : 'Publish reply');
        save.type = 'button'; save.onclick = async () => {
          if (!field.value.trim()) { $('moderator-status').textContent = 'Write a reply before saving.'; return; }
          save.disabled = true;
          try {
            await api('/api/moderation', { method: 'POST', authorization: `Bearer ${moderatorKey}`,
              data: { id: review.id, action: 'reply', content: field.value } });
            await loadQueue(moderationPage);
          } catch (error) { $('moderator-status').textContent = error.message; save.disabled = false; }
        };
        replyActions.append(save);
        if (review.developerReply) {
          const remove = node('button', 'Remove reply', 'danger');
          remove.type = 'button'; remove.onclick = async () => {
            if (!confirm('Remove the official response from this review?')) return;
            remove.disabled = true;
            try {
              await api('/api/moderation', { method: 'POST', authorization: `Bearer ${moderatorKey}`,
                data: { id: review.id, action: 'remove-reply' } });
              await loadQueue(moderationPage);
            } catch (error) { $('moderator-status').textContent = error.message; remove.disabled = false; }
          };
          replyActions.append(remove);
        }
        editor.append(label, field, replyActions); card.append(editor);
      }
      card.append(actions); return card;
    });
    $('moderator-reviews').replaceChildren(...cards);
    $('moderator-pagination').hidden = false;
    $('mod-page').textContent = `Page ${page} of ${Math.max(1, Math.ceil(result.count / result.pageSize))}`;
    $('mod-previous').disabled = page <= 1; $('mod-next').disabled = page * result.pageSize >= result.count;
    $('moderator-controls').hidden = false; $('moderator-login').hidden = true;
  } catch (error) { if (epoch === moderationEpoch && requestId === queueRequest) $('moderator-status').textContent = error.message; }
}
function lockModeration() {
  moderationEpoch++; queueRequest++; moderatorKey = '';
  $('moderator-key').value = ''; $('moderator-reviews').replaceChildren();
  $('moderator-controls').hidden = true; $('moderator-pagination').hidden = true;
  $('moderator-login').hidden = false; $('moderator-status').textContent = 'Locked.';
}
window.addEventListener('pagehide', lockModeration);

function setupModeration() {
  $('moderator-login').onsubmit = (event) => {
    event.preventDefault(); moderationEpoch++; moderatorKey = $('moderator-key').value.trim(); $('moderator-key').value = ''; loadQueue(1);
  };
  for (const button of document.querySelectorAll('[data-state]')) button.onclick = () => { moderationState = button.dataset.state; loadQueue(1); };
  $('moderator-logout').onclick = lockModeration;
  $('mod-previous').onclick = () => loadQueue(moderationPage - 1);
  $('mod-next').onclick = () => loadQueue(moderationPage + 1);
}

const view = new URL(location.href).searchParams.get('view');
if (view === 'manage' || view === 'moderate' || view === 'mine') {
  $('public-page').hidden = true;
  if (view === 'manage') { $('manage-page').hidden = false; setupManage(); }
  else if (view === 'mine') { $('mine-page').hidden = false; setupMine(); }
  else { $('moderate-page').hidden = false; setupModeration(); }
} else setupPublic();
