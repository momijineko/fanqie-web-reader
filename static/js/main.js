// ====== Router ======
const routes = [];
function route(path, handler) { routes.push({ path, handler }); }

// Track the last top-level tab for smart back navigation
let lastTopTab = 'search';

function navigate(hash) { location.hash = hash; }

function goBack() {
  const hash = location.hash.slice(1) || 'search';
  const path = hash.split('?')[0];
  const qs = hash.split('?')[1];
  const q = {};
  if (qs) new URLSearchParams(qs).forEach((v,k) => q[k] = v);

  if (path === 'search' && q.q) {
    // Search results → back to home (clear query)
    $('searchInput').value = '';
    updateClearBtn();
    location.hash = 'search';
  } else if (path === 'reader' && q.book_id) {
    location.hash = `detail?book_id=${q.book_id}`;
  } else if (path === 'comments' && q.book_id) {
    location.hash = `detail?book_id=${q.book_id}`;
  } else if (path === 'author') {
    // Author page → back to the detail page that linked here
    const fromBookId = q.from || '';
    location.hash = fromBookId ? `detail?book_id=${fromBookId}` : lastTopTab;
  } else if (path === 'detail') {
    location.hash = lastTopTab;
  } else {
    location.hash = 'search';
  }
}

function router() {
  const hash = location.hash.slice(1) || 'search';
  const [path, queryStr] = hash.split('?');
  const query = {};
  if (queryStr) new URLSearchParams(queryStr).forEach((v,k) => query[k] = v);

  if (currentChapterKey && !path.startsWith('reader')) {
    sessionStorage.setItem('scroll_' + currentChapterKey, window.scrollY);
    currentChapterKey = null;
  }
  if (!path.startsWith('reader')) { appendedChapters = 0; }

  const app = $('app');
  const isReader = path === 'reader';
  const isPageMode = isReader && getReadMode() === 'page';
  const hasSearchQuery = path === 'search' && query.q;
  const showSearch = path === 'search' || path === 'shelf';
  const showBottomNav = (showSearch || path === 'profile') && !isPageMode;

  // Track which top-level tab the user was on before going deeper
  if (path === 'search' || path === 'shelf' || path === 'profile') {
    lastTopTab = path;
  }
  const header = document.querySelector('.header');
  if (header) header.style.display = isReader ? 'none' : '';
  document.body.style.overflow = isPageMode ? 'hidden' : '';
  // Show back button on: detail, comments, search-with-query (results page)
  // Hide on: search-home, shelf, reader (has its own back)
  const showBackBtn = hasSearchQuery || (!showSearch && !isReader);
  $('backBtn').classList.toggle('hidden', !showBackBtn);
  $('searchBar').classList.toggle('hidden', !showSearch);
  $('searchTabs').classList.toggle('hidden', path !== 'search');
  $('backTop').classList.remove('visible');
  const bottomNav = $('bottomNav');
  if (bottomNav) {
    bottomNav.classList.toggle('hidden', !showBottomNav);
    bottomNav.querySelectorAll('.bottom-nav-item').forEach(item => {
      item.classList.toggle('active', item.dataset.route === path);
    });
  }

  for (const r of routes) { if (r.path === path) { r.handler(query, app); break; } }
  // Refresh Lucide icons after route renders (delay to wait for async + DOM updates)
  setTimeout(() => refreshIcons(), 300);
}
window.addEventListener('hashchange', router);

// ====== Search state ======
let S = { key: '', tab: 3, loading: false, tabCache: {} };
function getTabData() { return S.tabCache[S.tab] || { books: [], filtered: [], offset: 0, hasMore: false, sortBy: 'default', tagFilter: '' }; }
function saveTabData(d) { S.tabCache[S.tab] = d; }

// ====== Routes ======
route('search', (q, app) => {
  $('pageTitle').textContent = '番茄小说';
  $('searchInput').placeholder = '搜索书名、作者...';
  if (q.q) {
    $('searchInput').value = q.q;
    doSearch();
  } else {
    // Back to home — clear search state so home renders
    $('searchInput').value = '';
    updateClearBtn();
    S.key = '';
    saveTabData({ books: [], filtered: [], offset: 0, hasMore: false, sortBy: 'default', tagFilter: '' });
    renderHome(app);
  }
});

route('shelf', (q, app) => { const count = loadData().shelf.length; $('pageTitle').textContent = `我的书架 (${count})`; $('searchInput').value = ''; $('searchInput').placeholder = '搜索书架...'; updateClearBtn(); _shelfFilter = ''; renderShelf(app); });

