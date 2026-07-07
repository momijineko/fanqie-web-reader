// ====== Render: Home ======
function renderHome(app) {
  const data = loadData();
  let html = '';
  if (data.readingHistory) {
    const rh = data.readingHistory;
    const pct = rh.totalChapters > 0 ? Math.round((rh.chapterIdx+1)/rh.totalChapters*100) : 0;
    const circumference = 2 * Math.PI * 22;
    const dashOffset = circumference * (1 - pct / 100);
    html += `<div class="continue-card view" onclick="navigate('reader?book_id=${rh.bookId}&chapter_idx=${rh.chapterIdx}')">
      <div class="info"><div class="label">继续阅读</div><div class="title">${escapeHtml(rh.name||'')}</div><div class="chapter">${escapeHtml(rh.chapterName||'')}</div></div>
      <div class="ring"><svg width="52" height="52"><circle class="ring-bg" cx="26" cy="26" r="22"/><circle class="ring-fg" cx="26" cy="26" r="22" stroke-dasharray="${circumference}" stroke-dashoffset="${dashOffset}"/></svg><div class="ring-text">${pct}%</div></div><i data-lucide="chevron-right" class="continue-chev" width="20" height="20"></i></div>`;
  }
  // Discover content — always shown (bookshelf is on its own tab)
  const genreTags = [
    {name:'热门',icon:'🔥'},{name:'玄幻',icon:'🗡️'},{name:'都市',icon:'🏙️'},
    {name:'言情',icon:'💕'},{name:'仙侠',icon:'🔮'},{name:'游戏',icon:'🎮'},
    {name:'历史',icon:'🏰'},{name:'科幻',icon:'🔬'}
  ];
  const quickCats = [
    {name:'都市小说',icon:'🏙️',q:'都市'},{name:'玄幻奇幻',icon:'🗡️',q:'玄幻'},
    {name:'仙侠修真',icon:'🔮',q:'仙侠'},      {name:'历史军事',icon:'🏰',q:'历史'},
    {name:'游戏竞技',icon:'🎮',q:'游戏'},{name:'科幻世界',icon:'🔬',q:'科幻'},
    {name:'悬疑灵异',icon:'🕵️',q:'悬疑'},{name:'古代言情',icon:'💕',q:'言情'}
  ];
  html += `<div class="home-discover view">
    <div class="discover-welcome">
      <div class="welcome-emoji">📚</div>
      <div class="welcome-title">发现好书</div>
      <div class="welcome-desc">搜索书名或作者，找到你的下一本读物</div>
    </div>
    <div class="discover-section">
      <div class="discover-section-title">热门分类</div>
      <div class="discover-tags">${genreTags.map(t => `<span class="discover-tag" onclick="navigate('search?q=${encodeURIComponent(t.name)}')">${t.icon} ${t.name}</span>`).join('')}</div>
    </div>
    <div class="discover-section">
      <div class="discover-section-title">探索发现</div>
      <div class="discover-grid">${quickCats.map(c => `<div class="discover-card" onclick="navigate('search?q=${encodeURIComponent(c.q)}')"><div class="discover-card-icon">${c.icon}</div><div class="discover-card-name">${c.name}</div></div>`).join('')}</div>
    </div>
    <div class="discover-tip">
      <div class="discover-tip-icon">💡</div>
      <div class="discover-tip-text">搜索你喜欢的小说开始阅读</div>
    </div>
  </div>`;
  app.innerHTML = html;
  refreshIcons(app);
}

// ====== Render: Search results ======
function renderResults(app) {
  app = app || $('app');
  const td = getTabData();
  if (td.books.length === 0 && !S.loading) { renderHome(app); return; }
  if (td.books.length === 0 && S.loading) { app.innerHTML = skeletonResults(5); return; }
  const display = td.filtered.length > 0 ? td.filtered : td.books;
  const allTags = [...new Set(td.books.flatMap(b => (b.Tags||'').split(',').map(t=>t.trim()).filter(Boolean)))];
  let html = `<div class="search-controls view"><span class="search-result-info">${td.books.length} 条</span>`;
  if (td.filtered.length !== td.books.length) html += `<span class="search-result-info">· 筛选 ${td.filtered.length}</span>`;
  for (const s of [{key:'default',label:'默认'},{key:'read',label:'热度'},{key:'words',label:'字数'},{key:'chapters',label:'章节'}]) {
    html += `<span class="sort-btn${td.sortBy===s.key?' active':''}" onclick="setSort('${s.key}')">${s.label}</span>`;
  }
  html += '</div>';
  if (allTags.length > 0) {
    html += '<div class="search-controls view" style="margin-top:-4px">';
    html += allTags.map(t => `<span class="sort-btn${td.tagFilter===t?' active':''}" onclick="setTag('${escapeHtml(t)}')">${escapeHtml(t)}</span>`).join('');
    html += '</div>';
  }
  html += '<div class="book-list view">';
  for (const book of display) {
    const tags = (book.Tags||'').split(',').map(t=>t.trim()).filter(Boolean);
    const status = book.Status||'';
    const wc = book.WordCount ? (book.WordCount/10000).toFixed(1)+'万字' : '';
    const score = parseFloat(book.Score);
    const stars = score > 0 ? '<i data-lucide="star" width="12" height="12" style="vertical-align:-1px"></i>'+(score > 10 ? (score/10).toFixed(1) : score.toFixed(1)) : '';
    html += `<div class="book-card" onclick="navigate('detail?book_id=${book.BookID}')">
      <img class="book-cover" src="${book.ThumbUrl||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'">
      <div class="book-info">
        <div class="book-title">${escapeHtml(book.Name||'')}${status?`<span class="status-badge ${status==='连载中'?'ongoing':'finished'}">${status}</span>`:''}</div>
        <div class="book-author">${escapeHtml(book.Author||'')} ${stars?'· '+stars:''}</div>
        <div class="book-desc">${escapeHtml(book.Desc||'')}</div>
        <div class="book-meta">${[book.ChapterCount?book.ChapterCount+'章':'', wc, ...tags.map(t=>`<span class="tag-pill">${escapeHtml(t)}</span>`)].filter(Boolean).join(' · ')}</div>
      </div></div>`;
  }
  html += '</div>';
  if (td.books.length > 0) html += `<div class="load-more view"><button onclick="loadMore()" ${S.loading?'disabled':''}>${S.loading?'加载中...':td.hasMore?'加载更多':'没有更多了'}</button></div>`;
  app.innerHTML = html;
  refreshIcons(app);
}

// ====== Render: Shelf ======
let _shelfFilter = '';
let _shelfTab = 'local';

function renderShelf(app, filter) {
  if (filter !== undefined) _shelfFilter = filter;
  const data = loadData();
  const rh = data.readingHistory;

  let html = `<div class="home-section view">`;
  if (_profileUser) {
    $('pageTitle').textContent = `我的书架 · ${_onlineShelf.length} 本`;
    html += _renderCloudShelf(rh);
  } else {
    $('pageTitle').textContent = `我的书架 · ${data.shelf.length} 本`;
    const shelf = data.shelf;
    if (!shelf.length) html += '<div class="shelf-empty"><div class="icon"><i data-lucide="book-open" width="48" height="48"></i></div><div>还没有收藏的书籍<br><span style="font-size:12px">在书籍详情页点击收藏即可加入书架</span></div><button class="shelf-empty-cta" onclick="navigate(\'search\')">去书城逛逛</button></div>';
    else {
      html += _renderLocalShelf(shelf, rh);
    }
  }
  html += '</div>';
  app.innerHTML = html;
  refreshIcons(app);
  _setupLongPress(app);
  _applyShelfFilter();
}

