/* All interactions stay in the browser. No remote dependencies or requests. */
(() => {
  'use strict';
  document.documentElement.classList.add('js');
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const normalize = text => text.toLocaleLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '');
  const terms = value => normalize(value.trim()).split(/\s+/).filter(Boolean);
  const matches = (text, words) => words.every(word => normalize(text).includes(word));

  // Respect the operating system until the visitor explicitly chooses a theme.
  const themeButton = $('.theme-toggle');
  const themeKey = 'wizestream-site-theme';
  try {
    const stored = localStorage.getItem(themeKey);
    if (stored === 'light' || stored === 'dark') document.documentElement.dataset.theme = stored;
  } catch { /* Storage may be unavailable in a private/file browsing context. */ }
  const darkSystem = window.matchMedia('(prefers-color-scheme: dark)');
  const isDark = () => (document.documentElement.dataset.theme || (darkSystem.matches ? 'dark' : 'light')) === 'dark';
  function describeTheme() {
    themeButton?.setAttribute('aria-label', `Switch to ${isDark() ? 'light' : 'dark'} theme`);
  }
  if (themeButton) {
    themeButton.hidden = false;
    describeTheme();
    themeButton.addEventListener('click', () => {
      const choice = isDark() ? 'light' : 'dark';
      document.documentElement.dataset.theme = choice;
      try { localStorage.setItem(themeKey, choice); } catch { /* Preference remains session-only. */ }
      describeTheme();
    });
    darkSystem.addEventListener('change', describeTheme);
  }

  const menu = $('.primary-navigation');
  const menuButton = $('.menu-toggle');
  const mobile = window.matchMedia('(max-width: 800px)');
  function closeMenu() {
    menu?.classList.remove('is-open');
    menuButton?.setAttribute('aria-expanded', 'false');
    menuButton?.setAttribute('aria-label', 'Open navigation');
  }
  if (menu && menuButton) {
    menuButton.hidden = false;
    menuButton.addEventListener('click', () => {
      const open = menu.classList.toggle('is-open');
      menuButton.setAttribute('aria-expanded', String(open));
      menuButton.setAttribute('aria-label', `${open ? 'Close' : 'Open'} navigation`);
    });
    menu.addEventListener('click', event => { if (event.target.closest('a')) closeMenu(); });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && menu.classList.contains('is-open')) { closeMenu(); menuButton.focus(); }
    });
    mobile.addEventListener('change', closeMenu);
  }
  const docsMenu = $('.docs-menu');
  if (docsMenu) {
    docsMenu.open = !mobile.matches;
    mobile.addEventListener('change', () => { docsMenu.open = !mobile.matches; });
  }
  // Add current-page semantics without changing the static navigation fallback.
  $$('.nav-link.is-active, .docs-menu a.is-active').forEach(anchor => anchor.setAttribute('aria-current', 'page'));

  const dialog = $('#search-dialog');
  const searchInput = $('#site-search-input');
  const resultList = $('#site-search-results');
  const searchStatus = $('#site-search-status');
  const entries = (window.WizeStreamSearchIndex || []).map(entry => ({
    ...entry, normalizedTitle: normalize(entry.title), normalizedText: normalize(entry.text),
  }));
  const root = document.body.dataset.base || '.';
  let previousFocus;
  function renderSearch() {
    const words = terms(searchInput.value);
    const ranked = entries.map(entry => {
      const match = words.every(word => entry.normalizedText.includes(word) || entry.normalizedTitle.includes(word));
      const score = words.reduce((sum, word) => sum + (entry.normalizedTitle.includes(word) ? 20 : 0) + (normalize(entry.description).includes(word) ? 5 : 0), 0);
      return { entry, match, score };
    }).filter(item => item.match).sort((a, b) => b.score - a.score);
    const selected = words.length ? ranked.slice(0, 12) : ranked.filter(item => item.entry.url.startsWith('docs/')).slice(0, 6);
    resultList.replaceChildren();
    if (!selected.length) {
      const empty = document.createElement('p');
      empty.className = 'search-empty';
      empty.textContent = 'No matching pages. Try a shorter phrase or a different feature name.';
      resultList.append(empty);
    }
    for (const { entry } of selected) {
      const anchor = document.createElement('a');
      anchor.className = 'search-result';
      anchor.href = `${root}/${entry.url}`;
      const title = document.createElement('strong');
      title.textContent = entry.title;
      const excerpt = document.createElement('p');
      let snippet = entry.description;
      if (words.length && !matches(snippet, words)) {
        const first = entry.normalizedText.indexOf(words[0]);
        if (first >= 0) {
          const start = Math.max(0, first - 50);
          snippet = `${start ? '…' : ''}${entry.text.slice(start, start + 180)}…`;
        }
      }
      excerpt.textContent = snippet;
      anchor.append(title, excerpt);
      resultList.append(anchor);
    }
    searchStatus.textContent = words.length ? `${ranked.length} matching pages. Showing ${selected.length}.` : 'Suggested documentation guides.';
  }
  function openSearch() {
    if (!dialog || typeof dialog.showModal !== 'function') return false;
    previousFocus = document.activeElement;
    closeMenu();
    renderSearch();
    if (!dialog.open) dialog.showModal();
    searchInput.focus();
    return true;
  }
  if (dialog && searchInput && resultList && searchStatus) {
    searchInput.addEventListener('input', renderSearch);
    $('.search-button')?.addEventListener('click', event => { if (openSearch()) event.preventDefault(); });
    $('[data-close-search]')?.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => previousFocus?.focus());
    dialog.addEventListener('click', event => {
      if (event.target !== dialog) return;
      const rect = dialog.getBoundingClientRect();
      if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
    });
    searchInput.addEventListener('keydown', event => {
      if (event.key === 'ArrowDown') { event.preventDefault(); $('.search-result', resultList)?.focus(); }
      if (event.key === 'Enter') { const first = $('.search-result', resultList); if (first) first.click(); }
    });
    document.addEventListener('keydown', event => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !event.altKey) {
        if (openSearch()) event.preventDefault();
      }
    });
  }

  const guideFilter = $('#guide-filter');
  if (guideFilter) {
    const guides = $$('.doc-group .guide-card');
    guideFilter.addEventListener('input', () => {
      const words = terms(guideFilter.value);
      guides.forEach(card => { card.hidden = !matches(card.textContent, words); });
      $$('[data-guide-group]').forEach(group => { group.hidden = !$('.guide-card:not([hidden])', group); });
      const count = guides.filter(card => !card.hidden).length;
      $('#guide-filter-status').textContent = count ? `${count} guide${count === 1 ? '' : 's'} found. Use the header search to search inside every page.` : 'No matching guides. Try the header search to look inside all documentation.';
    });
  }
  const faqFilter = $('#faq-filter');
  if (faqFilter) {
    const questions = $$('.faq-item');
    const previousOpen = new Set();
    let filtering = false;
    faqFilter.addEventListener('input', () => {
      const words = terms(faqFilter.value);
      if (words.length && !filtering) questions.forEach(item => { if (item.open) previousOpen.add(item); });
      questions.forEach(item => {
        item.hidden = !matches(item.textContent, words);
        if (words.length) item.open = !item.hidden;
        else if (filtering) item.open = previousOpen.has(item);
      });
      $$('[data-faq-group]').forEach(group => { group.hidden = !$('.faq-item:not([hidden])', group); });
      const count = questions.filter(item => !item.hidden).length;
      $('#faq-filter-status').textContent = count ? `${count} answer${count === 1 ? '' : 's'}${words.length ? ' found' : ''}` : 'No matching answers. Try the full documentation search in the header.';
      filtering = words.length > 0;
      if (!filtering) previousOpen.clear();
    });
  }
  const comparison = $('#comparison-table');
  if (comparison) {
    const controls = $('.comparison-controls');
    const search = $('#comparison-search');
    const category = $('#comparison-category');
    const project = $('#comparison-project');
    const differences = $('#comparison-differences');
    const rows = $$('[data-comparison-row]', comparison);
    const projectKeys = $$('thead [data-project]', comparison).map(cell => cell.dataset.project);
    function filterComparison() {
      const keys = projectKeys.filter(key => project.value === 'all' || key === 'wizestream' || key === project.value);
      $$('[data-project]', comparison).forEach(cell => { cell.hidden = !keys.includes(cell.dataset.project); });
      comparison.classList.toggle('is-pair', keys.length === 2);
      const words = terms(search.value);
      let count = 0;
      rows.forEach(row => {
        const selectedCells = keys.map(key => $(`[data-project="${key}"]`, row));
        const levels = selectedCells.map(cell => cell.dataset.level);
        const differs = new Set(levels).size > 1;
        const content = $('th', row).textContent + ' ' + selectedCells.map(cell => cell.textContent).join(' ');
        row.hidden = !matches(content, words) || (category.value !== 'all' && row.dataset.category !== category.value) || (differences.checked && !differs);
        if (!row.hidden) count++;
      });
      $$('[data-comparison-group]', comparison).forEach(group => {
        group.hidden = !$('[data-comparison-row]:not([hidden])', group);
        $('.category-row th', group).colSpan = keys.length + 1;
      });
      $('#comparison-status').textContent = `${count} feature${count === 1 ? '' : 's'} · ${keys.length} projects${differences.checked ? ' · differences in capability status' : ''}`;
      $('#comparison-empty').hidden = count > 0;
    }
    controls.addEventListener('input', filterComparison);
    controls.addEventListener('change', filterComparison);
    controls.addEventListener('submit', event => event.preventDefault());
    controls.addEventListener('reset', () => requestAnimationFrame(filterComparison));
    filterComparison();
  }
  const downloadStats = $('[data-download-stats]');
  if (downloadStats) {
    const status = $('[data-download-stats-status]', downloadStats);
    const formatter = new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 });
    fetch(downloadStats.dataset.endpoint, {
      credentials: 'omit',
      headers: { Accept: 'application/json' },
      referrerPolicy: 'no-referrer'
    })
      .then(response => {
        if (!response.ok) throw new Error('Download statistics unavailable');
        return response.json();
      })
      .then(summary => {
        ['izzy', 'github', 'website', 'total'].forEach(key => {
          const target = `[data-download-stat="${key}"]`;
          const element = $(target, downloadStats);
          const value = Number(summary[key]);
          if (!element || !Number.isSafeInteger(value) || value < 0) return;
          element.textContent = formatter.format(value);
          element.title = value.toLocaleString();
          element.setAttribute('aria-label', value.toLocaleString() + ' downloads');
        });
        status.textContent = 'Aggregate totals update periodically. No visitor identifier is stored by this counter.';
      })
      .catch(() => {
        status.textContent = 'Live totals are temporarily unavailable. Download links still work.';
      });
  }

})();