route('profile', (q, app) => { $('pageTitle').textContent = '我的'; renderProfile(app); });

route('author', async (q, app) => {
  const authorId = q.author_id || '';
  const name = q.name || '';
  if (!authorId && !name) { navigate('search'); return; }
  $('pageTitle').textContent = name || '作者';
  app.innerHTML = skeletonAuthor();
  renderAuthorPage(app, authorId, name);
});

route('detail', async (q, app) => {
  const bid = q.book_id;
  $('pageTitle').textContent = '加载中...';
  if (cache.detail[bid]) renderDetail(app, bid);
  else { app.innerHTML = skeletonDetail(); await fetchDetail(bid); renderDetail(app, bid); }
});

route('reader', async (q, app) => {
  const bid = q.book_id;
  let idx = parseInt(q.chapter_idx || '0');
  $('pageTitle').textContent = '加载中...';
  const header = document.querySelector('.header');
  if (header) header.style.display = 'none';
  if (!cache.detail[bid]) { app.innerHTML = skeletonReader(); await fetchDetail(bid); }
  const d = cache.detail[bid];
  if (!d || !d.chapters.length) { app.innerHTML = errorHtml('加载失败', `reader?book_id=${bid}&chapter_idx=0`); if (header) header.style.display = ''; return; }
  if (idx < 0 || idx >= d.chapters.length) idx = 0;
  currentBookId = bid;
  currentChapterIdx = idx;
  appendedChapters = 0;
  const chapterId = d.chapters[idx].ChapterID;
  if (getContentCache(chapterId)) renderReader(app, bid, idx);
  else { app.innerHTML = skeletonReader(); await fetchContent(chapterId, bid); renderReader(app, bid, idx); }
});

route('comments', async (q, app) => {
  const bid = q.book_id;
  $('pageTitle').textContent = '评论';
  if (cache.detail[bid] && cache.detail[bid].comments) renderComments(app, bid);
  else {
    app.innerHTML = skeletonComments();
    if (!cache.detail[bid]) await fetchDetail(bid);
    await fetchComments(bid);
    renderComments(app, bid);
  }
});

// ====== Search logic ======
function renderTabs() {
  $('searchTabs').innerHTML = TABS.map(t => `<button class="search-tab${S.tab===t.id?' active':''}${t.id!==3?' disabled':''}" ${t.id!==3?'disabled':''} onclick="selectTab(${t.id})">${t.name}</button>`).join('');
}
function selectTab(id) {
  S.tab = id; renderTabs();
  const td = getTabData();
  if (td.books.length > 0) renderResults($('app')); else renderHome($('app'));
}

function applyFilters() {
  const td = getTabData();
  let list = td.books.slice();
  if (td.tagFilter) list = list.filter(b => (b.Tags||'').toLowerCase().includes(td.tagFilter.toLowerCase()));
  if (td.sortBy==='read') list.sort((a,b)=>(b.ReadCount||0)-(a.ReadCount||0));
  else if (td.sortBy==='words') list.sort((a,b)=>(b.WordCount||0)-(a.WordCount||0));
  else if (td.sortBy==='chapters') list.sort((a,b)=>(b.ChapterCount||0)-(a.ChapterCount||0));
  td.filtered = list;
}

async function doSearch() {
  const hash = location.hash.slice(1)||'';
  if (hash.startsWith('shelf')) { _shelfFilter = $('searchInput').value.trim(); _applyShelfFilter(); return; }
  const key = $('searchInput').value.trim();
  if (!key) return;
  // Update URL so back button logic detects search results state
  const targetHash = `search?q=${encodeURIComponent(key)}`;
  if (location.hash.slice(1) !== targetHash) {
    location.hash = targetHash;
    return; // hashchange will call router() which calls doSearch() again via the route handler
  }
  S.key = key; S.loading = true;
  saveTabData({ books: [], filtered: [], offset: 0, hasMore: false, sortBy: 'default', tagFilter: '' });
  hideSuggest(); addHistory(key); updateClearBtn(); renderResults();
  try {
    const r = await fetchWithTimeout(`${API}/api/search?key=${encodeURIComponent(key)}&tab_type=${S.tab}`);
    const data = await r.json();
    const td = getTabData();
    td.books = data.code===200 ? (data.data||[]) : [];
    td.hasMore = td.books.length >= 10; applyFilters();
  } catch(e) { getTabData().books = []; }
  S.loading = false; renderResults();
}