function _applyShelfFilter() {
  const filter = (_shelfFilter || '').toLowerCase();
  let visible = 0;
  document.querySelectorAll('.shelf-grid-item').forEach(item => {
    const isPopup = !!item.closest('.shelf-folder-overlay');
    if (isPopup) return;
    const isFolder = item.classList.contains('shelf-folder');
    var _n = item.querySelector('.name');
    const name = (_n ? _n.textContent : '').toLowerCase();
    const names = (item.dataset.names || '').toLowerCase();
    const match = name.includes(filter) || names.includes(filter);
    if (!filter) {
      item.classList.remove('filter-hidden');
      if (!isFolder) visible++;
      else visible += (item.dataset.names ? item.dataset.names.split(',').length : 0);
    } else {
      item.classList.toggle('filter-hidden', !match);
      if (match && !isFolder) visible++;
      else if (match && isFolder) visible += (item.dataset.names ? item.dataset.names.split(',').length : 0);
    }
  });
  document.querySelectorAll('.shelf-folder-panel .shelf-grid-item').forEach(item => {
    var _n = item.querySelector('.name');
    const name = (_n ? _n.textContent : '').toLowerCase();
    item.classList.toggle('filter-hidden', !!filter && !name.includes(filter));
  });
  $('pageTitle').textContent = filter ? `筛选 · ${visible} 本` : (_profileUser ? `我的书架 · ${_onlineShelf.length} 本` : `我的书架 · ${loadData().shelf.length} 本`);
}

function _setupPopupLongPress(overlay) {
  let timer = null;
  overlay.addEventListener('pointerdown', e => {
    const item = e.target.closest('.shelf-grid-item');
    if (!item || !item.dataset.bookId) return;
    timer = setTimeout(() => _showBookActions(item), 500);
  });
  overlay.addEventListener('pointerup', () => { clearTimeout(timer); });
  overlay.addEventListener('pointercancel', () => { clearTimeout(timer); });
}

let _longPressTimer = null;
let _longPressSetup = false;
function _setupLongPress(root) {
  if (_longPressSetup) return;
  _longPressSetup = true;

  root.addEventListener('pointerdown', e => {
    const item = e.target.closest('.shelf-grid-item');
    if (!item) return;
    _longPressTimer = setTimeout(() => {
      item.classList.add('show-delete');
      _showBookActions(item);
    }, 500);
  });

  root.addEventListener('pointerup', () => { clearTimeout(_longPressTimer); });
  root.addEventListener('pointercancel', () => { clearTimeout(_longPressTimer); });
  root.addEventListener('contextmenu', e => {
    if (e.target.closest('.shelf-grid-item.show-delete')) e.preventDefault();
  });
  root.addEventListener('click', () => {
    document.querySelectorAll('.shelf-grid-item.show-delete').forEach(it => it.classList.remove('show-delete'));
  });
}

function _showBookActions(item) {
  const bookId = item.dataset.bookId;
  var _n = item.querySelector('.name');
  const bookName = _n ? _n.textContent : '';
  if (!bookId) return;

  const isCloud = !!_profileUser;
  const groups = [];
  const seen = new Set();
  let currentGid = null;
  let currentGname = '';

  if (isCloud) {
    for (const b of _onlineShelf) {
      if (b.GroupID && !seen.has(b.GroupID)) {
        seen.add(b.GroupID);
        groups.push({ id: b.GroupID, name: b.GroupName || '未命名分组' });
      }
    }
    const cb = _onlineShelf.find(b => b.BookID === bookId);
    if (cb) { currentGid = cb.GroupID; currentGname = cb.GroupName || ''; }
  } else {
    const localGroups = getLocalGroups();
    for (const g of localGroups) {
      if (!seen.has(g.id)) {
        seen.add(g.id);
        groups.push({ id: g.id, name: g.name });
      }
    }
    const lg = findBookGroup(bookId);
    if (lg) { currentGid = lg.id; currentGname = lg.name || ''; }
  }

  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.style.zIndex = '600';
  const ungroupedHtml = currentGid ? `<div class="profile-setting-item" onclick="_doMoveBook('${bookId}','',${isCloud})"><span>移出${escapeHtml(currentGname)}</span><i data-lucide="chevron-right" width="16" height="16" class="profile-arrow"></i></div>` : '';
  let ghtml = groups.filter(g => g.id !== currentGid).map(g => `<div class="profile-setting-item" onclick="_doMoveBook('${bookId}','${g.id}',${isCloud})"><span>${escapeHtml(g.name)}</span><i data-lucide="chevron-right" width="16" height="16" class="profile-arrow"></i></div>`).join('');
  const deleteAction = isCloud
    ? `this.closest('.modal-overlay').remove();confirmRemoveCloud('${bookId}')`
    : `this.closest('.modal-overlay').remove();confirmRemoveShelf('${bookId}')`;
  overlay.innerHTML = `<div class="modal-content" style="max-width:320px">
    <div class="modal-title" style="margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escapeHtml(bookName)}</div>
    <div class="profile-section-group" style="margin-bottom:12px">
      ${ungroupedHtml}
      ${ghtml}
      <div class="profile-setting-item" onclick="_newGroupPrompt('${bookId}',this.closest('.modal-overlay'),${isCloud})"><i data-lucide="plus" width="18" height="18"></i><span>新建分组</span></div>
    </div>
    <div class="profile-section-group">
      <div class="profile-setting-item" onclick="${deleteAction}" style="color:var(--accent)"><span>移出书架</span></div>
    </div>
  </div>`;
  overlay.addEventListener('click', e => { if (e.target === overlay) overlay.remove(); });
  document.body.appendChild(overlay);
  refreshIcons(overlay);
}

function _doMoveBook(bookId, groupId, isCloud) {
  const overlay = document.querySelector('.modal-overlay[style*="600"]');
  if (overlay) overlay.remove();
  if (isCloud) {
    let gname = '';
    if (groupId) {
      const g = _onlineShelf.find(b => b.GroupID === groupId);
      gname = g ? g.GroupName || '' : '';
    }
    _moveBookToGroup(bookId, groupId, gname);
  } else {
    moveBookToLocalGroup(bookId, groupId);
    renderShelf($('app'));
    showToast('已移出分组');
  }
}

async function _moveBookToGroup(bookId, groupId, groupName) {
  try {
    const body = { book_id: bookId };
    if (groupId) { body.group_id = groupId; body.group_name = groupName; }
    const r = await fetch(`${API}/api/user/bookshelf/move`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body),
    });
    if (r.ok) {
      const b = _onlineShelf.find(b => b.BookID === bookId);
      if (b) {
        b.GroupID = groupId;
        b.GroupName = groupName || '';
      }
      saveCachedShelf(_onlineShelf);
      clearFolderCache();
      if ($('app').firstElementChild && $('app').firstElementChild.classList.contains('home-section')) renderShelf($('app'));
      showToast('已移动');
    }
  } catch(e) { showToast('移动失败'); }
}

async function _moveBookToGroupWithName(bookId, name) {
  try {
    const r = await fetch(`${API}/api/user/bookshelf/move`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ book_id: bookId, group_name: name }),
    });
    if (r.ok) {
      await _loadOnlineShelf();
      clearFolderCache();
      if ($('app').firstElementChild && $('app').firstElementChild.classList.contains('home-section')) renderShelf($('app'));
      showToast(`已移至「${name}」`);
    }
  } catch(e) { showToast('创建失败'); }
}

function _newGroupPrompt(bookId, overlay, isCloud) {
  overlay.remove();
  const ov = document.createElement('div');
  ov.className = 'modal-overlay';
  ov.style.zIndex = '600';
  ov.innerHTML = `<div class="modal-content" style="max-width:300px">
    <div class="modal-title">新建分组</div>
    <input class="modal-input" id="newGroupInput" placeholder="输入分组名称" style="margin-bottom:12px">
    <div class="modal-actions">
      <button class="btn-outline" onclick="this.closest('.modal-overlay').remove()">取消</button>
      <button class="btn-primary" onclick="_doCreateGroup('${bookId}',${isCloud})">创建</button>
    </div>
  </div>`;
  ov.addEventListener('click', e => { if (e.target === ov) ov.remove(); });
  document.body.appendChild(ov);
  setTimeout(function() { var el = $('newGroupInput'); if (el) el.focus(); }, 100);
}

function _doCreateGroup(bookId, isCloud) {
  const input = $('newGroupInput');
  const name = (input ? input.value : '').trim();
  if (!name) return;
  var _ov = input.closest('.modal-overlay');
  if (_ov) _ov.remove();
  if (isCloud) {
    _moveBookToGroupWithName(bookId, name);
  } else {
    createLocalGroup(name, bookId);
    renderShelf($('app'));
    showToast(`已创建「${name}」`);
  }
}


function bookBadge(b) {
  const cc = b.ChapterCount || 0;
  const lastNum = _chapterNum(b.LastReadChapter);
  if (b.Status === '已完结') {
    if (lastNum > 0 && cc > 0 && lastNum >= cc) return '<span class="cover-badge finished">已完结</span>';
    if (lastNum > 0 && cc > 0) return `<span class="cover-badge updated">${cc - lastNum}章未读</span>`;
    return '<span class="cover-badge finished">已完结</span>';
  }
  if (b.UpdateStopped) return '<span class="cover-badge stopped">已断更</span>';
  if (lastNum > 0 && cc > 0 && lastNum >= cc) return '<span class="cover-badge readall">已看完</span>';
  if (b.Status === '连载中' && b.LastUpdateTime > 0 && b.LastReadTime > 0 && b.LastUpdateTime > b.LastReadTime) return '<span class="cover-badge updated">有更新</span>';
  if (b.Status === '连载中') return '<span class="cover-badge ongoing">连载中</span>';
  return '';
}

function _chapterNum(text) {
  const m = (text || '').match(/第(\d+)章/);
  return m ? parseInt(m[1]) : 0;
}

function _localBadge(bookId) {
  const cloud = _onlineShelf ? _onlineShelf.find(cb => String(cb.BookID) === String(bookId)) : null;
  if (cloud) return bookBadge(cloud);
  const prog = getBookProgress(bookId);
  const d = cache.detail[bookId];
  const dd = d && d.detail ? d.detail.data || d.detail : null;
  const status = dd ? (String(dd.creation_status) === '0' ? '已完结' : String(dd.creation_status) === '1' ? '连载中' : '') : '';
  if (!status && !prog) return '';
  return bookBadge({
    Status: status,
    ChapterCount: (dd ? dd.serial_count : 0) || (d ? d.chapters.length : 0) || (prog ? prog.totalChapters : 0),
    LastReadChapter: prog ? prog.chapterTitle || '' : '',
    LastReadTime: prog ? Math.floor((prog.updatedAt || 0) / 1000) : 0,
    LastUpdateTime: dd ? (dd.last_chapter_update_time || 0) : 0,
    UpdateStopped: dd ? String(dd.update_stop) === '1' : false,
  });
}

function _renderLocalShelf(shelf, rh) {
  const localGroups = getLocalGroups();
  const groups = new Map();
  const ungrouped = [];
  for (const b of shelf) {
    const g = findBookGroup(b.bookId);
    if (g) {
      if (!groups.has(g.id)) groups.set(g.id, { name: g.name, books: [], gid: g.id });
      groups.get(g.id).books.push(b);
    } else {
      ungrouped.push(b);
    }
  }
  const itemTime = b => b.addedAt || 0;
  const groupMaxTime = g => Math.max(...g.books.map(itemTime));
  const items = [];
  for (const b of ungrouped) items.push({ type: 'book', time: itemTime(b), data: b, isLocal: true });
  for (const [gid, g] of groups) items.push({ type: 'folder', time: groupMaxTime(g), gid, g: { name: g.name, books: g.books }, isLocal: true });
  items.sort((a, b) => b.time - a.time);

  let html = '<div class="shelf-grid">';
  for (const item of items) {
    if (item.type === 'book') {
      const b = item.data;
      const sp = rh && rh.bookId === b.bookId && rh.totalChapters > 0 ? Math.round((rh.chapterIdx+1)/rh.totalChapters*100) : 0;
      const badgeHtml = _localBadge(b.bookId);
      html += `<div class="shelf-grid-item" onclick="navigate('detail?book_id=${b.bookId}')" data-book-id="${b.bookId}" data-name="${escapeHtml(b.name||'')}"><div class="cover-wrap">${badgeHtml}<img src="${b.thumb||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'"></div><div class="name">${escapeHtml(b.name||'')}</div>${sp?`<div class="progress-text">读到第${rh.chapterIdx+1}章 · ${sp}%</div>`:'<div class="progress-text">&nbsp;</div>'}</div>`;
    } else {
      const gid = item.gid, g = item.g;
      const previews = g.books.slice(0, 4);
      let coverInner = '';
      if (previews.length === 1) {
        coverInner = `<img src="${previews[0].thumb||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'">`;
      } else {
        coverInner = '<div class="folder-cover-grid">';
        for (let i = 0; i < 4; i++) {
          if (previews[i]) {
            coverInner += `<img src="${previews[i].thumb||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'">`;
          } else {
            coverInner += '<div class="folder-cover-empty"></div>';
          }
        }
        coverInner += '</div>';
      }
      html += `<div class="shelf-grid-item shelf-folder" onclick="_openLocalFolder('${gid}')" data-names="${g.books.map(b => escapeHtml(b.name||'')).join(',')}"><div class="cover-wrap">${coverInner}</div><div class="name">${escapeHtml(g.name)}</div><div class="progress-text">${g.books.length} 本</div></div>`;
    }
  }
  html += '</div>';
  html += `<script id="_localFoldersData" type="application/json">${JSON.stringify(Object.fromEntries([...groups.entries()].map(([gid,g])=>[gid,{name:g.name,books:g.books}])))}</script>`;
  return html;
}

function _openLocalFolder(gid) {
  const dataEl = document.getElementById('_localFoldersData');
  if (!dataEl) return;
  let groups;
  try { groups = JSON.parse(dataEl.textContent); } catch(e) { return; }
  const g = groups[gid];
  if (!g) return;
  let booksHtml = '';
  for (const b of g.books) {
    const rh = loadData().readingHistory;
    const sp = rh && rh.bookId === b.bookId && rh.totalChapters > 0 ? Math.round((rh.chapterIdx+1)/rh.totalChapters*100) : 0;
    booksHtml += `<div class="shelf-grid-item" onclick="closeShelfFolder();navigate('detail?book_id=${b.bookId}')" data-name="${escapeHtml(b.name||'')}"><div class="cover-wrap"><img src="${b.thumb||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'"></div><div class="name">${escapeHtml(b.name||'')}</div>${sp?`<div class="progress-text">读到第${rh.chapterIdx+1}章 · ${sp}%</div>`:'<div class="progress-text">&nbsp;</div>'}</div>`;
  }
  const overlay = document.createElement('div');
  overlay.className = 'shelf-folder-overlay';
  overlay.innerHTML = `<div class="shelf-folder-panel" onclick="event.stopPropagation()">
    <div class="shelf-folder-panel-header">
      <div class="shelf-folder-panel-title">${escapeHtml(g.name)}</div>
      <button class="shelf-folder-close" onclick="closeShelfFolder()"><i data-lucide="x" width="18" height="18"></i></button>
    </div>
    <div class="shelf-grid">${booksHtml}</div>
  </div>`;
  overlay.addEventListener('click', closeShelfFolder);
  overlay.addEventListener('touchmove', e => { if (e.target.closest('.shelf-folder-panel')) return; e.preventDefault(); }, { passive: false });
  document.body.appendChild(overlay);
  document.body.style.overflow = 'hidden';
  _activeFolderOverlay = overlay;
  _activeFolderGid = gid;
  refreshIcons(overlay);
  _applyShelfFilter();
  _setupPopupLongPress(overlay);
}