function addHistory(key) {
  const data = loadData();
  data.searchHistory = data.searchHistory.filter(s => s !== key);
  data.searchHistory.unshift(key);
  if (data.searchHistory.length > 12) data.searchHistory.pop();
  saveSearchHistory(data.searchHistory);
}

function setSort(s) { const td = getTabData(); td.sortBy = td.sortBy===s?'default':s; applyFilters(); renderResults(); }
function setTag(t) { const td = getTabData(); td.tagFilter = td.tagFilter===t?'':t; applyFilters(); renderResults(); }

async function loadMore() {
  if (S.loading) return;
  const td = getTabData();
  if (!td.hasMore) return;
  S.loading = true; td.offset += 10; renderResults();
  try {
    const r = await fetchWithTimeout(`${API}/api/search?key=${encodeURIComponent(S.key)}&tab_type=${S.tab}&offset=${td.offset}`);
    const data = await r.json();
    const more = data.code===200 ? (data.data||[]) : [];
    td.books = td.books.concat(more);
    td.hasMore = more.length >= 10; applyFilters();
  } catch(e) { td.hasMore = false; }
  S.loading = false; renderResults();
}

// ====== Search suggest + clear ======
function showSuggest() {
  const hash = location.hash.slice(1)||'';
  if (hash.startsWith('shelf')) return;
  const data = loadData();
  const el = $('searchSuggest');
  el.innerHTML = data.searchHistory.length === 0
    ? '<div class="search-suggest-empty">暂无搜索历史</div>'
    : data.searchHistory.map(s => `<div class="search-suggest-item" data-key="${escapeHtml(s)}"><span>${escapeHtml(s)}</span><span class="del" data-del="${escapeHtml(s)}"><i data-lucide="x" width="12" height="12"></i></span></div>`).join('');
  el.classList.add('open');
  refreshIcons(el);
}
function hideSuggest() { $('searchSuggest').classList.remove('open'); }
function filterSuggest(q) {
  q = q.trim();
  if (!q) { showSuggest(); return; }
  const data = loadData();
  const matched = data.searchHistory.filter(s => s.toLowerCase().includes(q.toLowerCase()));
  const el = $('searchSuggest');
  el.innerHTML = matched.length === 0
    ? '<div class="search-suggest-empty">按回车搜索</div>'
    : matched.map(s => `<div class="search-suggest-item" data-key="${escapeHtml(s)}"><span>${escapeHtml(s)}</span><span class="del" data-del="${escapeHtml(s)}"><i data-lucide="x" width="12" height="12"></i></span></div>`).join('');
  el.classList.add('open');
  refreshIcons(el);
}
function quickSearch(key) { $('searchInput').value = key; doSearch(); }
function delHistory(key) { const data = loadData(); data.searchHistory = data.searchHistory.filter(s => s !== key); saveSearchHistory(data.searchHistory); showSuggest(); }

function onSearchInput() { updateClearBtn(); const hash = location.hash.slice(1)||''; if (hash.startsWith('shelf')) { _shelfFilter = $('searchInput').value.trim(); _applyShelfFilter(); } else { filterSuggest($('searchInput').value); } }
function clearSearch() { $('searchInput').value = ''; updateClearBtn(); const hash = location.hash.slice(1)||''; if (hash.startsWith('shelf')) { _shelfFilter = ''; _applyShelfFilter(); $('searchInput').focus(); } else { $('searchInput').focus(); showSuggest(); } }
function updateClearBtn() { $('searchClear').classList.toggle('visible', $('searchInput').value.length > 0); }

// ====== Global event listeners ======

// Suggest clicks
document.addEventListener('mousedown', function(e) {
  const del = e.target.closest('[data-del]');
  if (del) { e.stopPropagation(); delHistory(del.dataset.del); return; }
  const item = e.target.closest('[data-key]');
  if (item && $('searchSuggest').classList.contains('open')) quickSearch(item.dataset.key);
});

// Unified scroll handler (non-reader pages)
window.addEventListener('scroll', function() {
  const hash = location.hash.slice(1) || 'search';
  if (hash.startsWith('reader')) return;
  updateProgress();
  const btn = $('backTop');
  if (window.scrollY > 600) btn.classList.add('visible');
  else btn.classList.remove('visible');
});

// System dark mode auto-follow
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
  if (getTheme() === 'auto') applyTheme('auto');
});

// ====== Init ======
initTheme();
renderTabs();
_profileUser = getCachedUser();
_onlineShelf = getCachedShelf();
router();
_tryAutoLogin();

// Service Worker
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js', {scope: '/'}).catch(() => {});
}