function _renderCloudShelf(rh) {
  if (_onlineShelfLoading) return '<div class="loading" style="padding:20px">加载中...</div>';
  if (!_onlineShelf.length) return '<div class="shelf-empty"><div class="icon"><i data-lucide="cloud" width="32" height="32"></i></div><div>在线书架为空</div></div>';
  let html = '';
  const groups = new Map();
  const ungrouped = [];
  for (const b of _onlineShelf) {
    const gid = b.GroupID || '';
    if (!gid) { ungrouped.push(b); continue; }
    const gname = b.GroupName || '未分组';
    if (!groups.has(gid)) groups.set(gid, { name: gname, books: [] });
    groups.get(gid).books.push(b);
  }
  const itemTime = b => Math.max(b.LastReadTime || 0, b.LastUpdateTime || 0);
  const groupMaxTime = g => Math.max(...g.books.map(itemTime));
  const items = [];
  for (const b of ungrouped) items.push({ type: 'book', time: itemTime(b), data: b });
  for (const [gid, g] of groups) items.push({ type: 'folder', time: groupMaxTime(g), gid, g });
  items.sort((a, b) => b.time - a.time);
  html += '<div class="shelf-grid">';
  for (const item of items) {
    if (item.type === 'book') {
      const b = item.data;
      const progressHtml = `<div class="progress-text">${b.LastReadChapter ? escapeHtml(b.LastReadChapter) : '&nbsp;'}</div>`;
      html += `<div class="shelf-grid-item" onclick="navigate('detail?book_id=${b.BookID}')" data-book-id="${b.BookID}" data-name="${escapeHtml(b.Name||'')}"><div class="cover-wrap">${bookBadge(b)}<img src="${b.ThumbUrl||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'"></div><div class="name">${escapeHtml(b.Name||'')}</div>${progressHtml}</div>`;
    } else {
      const gid = item.gid, g = item.g;
      const previews = g.books.slice(0, 4);
      let coverInner = '';
      if (previews.length === 1) {
        coverInner = `<img src="${previews[0].ThumbUrl||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'">`;
      } else {
        coverInner = '<div class="folder-cover-grid">';
        for (let i = 0; i < 4; i++) {
          if (previews[i]) {
            coverInner += `<img src="${previews[i].ThumbUrl||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'">`;
          } else {
            coverInner += '<div class="folder-cover-empty"></div>';
          }
        }
        coverInner += '</div>';
      }
      const bookNames = g.books.map(b => escapeHtml(b.Name||'')).join(',');
      html += `<div class="shelf-grid-item shelf-folder" onclick="openShelfFolder('${gid}')" data-names="${bookNames}"><div class="cover-wrap">${coverInner}</div><div class="name">${escapeHtml(g.name)}</div><div class="progress-text">${g.books.length} 本</div></div>`;
    }
  }
  html += '</div>';
  html += `<script id="_shelfFoldersData" type="application/json">${JSON.stringify(Array.from(groups.entries()).map(([gid,g])=>[gid,{name:g.name,books:g.books}]))}</script>`;
  return html;
}
function removeShelf(bid) { const d = loadData(); d.shelf = d.shelf.filter(b => b.bookId !== bid); saveShelf(d.shelf); renderShelf($('app')); }
function confirmRemoveShelf(bid) {
  showConfirm('移除书籍', '确认从书架移除此书？', () => removeShelf(bid));
}

function confirmRemoveCloud(bid) {
  showConfirm('移除书籍', '确认从云端书架移除此书？', async () => {
    try {
      const r = await fetch(`${API}/api/user/bookshelf/remove`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ book_id: bid }),
      });
      const d = await r.json();
      if (d.code === 200) {
        _onlineShelf = _onlineShelf.filter(b => b.BookID !== bid);
        saveCachedShelf(_onlineShelf);
        clearFolderCache();
        if ($('app').firstElementChild && $('app').firstElementChild.classList.contains('home-section')) renderShelf($('app'));
        showToast('已移除');
      } else {
        showToast('移除失败');
      }
    } catch(e) { showToast('网络错误'); }
  });
}

function showConfirm(title, msg, onConfirm) {
  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.style.zIndex = '600';
  overlay.innerHTML = `<div class="modal-content" style="max-width:300px;text-align:center">
    <div class="modal-title">${title}</div>
    <div style="color:var(--text-secondary);font-size:14px;margin-bottom:20px">${msg}</div>
    <div class="modal-actions" style="justify-content:center">
      <button class="btn-outline" id="confirmCancel">取消</button>
      <button class="btn-primary" id="confirmOk">确定</button>
    </div>
  </div>`;
  overlay.addEventListener('click', (e) => { if (e.target === overlay) overlay.remove(); });
  overlay.querySelector('#confirmCancel').onclick = () => overlay.remove();
  overlay.querySelector('#confirmOk').onclick = () => { overlay.remove(); onConfirm(); };
  document.body.appendChild(overlay);
}
// ====== Render: Profile ======
let _profileUser = null;
let _onlineShelf = [];
let _onlineShelfLoading = false;
let _autoLoginPending = true;

async function renderProfile(app) {

  if (!_profileUser) {
    app.innerHTML = `<div class="profile-page view">
      <div class="profile-login-section">
        <div class="profile-avatar-placeholder"><i data-lucide="user" width="48" height="48"></i></div>
        <div class="profile-login-info">
          <div class="profile-login-title">未登录</div>
          <div class="profile-login-desc">登录后可同步在线书架</div>
        </div>
      </div>
      </div>
      <div class="profile-section">
        <div class="profile-section-title">账号</div>
        <div class="profile-section-group">
          <div class="profile-setting-item" onclick="showCookieModal()">
            <i data-lucide="key" width="18" height="18"></i>
            <span>Cookie 登录</span>
            <i data-lucide="chevron-right" width="16" height="16" class="profile-arrow"></i>
          </div>
        </div>
      </div>
      ${_settingsHtml()}
      ${_aboutHtml()}
    </div>`;
    refreshIcons(app);
    return;
  }

  const u = _profileUser;
  const avatarHtml = u.avatar_url
    ? `<img class="profile-avatar-img" src="${escapeHtml(u.avatar_url)}" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'"><div class="profile-avatar-placeholder" style="display:none"><i data-lucide="user" width="48" height="48"></i></div>`
    : `<div class="profile-avatar-placeholder"><i data-lucide="user" width="48" height="48"></i></div>`;

  app.innerHTML = `<div class="profile-page view">
    <div class="profile-login-section logged-in">
      ${avatarHtml}
      <div class="profile-login-info">
        <div class="profile-login-title">${escapeHtml(u.user_name || '用户')}</div>
        <div class="profile-login-desc">已登录</div>
      </div>
      <button class="profile-logout-btn" onclick="doLogout()">退出登录</button>
    </div>
    <div class="profile-section">
      <div class="profile-section-title">账号</div>
      <div class="profile-section-group">
        <div class="profile-setting-item" onclick="showCookieModal()">
          <i data-lucide="key" width="18" height="18"></i>
          <span>更新 Cookie</span>
          <i data-lucide="chevron-right" width="16" height="16" class="profile-arrow"></i>
        </div>
      </div>
    </div>
    ${_settingsHtml()}
    ${_aboutHtml()}
  </div>`;
  refreshIcons(app);
  _fetchVersion();
}

async function _fetchVersion() {
  const el = $('aboutVersion');
  if (!el) return;
  try {
    const r = await fetch(`${API}/api/version`);
    const data = await r.json();
    if (data.version) el.textContent = data.version;
  } catch(e) {}
}

function _settingsHtml() {
  const bytes = JSON.stringify(localStorage).length;
  let cacheSize;
  if (bytes < 1024) cacheSize = `${bytes} B`;
  else if (bytes < 1024 * 1024) cacheSize = `${(bytes / 1024).toFixed(1)} KB`;
  else cacheSize = `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  const curTheme = getTheme();
  const einkOn = isEink();
  const themeRow = einkOn ? '' : `<div class="profile-setting-item" style="cursor:default">
        <i data-lucide="palette" width="18" height="18"></i>
        <span>主题</span>
        <div class="theme-swatches">${THEMES.map(t => 
    `<button class="bg-swatch swatch-${t.id}${curTheme===t.id?' active':''}" onclick="applyThemeFrom(event,'${t.id}')" title="${t.name}"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>`
  ).join('')}</div>
      </div>`;
  return `<div class="profile-section">
    <div class="profile-section-title">设置</div>
    <div class="profile-section-group">
      ${themeRow}
      <div class="profile-setting-item" onclick="toggleEink()">
        <i data-lucide="tablet" width="18" height="18"></i>
        <span>墨水屏模式</span>
        <label class="toggle-switch" onclick="event.stopPropagation()">
          <input type="checkbox" ${einkOn?'checked':''} onchange="toggleEink()">
          <span class="toggle-slider"></span>
        </label>
      </div>
      <div class="profile-setting-item" onclick="clearAllCache()">
        <i data-lucide="trash-2" width="18" height="18"></i>
        <span>清除缓存</span>
        <span class="profile-stat-value">${cacheSize}</span>
        <i data-lucide="chevron-right" width="16" height="16" class="profile-arrow"></i>
      </div>
    </div>
  </div>`;
}

function _aboutHtml() {
  return `<div class="profile-section about-section">
    <div class="about-card">
      <div class="about-icon"><i data-lucide="book-open" width="40" height="40"></i></div>
      <div class="about-name">番茄小说阅读器</div>
      <div class="about-version" id="aboutVersion">v1.0</div>
      <div class="about-desc">本地缓存阅读 · 云端同步进度</div>
      <a class="about-github" href="https://github.com/momijineko/fanqie-web-reader" target="_blank" rel="noopener">
        <i data-lucide="github" width="16" height="16"></i>
        <span>Star on GitHub</span>
      </a>
    </div>
  </div>`;
}

function clearAllCache() {
  if (!confirm('确认清除数据缓存？本地书架、阅读进度等将被清除，但主题、字体等设置会保留。')) return;
  const keep = ['readerTheme', 'readerLightTheme', 'readerFont', 'fontSize', 'lineHeight', 'readMode'];
  const saved = {};
  for (const k of keep) { const v = localStorage.getItem(k); if (v !== null) saved[k] = v; }
  localStorage.clear();
  for (const k of keep) { if (saved[k] !== undefined) localStorage.setItem(k, saved[k]); }
  location.reload();
}

let _activeFolderOverlay = null;
let _activeFolderGid = null;

function openShelfFolder(gid) {
  closeShelfFolder();
  const dataEl = document.getElementById('_shelfFoldersData');
  if (!dataEl) return;
  let groups;
  try { groups = new Map(JSON.parse(dataEl.textContent)); } catch(e) { return; }
  const g = groups.get(gid);
  if (!g) return;
  let booksHtml = '';
  for (const b of g.books) {
    const progressHtml = `<div class="progress-text">${b.LastReadChapter ? escapeHtml(b.LastReadChapter) : '&nbsp;'}</div>`;
    booksHtml += `<div class="shelf-grid-item" onclick="closeShelfFolder();navigate('detail?book_id=${b.BookID}')" data-book-id="${b.BookID}" data-name="${escapeHtml(b.Name||'')}"><div class="cover-wrap">${bookBadge(b)}<img src="${b.ThumbUrl||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'"></div><div class="name">${escapeHtml(b.Name||'')}</div>${progressHtml}</div>`;
  }
  const overlay = document.createElement('div');
  overlay.className = 'shelf-folder-overlay';
  overlay.innerHTML = `<div class="shelf-folder-panel" onclick="event.stopPropagation()">
    <div class="shelf-folder-panel-header">
      <div class="shelf-folder-panel-title">${escapeHtml(g.name)}</div>
      <button class="shelf-folder-close" onclick="closeShelfFolder()"><i data-lucide="x" width="18" height="18"></i></button>
    </div>
    <div class="shelf-grid">${booksHtml}</div>
  </div>`;
  overlay.addEventListener('click', closeShelfFolder);
  overlay.addEventListener('touchmove', (e) => { if (e.target.closest('.shelf-folder-panel')) return; e.preventDefault(); }, { passive: false });
  document.body.appendChild(overlay);
  document.body.style.overflow = 'hidden';
  _activeFolderOverlay = overlay;
  _activeFolderGid = gid;
  refreshIcons(overlay);
  _applyShelfFilter();
  _setupPopupLongPress(overlay);
}

function closeShelfFolder() {
  if (!_activeFolderOverlay) { document.body.style.overflow = ''; return; }
  const o = _activeFolderOverlay;
  _activeFolderOverlay = null;
  _activeFolderGid = null;
  o.classList.add('closing');
  o.addEventListener('animationend', () => { o.remove(); }, { once: true });
  document.body.style.overflow = '';
}

function clearFolderCache() {
  if (_activeFolderOverlay) { _activeFolderOverlay.remove(); }
  _activeFolderOverlay = null;
  _activeFolderGid = null;
}

async function _tryAutoLogin() {
  try {
    const r = await fetch(`${API}/api/user/info`);
    const data = await r.json();
    if (data.code === 200 && data.data) {
      const wasLoggedIn = !!_profileUser;
      _profileUser = data.data;
      saveCachedUser(_profileUser);
      _autoLoginPending = false;
      await _loadOnlineShelf();
      router();
      return;
    }
  } catch(e) {}
  const wasLoggedIn = !!_profileUser;
  _profileUser = null;
  clearCachedUser();
  _autoLoginPending = false;
  if (wasLoggedIn) router();
}

async function _loadOnlineShelf() {
  _onlineShelfLoading = true;
  clearFolderCache();
  try {
    const r = await fetch(`${API}/api/user/bookshelf`);
    const data = await r.json();
    if (data.code === 200) {
      _onlineShelf = data.data || [];
      saveCachedShelf(_onlineShelf);
      _syncCloudProgress();
    }
  } catch(e) {}
  _onlineShelfLoading = false;
}

function _syncCloudProgress() {
  const all = JSON.parse(localStorage.getItem('bookProgress') || '{}');
  for (const b of _onlineShelf) {
    const cloudIdx = b.ReadChapterIdx;
    const cloudItemId = b.ReadItemId;
    const hasCloud = cloudItemId && cloudItemId !== '0' && cloudIdx >= 0 && b.LastReadTime;
    const local = all[b.BookID];
    if (!hasCloud) {
      if (local && local.fromCloud) delete all[b.BookID];
      continue;
    }
    const cloudTs = b.LastReadTime * 1000;
    if (!local || local.fromCloud || cloudTs > (local.updatedAt || 0)) {
      all[b.BookID] = {
        chapterIdx: cloudIdx,
        chapterId: cloudItemId,
        chapterTitle: b.LastReadChapter || '',
        totalChapters: b.ChapterCount || 0,
        updatedAt: cloudTs,
        fromCloud: true,
      };
    }
  }
  localStorage.setItem('bookProgress', JSON.stringify(all));
}

function showCookieModal() {
  const overlay = document.createElement('div');
  overlay.id = 'cookieModal';
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `<div class="modal-content">
    <div class="modal-title">Cookie 登录</div>
    <div class="modal-guide">
      <div class="modal-step">
        <div class="modal-step-num">1</div>
        <div>在浏览器打开 <a href="https://fanqienovel.com" target="_blank" rel="noopener">fanqienovel.com</a> 并登录账号</div>
      </div>
      <div class="modal-step">
        <div class="modal-step-num">2</div>
        <div>按 <kbd>F12</kbd> 打开开发者工具 → <b>Network</b> 标签 → 刷新页面</div>
      </div>
      <div class="modal-step">
        <div class="modal-step-num">3</div>
        <div>点击 <b>Network</b> 列表中路径以 <code>/api</code> 开头的请求 → <b>Headers</b> → <b>Request Headers</b> → 找到 <code>Cookie:</code> 行 → 复制其值</div>
      </div>
      <div class="modal-step">
        <div class="modal-step-num">4</div>
        <div>将复制的完整 Cookie 字符串粘贴到下方</div>
      </div>
      <div class="modal-tip">提示：Cookie 仅保存在你本地服务端，不会上传第三方。登录后可在「我的」页面查看在线书架。</div>
    </div>
    <textarea id="cookieInput" class="modal-input" placeholder="粘贴完整 Cookie 字符串（以 key=value; key2=value2 格式）..." rows="4"></textarea>
    <div class="modal-actions">
      <button class="btn-outline" onclick="closeCookieModal()">取消</button>
      <button class="btn-primary" onclick="doCookieLogin()">登录</button>
    </div>
  </div>`;
  overlay.addEventListener('click', (e) => { if (e.target === overlay) closeCookieModal(); });
  document.body.appendChild(overlay);
}

function closeCookieModal() {
  const m = $('cookieModal');
  if (m) m.remove();
}

async function doCookieLogin() {
  const input = $('cookieInput');
  let cookie = (input ? input.value : '').trim();
  cookie = cookie.replace(/^['"]|['"]$/g, '');
  if (!cookie) { showToast('请输入 Cookie'); return; }
  try {
    const r = await fetch(`${API}/api/user/cookie`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ cookie }),
    });
    const data = await r.json();
    if (data.code === 200) {
      closeCookieModal();
      showToast('Cookie 已保存，验证中...');
      const infoR = await fetch(`${API}/api/user/info`);
      const infoData = await infoR.json();
      if (infoData.code === 200 && infoData.data) {
        _profileUser = infoData.data;
        saveCachedUser(_profileUser);
        _autoLoginPending = false;
        _loadOnlineShelf();
        showToast('登录成功 · 云端与本地书架独立存储');
      } else {
        showToast('Cookie 无效，请检查是否已登录');
        await fetch(`${API}/api/user/cookie`, { method: 'DELETE' });
      }
    } else {
      showToast('保存失败');
    }
  } catch(e) {
    showToast('网络错误');
  }
}

async function doLogout() {
  try {
    await fetch(`${API}/api/user/cookie`, { method: 'DELETE' });
  } catch(e) {}
  _profileUser = null;
  _onlineShelf = [];
  clearCachedUser();
  clearCachedShelf();
  clearFolderCache();
  renderProfile($('app'));
  showToast('已退出登录 · 本地书架数据不受影响');
}

// ====== Render: Detail ======
function renderDetail(app, bid) {
  const d = cache.detail[bid];
  if (!d) { app.innerHTML = errorHtml('加载失败', `detail?book_id=${bid}`); return; }
  const detail = d.detail && d.detail.data ? d.detail.data : null;
  const chapters = d.chapters;
  const inShelf = isInShelf(bid);
  const data = loadData();
  const localIdx = data.readingHistory && data.readingHistory.bookId === bid ? data.readingHistory.chapterIdx : -1;
  const localName = data.readingHistory && data.readingHistory.bookId === bid ? data.readingHistory.chapterName : '';
  const bookProg = getBookProgress(bid);
  let cloudIdx = -1;
  if (bookProg && bookProg.chapterId) {
    cloudIdx = chapters.findIndex(ch => String(ch.ChapterID) === String(bookProg.chapterId));
  }
  if (cloudIdx < 0 && bookProg) cloudIdx = bookProg.chapterIdx;
  const useCloud = bookProg && cloudIdx >= 0 && (localIdx < 0 || cloudIdx > localIdx);
  const resumeIdx = Math.max(localIdx, cloudIdx >= 0 ? cloudIdx : -1);
  const resumeChapterName = useCloud ? (bookProg.chapterTitle || (chapters[resumeIdx] ? chapters[resumeIdx].Name : '')) : localName;
  const resumeLabel = resumeIdx >= 0 ? `继续阅读 · ${escapeHtml(resumeChapterName || ('第'+(resumeIdx+1)+'章'))}` : '开始阅读';
  $('pageTitle').textContent = d.bookName || '书籍详情';

  let html = '<div class="book-detail view">';
  let hasAlias = false;
  if (detail) {
    const cover = detail.thumb_url || detail.audio_thumb_uri || '';
    const title = detail.title || detail.book_name || '未知';
    const author = detail.author || detail.writer || '未知';
    const desc = detail.abstract || detail.description || '';
    const cat = detail.category || detail.categories || '';
    const cc = detail.chapter_number || chapters.length || '';
    const wc = detail.word_number ? (detail.word_number/10000).toFixed(0)+'万字' : '';
    const score = detail.score || '';
    const originalName = detail.original_book_name || '';
    const aliasName = detail.book_flight_alias_name || '';
    // Show alias if: original name differs from current title, or alias differs from both
    const aliasParts = [];
    if (originalName && originalName !== title) aliasParts.push(originalName);
    if (aliasName && aliasName !== title && aliasName !== originalName) aliasParts.push(aliasName);
    const aliasLine = aliasParts.length ? `<div class="book-detail-alias">又名：${aliasParts.map(n=>escapeHtml(n)).join(' / ')}</div>` : '';
    hasAlias = detail.original_book_name && detail.original_book_name !== title;
    let coverHtml;
    if (hasAlias) {
      coverHtml = `<div class="cover-flip" id="coverFlip" onclick="this.classList.toggle('flipped');event.stopPropagation()">
        <div class="cover-flip-inner">
          <div class="cover-flip-front">
            <img src="${cover||FALLBACK_IMG}" onerror="this.src='${FALLBACK_IMG}'">
            <span class="cover-flip-badge">推广</span>
          </div>
          <div class="cover-flip-back">
            <img id="originalCoverImg" src="${cover||FALLBACK_IMG}" onerror="this.src='${FALLBACK_IMG}'">
            <span class="cover-flip-badge">原始</span>
          </div>
        </div>
        <span class="cover-flip-hint">⇄</span>
      </div>`;
    } else {
      coverHtml = `<img class="book-detail-cover" src="${cover||FALLBACK_IMG}" onerror="this.src='${FALLBACK_IMG}'">`;
    }
    html += `<div class="book-detail-header">
      ${coverHtml}
      <div class="book-detail-info">
        <div class="book-detail-title">${escapeHtml(title)}</div>
        ${aliasLine}
        <div class="book-detail-author"><a class="author-link" onclick="navigate('author?author_id=${encodeURIComponent(d.authorId||'')}&name=${encodeURIComponent(author)}&from=${bid}')">${escapeHtml(author)}</a></div>
        <div class="book-detail-meta">${cat?`<span class="detail-tag">${escapeHtml(cat)}</span>`:''}${cc?`<span class="detail-stat">${cc}章</span>`:''}${wc?`<span class="detail-stat">${wc}</span>`:''}${score?`<span class="detail-stat">评分 ${score}</span>`:''}</div>
        <div class="book-detail-actions">
          <button class="btn-primary" onclick="navigate('reader?book_id=${bid}&chapter_idx=${resumeIdx>=0?resumeIdx:0}')">${resumeLabel}</button>
          <button class="btn-outline" onclick="navigate('comments?book_id=${bid}')">评论</button>
          <button class="btn-outline ${inShelf?'active':''}" onclick="doToggleShelf('${bid}')">${inShelf?'<i data-lucide="check" width="14" height="14" style="vertical-align:-2px"></i> 已收藏':'<i data-lucide="bookmark" width="14" height="14" style="vertical-align:-2px"></i> 收藏'}</button>
          <button class="btn-outline" onclick="shareLink('${escapeHtml(title)}','/#detail?book_id=${bid}')">分享</button>
        </div>
      </div></div>`;
    if (desc) html += `<div class="book-detail-desc collapsed" id="bookDesc">${escapeHtml(desc)}</div><button class="book-detail-desc-toggle" id="descToggle" onclick="toggleDesc()">展开简介 <i data-lucide="chevron-down" width="12" height="12" style="vertical-align:-1px"></i></button>`;
  } else { html += errorHtml('加载失败', `detail?book_id=${bid}`); }
  html += '</div>';

  const searchInput = chapters.length > 10 ? `<input type="text" class="chapter-search-input" placeholder="筛选..." oninput="debouncedFilter(this.value,'${bid}')">` : '';
  const slider = '';
  html += `<div class="chapter-section view"><div class="chapter-section-title"><span>章节目录 · ${chapters.length} 章</span>${searchInput}</div>${slider}<div class="chapter-list" id="chapterList">`;
  if (!chapters.length) html += '<div class="loading" style="padding:20px">暂无章节</div>';
  else html += renderChapterItems(chapters, bid, resumeIdx);
  html += '</div></div>';
  let bookReadCount = resumeIdx >= 0 ? resumeIdx + 1 : 0;
  if (data.stats.readSet) {
    const localExtra = chapters.filter(ch => data.stats.readSet.includes(String(ch.ChapterID)) && chapters.indexOf(ch) > resumeIdx).length;
    bookReadCount += localExtra;
  }
  if (bookReadCount > 0) html += `<div class="stats-bar view">本书已读 ${bookReadCount} 章</div>`;
  if (detail) html += `<div class="detail-sticky-spacer"></div><div class="detail-sticky"><button class="btn-primary" onclick="navigate('reader?book_id=${bid}&chapter_idx=${resumeIdx>=0?resumeIdx:0}')">${resumeLabel}</button></div>`;

  app.innerHTML = html;
  // Setup cover flip for aliased books
  if (hasAlias && d.authorId) {
    fetchOriginalCover(d.authorId, bid);
  }
  refreshIcons(app);
  // Hide toggle if description is short enough (no overflow)
  const desc = $('bookDesc');
  const toggle = $('descToggle');
  if (desc && toggle && desc.scrollHeight <= desc.clientHeight + 1) {
    desc.classList.remove('collapsed');
    toggle.style.display = 'none';
  }
  if (resumeIdx >= 0) {
    const curItem = app.querySelector('.chapter-item-current');
    if (curItem) curItem.scrollIntoView({ block: 'center', behavior: 'instant' });
  }
}

async function fetchAuthorBooks(author, currentBid, authorId) {
  const scroll = $('authorBooksScroll');
  if (!scroll) return;
  try {
    let books = [];
    if (authorId) {
      const r = await fetch(`${API}/api/author_books?author_id=${encodeURIComponent(authorId)}`);
      const data = await r.json();
      books = data.code === 200 ? (data.data || []) : [];
    } else {
      const r = await fetch(`${API}/api/search?key=${encodeURIComponent(author)}&tab_type=3`);
      const data = await r.json();
      books = ((data.code === 200 ? data.data : []) || []).filter(b => b.Author === author);
    }
    const others = books.filter(b => b.BookID !== currentBid).slice(0, 6);
    if (!others.length) {
      const section = $('authorBooksSection');
      if (section) section.style.display = 'none';
      return;
    }
    scroll.innerHTML = others.map(b => `<div class="related-item" onclick="navigate('detail?book_id=${b.BookID}')"><img src="${b.ThumbUrl||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'"><div class="name">${escapeHtml(b.Name||'')}</div>${b.ShortName?`<div class="related-short-name">${escapeHtml(b.ShortName)}</div>`:''}</div>`).join('');
  } catch(e) {
    scroll.innerHTML = '<div style="padding:12px 0;color:var(--text-muted);font-size:13px">加载失败</div>';
  }
}

function renderChapterItems(chapters, bid, lastRead) {
  const hasVolumes = chapters.some(ch => ch.volume_name);
  if (!hasVolumes) return chapters.map((ch, i) => chapterItemHtml(ch, i, bid, lastRead)).join('');
  let html = '';
  let currentVol = null;
  for (let i = 0; i < chapters.length; i++) {
    const vol = chapters[i].volume_name || '默认';
    if (vol !== currentVol) {
      currentVol = vol;
      let endI = i;
      for (let j = i+1; j < chapters.length; j++) { if ((chapters[j].volume_name||'默认') === vol) endI = j; else break; }
      html += `<div class="volume-header" onclick="this.nextElementSibling.classList.toggle('collapsed');this.classList.toggle('collapsed')"><span class="arrow"><i data-lucide="chevron-down" width="10" height="10"></i></span>${escapeHtml(vol)}</div><div class="volume-body">`;
      for (let k = i; k <= endI; k++) html += chapterItemHtml(chapters[k], k, bid, lastRead);
      html += '</div>';
      i = endI;
    }
  }
  return html;
}

function chapterItemHtml(ch, i, bid, lastRead) {
  let cls = 'chapter-item';
  if (i < lastRead) cls += ' chapter-item-read';
  if (i === lastRead) cls += ' chapter-item-current';
  const time = ch.UpdateTime ? timeAgo(ch.UpdateTime) : '';
  const badge = i === lastRead ? '<span class="chapter-item-badge">上次读到</span>' : '';
  return `<div class="${cls}" onclick="navigate('reader?book_id=${bid}&chapter_idx=${i}')"><span>${escapeHtml(ch.Name||'第'+(i+1)+'章')}${badge}</span>${time?`<span class="chapter-item-time">${time}</span>`:''}</div>`;
}

const debouncedFilter = debounce(filterChapters, 200);

// ====== Render: Author Page ======
async function renderAuthorPage(app, authorId, authorName) {
  const displayName = authorName || '作者';
  if ($('pageTitle')) $('pageTitle').textContent = '作者主页';
  app.innerHTML = `<div class="author-page view">
    <div class="author-header">
      <div class="author-avatar"><i data-lucide="user" width="36" height="36"></i></div>
      <div class="author-info">
        <div class="author-name">${escapeHtml(displayName)}</div>
        <div class="author-meta" id="authorMeta">
          <span class="meta-item"><i data-lucide="book-open" width="13" height="13"></i> 加载中...</span>
        </div>
      </div>
      <button class="author-follow-btn">关注</button>
    </div>
    <div class="author-books" id="authorBooks"><div class="loading" style="padding:40px 0">加载中...</div></div>
  </div>`;
  refreshIcons(app);

  try {
    let books = [];
    if (authorId) {
      const r = await fetch(`${API}/api/author_books?author_id=${encodeURIComponent(authorId)}`);
      const data = await r.json();
      if (data.code === 200) {
        books = data.data || [];
        // Update header with richer info from API
        const meta = $('authorMeta');
        if (meta) {
          const parts = [];
          if (data.author_book_num) parts.push(`<span class="meta-item"><i data-lucide="book-open" width="13" height="13"></i> ${data.author_book_num} 部作品</span>`);
          if (data.author_fans) parts.push(`<span class="meta-item"><i data-lucide="users" width="13" height="13"></i> ${data.author_fans} 粉丝</span>`);
          meta.innerHTML = parts.join(' · ') || '<span class="meta-item"><i data-lucide="book-open" width="13" height="13"></i> 暂无作品</span>';
          refreshIcons(meta);
        }
        const avatar = app.querySelector('.author-avatar');
        if (avatar && data.author_avatar) {
          avatar.innerHTML = `<img src="${data.author_avatar}" onerror="this.parentElement.innerHTML='<i data-lucide=\\'user\\' width=\\'36\\' height=\\'36\\'></i>'">`;
        }
        const nameEl = app.querySelector('.author-name');
        if (nameEl && data.author_name) nameEl.textContent = data.author_name;
        if (data.author_desc) {
          const info = app.querySelector('.author-info');
          if (info) info.insertAdjacentHTML('beforeend', `<div class="author-desc">${escapeHtml(data.author_desc)}</div>`);
        }
      }
    } else {
      // Fallback: search by name
      const r = await fetch(`${API}/api/search?key=${encodeURIComponent(displayName)}&tab_type=3`);
      const data = await r.json();
      books = (data.code === 200 ? (data.data || []) : []).filter(b => b.Author === displayName);
      const meta = $('authorMeta');
      if (meta) meta.innerHTML = `<span class="meta-item"><i data-lucide="book-open" width="13" height="13"></i> ${books.length ? books.length+' 部作品' : '暂无作品'}</span>`;
      refreshIcons(meta);
    }

    const container = $('authorBooks');
    if (!container) return;

    if (!books.length) {
      container.innerHTML = '<div class="shelf-empty"><div class="icon"><i data-lucide="book-open" width="48" height="48"></i></div><div>该作者暂无作品</div></div>';
      refreshIcons(container);
      return;
    }

    container.innerHTML = '<div class="book-list">' + books.map(book => {
      const tags = (book.Tags||'').split(',').map(t=>t.trim()).filter(Boolean);
      const status = book.Status||'';
      const wc = book.WordCount ? (book.WordCount/10000).toFixed(1)+'万字' : '';
      const score = parseFloat(book.Score);
      const stars = score > 0 ? '<i data-lucide="star" width="12" height="12" style="vertical-align:-1px"></i>'+(score > 10 ? (score/10).toFixed(1) : score.toFixed(1)) : '';
      const readText = book.ReadCountText || (book.ReadCount ? book.ReadCount+'人在读' : '');
      return `<div class="book-card" onclick="navigate('detail?book_id=${book.BookID}')">
        <img class="book-cover" src="${book.ThumbUrl||FALLBACK_IMG}" loading="lazy" onerror="this.src='${FALLBACK_IMG}'">
        <div class="book-info">
          <div class="book-title">${escapeHtml(book.Name||'')}${status?`<span class="status-badge ${status==='连载中'?'ongoing':'finished'}">${status}</span>`:''}</div>
          ${book.ShortName?`<div class="book-short-name">又名：${escapeHtml(book.ShortName)}</div>`:''}
          <div class="book-author">${[stars, readText].filter(Boolean).join(' · ')}</div>
          <div class="book-desc">${escapeHtml(book.Desc||'')}</div>
          <div class="book-meta">${[book.ChapterCount?book.ChapterCount+'章':'', wc, ...tags.map(t=>`<span class="tag-pill">${escapeHtml(t)}</span>`)].filter(Boolean).join(' · ')}</div>
        </div></div>`;
    }).join('') + '</div>';
    refreshIcons(container);
  } catch(e) {
    const container = $('authorBooks');
    if (container) container.innerHTML = '<div class="error view">加载失败</div>';
  }
}
function filterChapters(q, bid) {
  const el = $('chapterList'); if (!el) return;
  const d = cache.detail[bid]; if (!d) return;
  q = q.trim().toLowerCase();
  const data = loadData();
  const lastRead = data.readingHistory && data.readingHistory.bookId === bid ? data.readingHistory.chapterIdx : -1;
  if (!q) { el.innerHTML = renderChapterItems(d.chapters, bid, lastRead); return; }
  const matched = d.chapters.map((ch,i)=>({ch,i})).filter(({ch})=>(ch.Name||'').toLowerCase().includes(q));
  if (!matched.length) { el.innerHTML = '<div class="loading" style="padding:20px">无匹配</div>'; return; }
  el.innerHTML = matched.map(({ch,i})=>chapterItemHtml(ch,i,bid,lastRead)).join('');
}

function onChapterSlider(val, bid) {
  const idx = parseInt(val);
  const el = $('chapterSliderLabel');
  if (el) el.textContent = `第${idx+1}章`;
  navigate(`reader?book_id=${bid}&chapter_idx=${idx}`);
}

function doToggleShelf(bid) { const d = cache.detail[bid]; toggleShelf(bid, d.bookName, d.bookAuthor, d.bookThumb); renderDetail($('app'), bid); }

function toggleDesc() {
  const desc = $('bookDesc');
  const btn = $('descToggle');
  if (!desc || !btn) return;
  const collapsed = desc.classList.toggle('collapsed');
  btn.innerHTML = collapsed
    ? '展开简介 <i data-lucide="chevron-down" width="12" height="12" style="vertical-align:-1px"></i>'
    : '收起简介 <i data-lucide="chevron-up" width="12" height="12" style="vertical-align:-1px"></i>';
  refreshIcons(btn);
}

async function fetchOriginalCover(authorId, bid) {
  try {
    const r = await fetch(`${API}/api/author_books?author_id=${encodeURIComponent(authorId)}`);
    const data = await r.json();
    if (data.code === 200 && Array.isArray(data.data)) {
      const book = data.data.find(b => String(b.BookID) === String(bid));
      if (book && book.ThumbUrl) {
        const img = document.getElementById('originalCoverImg');
        if (img) img.src = book.ThumbUrl;
      }
    }
  } catch (e) {}
}

// ====== Render: Comments ======
let commentPage = { offset: 0, hasMore: true };
function renderComments(app, bid) {
  const d = cache.detail[bid];
  const list = d ? d.comments : null;
  if (!list || !list.length) { app.innerHTML = '<div class="error view">暂无评论</div>'; return; }
  commentPage.offset = 0;
  const pageSize = 20;
  const shown = list.slice(0, pageSize);
  let html = `<div class="comments-section view"><h3>全部评论 (${list.length})</h3>`;
  html += renderCommentItems(shown);
  if (list.length > pageSize) html += `<div class="load-more view"><button onclick="loadMoreComments('${bid}')">加载更多评论</button></div>`;
  html += '</div>';
  app.innerHTML = html;
  refreshIcons(app);
}
function renderCommentItems(list) {
  let html = '';
  for (const c of list) {
    const user = c.user_name || (c.user_info && c.user_info.user_name) || c.nick_name || '匿名';
    const avatar = c.avatar_url || (c.user_info && (c.user_info.user_avatar || c.user_info.avatar_url)) || '';
    const text = c.content || c.text || '';
    const time = c.create_time || c.create_timestamp || 0;
    const digg = c.digg_count || 0;
    const imgs = c.images || [];
    const reply = c.reply_list || c.reply_comment || c.child_comments || null;
    const avatarHtml = avatar
      ? `<img class="comment-avatar" src="${safeImgUrl(avatar)}" alt="" loading="lazy" onerror="this.style.display='none'">`
      : `<div class="comment-avatar comment-avatar-placeholder">${escapeHtml((user||'匿')[0])}</div>`;
    html += `<div class="comment-item">${avatarHtml}<div class="comment-body">`;
    html += `<div class="comment-user">${escapeHtml(user)}</div>`;
    html += `<div class="comment-text">${escapeHtml(text)}</div>`;
    if (imgs.length > 0) {
      const imgGridClass = imgs.length === 1 ? 'comment-images single-img' : 'comment-images';
      html += `<div class="${imgGridClass}">`;
      for (const src of imgs) {
        html += `<img class="comment-img" src="${safeImgUrl(src)}" alt="评论图片" loading="lazy" onclick="openImageViewer(this.src)">`;
      }
      html += '</div>';
    }
    const timeStr = time ? formatTime(time) : '';
    const diggStr = digg > 0 ? `<span class="comment-digg">${digg}</span>` : '';
    if (timeStr || diggStr) {
      html += `<div class="comment-meta">${timeStr ? `<span>${timeStr}</span>` : ''}${diggStr}</div>`;
    }
    if (reply && Array.isArray(reply) && reply.length > 0) {
      html += '<div class="comment-reply">';
      for (const rc of reply.slice(0,3)) {
        const rcUser = rc.user_name || (rc.user_info && rc.user_info.user_name) || rc.nick_name || '匿名';
        const rcAvatar = rc.avatar_url || (rc.user_info && (rc.user_info.user_avatar || rc.user_info.avatar_url)) || '';
        const rcAvatarHtml = rcAvatar
          ? `<img class="comment-avatar comment-avatar-sm" src="${safeImgUrl(rcAvatar)}" alt="" loading="lazy" onerror="this.style.display='none'">`
          : `<div class="comment-avatar comment-avatar-sm comment-avatar-placeholder">${escapeHtml((rcUser||'匿')[0])}</div>`;
        html += `<div class="comment-item">${rcAvatarHtml}<div class="comment-body"><div class="comment-user">${escapeHtml(rcUser)}</div><div class="comment-text">${escapeHtml(rc.content||rc.text||'')}</div></div></div>`;
      }
      html += '</div>';
    }
    html += '</div></div>';
  }
  return html;
}
function loadMoreComments(bid) {
  const d = cache.detail[bid]; if (!d || !d.comments) return;
  commentPage.offset += 20;
  const more = d.comments.slice(commentPage.offset, commentPage.offset + 20);
  if (!more.length) return;
  const section = document.querySelector('.comments-section');
  if (!section) return;
  const btn = section.querySelector('.load-more');
  if (btn) btn.remove();
  section.insertAdjacentHTML('beforeend', renderCommentItems(more));
  if (commentPage.offset + 20 < d.comments.length) {
    section.insertAdjacentHTML('beforeend', `<div class="load-more view"><button onclick="loadMoreComments('${bid}')">加载更多评论</button></div>`);
  }
}
