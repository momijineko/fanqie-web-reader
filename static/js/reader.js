// ====== Shared reader helpers ======
function renderParas(paras, authorSpeak) {
  let html = '';
  for (let pi = 0; pi < paras.length; pi++) {
    const text = paras[pi];
    if (!text) continue;
    const imgMatch = text.match(/^https?:\/\/\S+\.(jpg|jpeg|png|webp|gif)(\?\S*)?$/i);
    if (imgMatch) { html += `<img class="content-img" src="${text}">`; continue; }
    const innerImgs = text.match(/<img[^>]+src="([^"]+)"[^>]*>/gi);
    if (innerImgs) { innerImgs.forEach(imgTag => { const src = imgTag.match(/src="([^"]+)"/); if (src) html += `<img class="content-img" src="${src[1]}">`; }); continue; }
    html += `<div class="para-wrap" data-para-idx="${pi}"><p>${escapeHtml(text)}<span class="para-badge" data-para-idx="${pi}" onclick="event.stopPropagation();openParaComment(${pi})"></span></p></div>`;
  }
  if (authorSpeak) html += `<div class="author-speak"><i data-lucide="message-circle" width="14" height="14"></i> 作者说：${escapeHtml(authorSpeak)}</div>`;
  return html;
}

function cycleFont() {
  const cur = getFont();
  const i = FONTS.findIndex(f => f.id === cur);
  changeFont(FONTS[(i+1)%FONTS.length].id);
}

function getDisplayTheme() {
  if (getTheme() === 'auto') return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'default';
  return getTheme();
}

function saveReaderProgress(bid, idx, chapterId, chapterTitle, total) {
  const data = loadData();
  data.readingHistory = { bookId: bid, name: (cache.detail[bid]||{}).bookName||'', chapterIdx: idx, chapterName: chapterTitle, totalChapters: total, updatedAt: Date.now() };
  saveReadingHistory(data.readingHistory);
  if (!data.stats.readSet) data.stats.readSet = [];
  const csid = String(chapterId);
  if (!data.stats.readSet.includes(csid)) { data.stats.readSet.push(csid); data.stats.chaptersRead = data.stats.readSet.length; saveStats(data.stats); }
}

// ====== Reader state ======
let pg = { bid:'', idx:0, total:0, totalPages:1, curPage:0, animating:false, swipeStartX:0, swipeStartY:0, swipeStartTime:0, swipeActive:false, pages:[] };
let scrollState = { lastScrollY: 0, ticking: false };

// ====== Reader dispatcher ======
function renderReader(app, bid, idx) {
  const d = cache.detail[bid];
  if (!d) { app.innerHTML = errorHtml('加载失败', `reader?book_id=${bid}&chapter_idx=${idx}`); return; }
  const chapterId = d.chapters[idx].ChapterID;
  const c = getContentCache(chapterId);
  if (!c) { app.innerHTML = loadingHtml(); return; }
  if (getReadMode() === 'scroll') renderScrollReader(app, bid, idx);
  else renderPageReader(app, bid, idx); // page, no-anim
}

// ====== Scroll Reader ======
function renderScrollReader(app, bid, idx) {
  const d = cache.detail[bid];
  const chapterId = d.chapters[idx].ChapterID;
  const c = getContentCache(chapterId);
  const paras = c.paragraphs;
  const total = d.chapters.length;
  const prevIdx = idx > 0 ? idx - 1 : -1;
  const nextIdx = idx < total - 1 ? idx + 1 : -1;
  const wc = paras.reduce((s,p) => s + (p||'').length, 0);
  const readMin = Math.max(1, Math.round(wc / 400));
  const chapterTitle = d.chapters[idx].Name || `第${idx+1}章`;

  currentChapterKey = chapterId;
  currentBookId = bid; currentChapterIdx = idx; appendedChapters = 0;
  saveReaderProgress(bid, idx, chapterId, chapterTitle, total);

  const dt = getDisplayTheme();
  const curFont = getFont();
  const curMode = getReadMode();
  const fontSize = getFontSize();
  const lineHeight = getLineHeight();

  app.innerHTML = `<div class="scroll-reader view" id="scrollReader">
    <div class="page-header">
      <button class="back-btn" onclick="goBack()"><i data-lucide="chevron-left" width="22" height="22"></i></button>
      <span class="chapter-name">${escapeHtml(chapterTitle)}</span>
      <button class="settings-btn" onclick="toggleToolbar()">Aa</button>
    </div>
    <div class="reader-chapter-info">
      <div class="reader-bookname">${escapeHtml(d.bookName)} · 第 ${idx+1}/${total} 章 · 约 ${readMin} 分钟</div>
    </div>
    <div class="reader-content" id="readerContent">${renderParas(paras, c.authorSpeak)}</div>
    <div class="chapter-end">· · ·</div>
    <div class="auto-next-indicator hidden" id="autoNextIndicator">正在加载下一章...</div>
    <div class="reader-nav">
      <button ${prevIdx>=0?`onclick="navigate('reader?book_id=${bid}&chapter_idx=${prevIdx}')"`:'disabled'}><i data-lucide="chevron-left" width="16" height="16"></i> 上一章</button>
      <button onclick="showChapterList()">目录</button>
      <button class="btn-accent" ${nextIdx>=0?`onclick="navigate('reader?book_id=${bid}&chapter_idx=${nextIdx}')"`:'disabled'}>下一章 <i data-lucide="chevron-right" width="16" height="16"></i></button>
    </div>
  </div>
  <div class="reader-bottom-sheet auto-hide" id="readerToolbar">
    <div class="sheet-handle"></div>
    <div class="reader-settings">
      <div class="settings-row">
        <span class="settings-label">字号</span>
        <button class="settings-btn-sm" onclick="changeFontSize(-1)">A-</button>
        <input type="range" class="settings-slider" id="fontSizeSlider" min="14" max="28" step="1" value="${fontSize}" oninput="changeFontSizeTo(+this.value)">
        <button class="settings-btn-sm" onclick="changeFontSize(1)">A+</button>
      </div>
      <div class="settings-row">
        <span class="settings-label">行距</span>
        <input type="range" class="settings-slider" id="lineHeightSlider" min="14" max="24" step="1" value="${Math.round(lineHeight*10)}" oninput="changeLineHeightTo(+this.value/10)">
      </div>
      <div class="settings-row">
        <span class="settings-label">字体</span>
        <div class="settings-chips">${FONTS.map(f=>`<button class="settings-chip${f.id===curFont?' active':''}" onclick="changeFont('${f.id}')">${f.name}</button>`).join('')}</div>
      </div>
      <div class="settings-row">
        <span class="settings-label">背景</span>
        <div class="bg-swatches">
          <button class="bg-swatch swatch-default${dt==='default'?' active':''}" onclick="applyThemeFrom(event,'default')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
          <button class="bg-swatch swatch-sepia${dt==='sepia'?' active':''}" onclick="applyThemeFrom(event,'sepia')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
          <button class="bg-swatch swatch-green${dt==='green'?' active':''}" onclick="applyThemeFrom(event,'green')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
          <button class="bg-swatch swatch-dark${dt==='dark'?' active':''}" onclick="applyThemeFrom(event,'dark')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
        </div>
      </div>
      <div class="settings-row">
        <span class="settings-label">模式</span>
        <div class="settings-chips">
          ${READ_MODES.map(m => `<button class="settings-chip${curMode===m.id?' active':''}" onclick="switchReadMode('${m.id}')">${m.label}</button>`).join('')}
        </div>
      </div>
    </div>
    <div class="sheet-divider"></div>
    <div class="sheet-nav">
      <button onclick="shareLink('${escapeHtml(chapterTitle)}','/#reader?book_id=${bid}&chapter_idx=${idx}')">分享</button>
    </div>
  </div>`;

  document.documentElement.style.setProperty('--reader-font-size', getFontSize()+'px');
  document.documentElement.style.setProperty('--reader-line-height', getLineHeight());
  applyFont();

  const savedScroll = sessionStorage.getItem('scroll_' + chapterId);
  if (savedScroll) setTimeout(() => window.scrollTo(0, parseInt(savedScroll)), 50);
  else window.scrollTo(0, 0);
  setTimeout(updateProgress, 100);
  preloadAdjacent(bid, idx);
  setupScrollAutoNext(bid, idx);
  setupScrollToolbarAutoHide();
  setupScrollSwipe(bid, idx, total);
  loadParaCommentCounts(chapterId);
  refreshIcons(app);
}

function setupScrollAutoNext(bid, idx) {
  if (autoNextObserver) { autoNextObserver.disconnect(); autoNextObserver = null; }
  const d = cache.detail[bid]; if (!d) return;
  const sentinel = document.createElement('div');
  sentinel.id = 'autoNextSentinel'; sentinel.style.height = '1px';
  const nav = document.querySelector('.scroll-reader .reader-nav');
  if (nav) nav.parentNode.insertBefore(sentinel, nav);

  autoNextObserver = new IntersectionObserver(async (entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      const nextIdx = currentChapterIdx + appendedChapters + 1;
      if (nextIdx >= d.chapters.length) continue;
      const indicator = $('autoNextIndicator');
      if (indicator) indicator.classList.remove('hidden');
      const nextChapterId = d.chapters[nextIdx].ChapterID;
      if (!getContentCache(nextChapterId)) await fetchContent(nextChapterId);
      const nc = getContentCache(nextChapterId);
      if (nc && nc.paragraphs.length > 0) {
        const content = $('readerContent');
        if (content) {
          const div = document.createElement('div');
          div.className = 'auto-next-chapter';
          div.innerHTML = `<div class="auto-next-chapter"><div class="reader-title">${escapeHtml(d.chapters[nextIdx].Name||'第'+(nextIdx+1)+'章')}</div>` + renderParas(nc.paragraphs, nc.authorSpeak) + `<div class="chapter-end">本章已读完</div></div>`;
          content.appendChild(div);
          appendedChapters++;
          loadParaCommentCounts(nextChapterId);
          const data = loadData();
          data.readingHistory.chapterIdx = nextIdx;
          data.readingHistory.chapterName = d.chapters[nextIdx].Name || `第${nextIdx+1}章`;
          saveReadingHistory(data.readingHistory);
          if (!data.stats.readSet) data.stats.readSet = [];
          const nsid = String(nextChapterId);
          if (!data.stats.readSet.includes(nsid)) { data.stats.readSet.push(nsid); data.stats.chaptersRead = data.stats.readSet.length; saveStats(data.stats); }
        }
      }
      if (indicator) indicator.classList.add('hidden');
      preloadAdjacent(bid, nextIdx);
    }
  }, { rootMargin: '200px' });
  autoNextObserver.observe(sentinel);
}

function setupScrollToolbarAutoHide() {
  scrollState.lastScrollY = window.scrollY;
  scrollState.ticking = false;
  window.addEventListener('scroll', function() {
    if (scrollState.ticking) return;
    scrollState.ticking = true;
    requestAnimationFrame(() => {
      const curY = window.scrollY;
      const tb = $('readerToolbar');
      if (tb) {
        if (curY > scrollState.lastScrollY + 10) tb.classList.add('hidden-toolbar');
        else if (curY < scrollState.lastScrollY - 10) tb.classList.remove('hidden-toolbar');
      }
      scrollState.lastScrollY = curY;
      scrollState.ticking = false;
    });
  });
}

function setupScrollSwipe(bid, idx, total) {
  let touchStartX = 0, touchStartY = 0, touchStartTime = 0;
  const content = $('readerContent');
  if (!content) return;
  content.addEventListener('touchstart', e => {
    touchStartX = e.touches[0].clientX; touchStartY = e.touches[0].clientY;
    touchStartTime = Date.now();
  }, { passive: true });
  content.addEventListener('touchend', e => {
    const dx = e.changedTouches[0].clientX - touchStartX;
    const dy = e.changedTouches[0].clientY - touchStartY;
    const dt = Date.now() - touchStartTime;
    if (dt > 500 || Math.abs(dy) > Math.abs(dx) || Math.abs(dx) < 60) return;
    if (dx < -60 && idx < total - 1) navigate(`reader?book_id=${bid}&chapter_idx=${idx+1}`);
    else if (dx > 60 && idx > 0) navigate(`reader?book_id=${bid}&chapter_idx=${idx-1}`);
  }, { passive: true });
}

// ====== Paginated Reader ======
function renderPageReader(app, bid, idx) {
  const d = cache.detail[bid];
  const chapterId = d.chapters[idx].ChapterID;
  const c = getContentCache(chapterId);
  const paras = c.paragraphs;
  const total = d.chapters.length;
  const chapterTitle = d.chapters[idx].Name || `第${idx+1}章`;

  currentChapterKey = null;
  currentBookId = bid; currentChapterIdx = idx;
  pg = { bid, idx, total, totalPages:1, curPage:0, animating:false, swipeStartX:0, swipeStartY:0, swipeStartTime:0, swipeActive:false, pages:[] };
  saveReaderProgress(bid, idx, chapterId, chapterTitle, total);

  const dt = getDisplayTheme();
  const curFont = getFont();
  const curMode = getReadMode();
  const fontSize = getFontSize();
  const lineHeight = getLineHeight();
  const prevDisabled = idx <= 0;
  const nextDisabled = idx >= total - 1;

  app.innerHTML = `<div class="paginated-reader" id="paginatedReader">
    <div class="page-header" id="pageHeader">
      <button class="back-btn" onclick="goBack()"><i data-lucide="chevron-left" width="22" height="22"></i></button>
      <span class="chapter-name">${escapeHtml(chapterTitle)}</span>
      <button class="settings-btn" onclick="toggleToolbar()">Aa</button>
    </div>
    <div class="page-container" id="pageContainer" onclick="handlePageContainerClick(event)">
      <div class="page-viewport" id="pageViewport"></div>
      <div class="page-shadow-left" id="pageShadowLeft"></div>
      <div class="page-shadow-right" id="pageShadowRight"></div>
      <div class="tap-zone tap-zone-left"></div>
      <div class="tap-zone tap-zone-center"></div>
      <div class="tap-zone tap-zone-right"></div>
      <div class="page-toolbar-overlay" id="pageToolbar">
        <div class="page-toolbar-top">
          <button class="back-btn" onclick="goBack()"><i data-lucide="chevron-left" width="22" height="22"></i></button>
          <span class="chapter-name">${escapeHtml(chapterTitle)}</span>
          <button class="icon-btn" onclick="toggleToolbar()"><i data-lucide="x" width="16" height="16"></i></button>
        </div>
        <div style="flex:1" onclick="toggleToolbar()"></div>
        <div class="page-toolbar-bottom">
          <div class="reader-settings">
            <div class="settings-row">
              <span class="settings-label">字号</span>
              <button class="settings-btn-sm" onclick="pgFontSize(-1)">A-</button>
              <input type="range" class="settings-slider" min="14" max="28" step="1" value="${fontSize}" oninput="changeFontSizeTo(+this.value);pgCalcDelayed()">
              <button class="settings-btn-sm" onclick="pgFontSize(1)">A+</button>
            </div>
            <div class="settings-row">
              <span class="settings-label">行距</span>
              <input type="range" class="settings-slider" min="14" max="24" step="1" value="${Math.round(lineHeight*10)}" oninput="changeLineHeightTo(+this.value/10);pgCalcDelayed()">
            </div>
            <div class="settings-row">
              <span class="settings-label">字体</span>
              <div class="settings-chips">${FONTS.map(f=>`<button class="settings-chip${f.id===curFont?' active':''}" onclick="changeFont('${f.id}');pgCalcDelayed()">${f.name}</button>`).join('')}</div>
            </div>
            <div class="settings-row">
              <span class="settings-label">背景</span>
              <div class="bg-swatches">
                <button class="bg-swatch swatch-default${dt==='default'?' active':''}" onclick="applyThemeFrom(event,'default')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
                <button class="bg-swatch swatch-sepia${dt==='sepia'?' active':''}" onclick="applyThemeFrom(event,'sepia')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
                <button class="bg-swatch swatch-green${dt==='green'?' active':''}" onclick="applyThemeFrom(event,'green')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
                <button class="bg-swatch swatch-dark${dt==='dark'?' active':''}" onclick="applyThemeFrom(event,'dark')"><div class="mini-lines"><div class="mini-line"></div><div class="mini-line"></div><div class="mini-line"></div></div></button>
              </div>
            </div>
            <div class="settings-row">
              <span class="settings-label">模式</span>
              <div class="settings-chips">
                ${READ_MODES.map(m => `<button class="settings-chip${curMode===m.id?' active':''}" onclick="switchReadMode('${m.id}')">${m.label}</button>`).join('')}
              </div>
            </div>
          </div>
          <div class="sheet-divider"></div>
          <div class="nav-row">
            <button ${prevDisabled?'disabled':''} onclick="pgSwitchChapter(${idx-1},-1)"><i data-lucide="chevron-left" width="16" height="16"></i> 上一章</button>
            <button onclick="showChapterList()">目录</button>
            <button class="btn-accent" ${nextDisabled?'disabled':''} onclick="pgSwitchChapter(${idx+1},0)">下一章 <i data-lucide="chevron-right" width="16" height="16"></i></button>
          </div>
          <div class="slider-row">
            <input type="range" id="pageSlider" min="1" max="1" value="1" oninput="pgSliderGo(this.value)">
            <span class="slider-label" id="pageSliderLabel">1/1</span>
          </div>
        </div>
      </div>
    </div>
    <div class="page-footer">
      <div class="page-footer-left">
        <span>${escapeHtml(d.bookName)}</span><span>·</span><span>第${idx+1}/${total}章</span>
      </div>
      <div class="page-footer-right">
        <span id="pageInfo">1/1</span>
        <span id="pagePercent">0%</span>
      </div>
    </div>
  </div>`;

  document.documentElement.style.setProperty('--reader-font-size', getFontSize()+'px');
  document.documentElement.style.setProperty('--reader-line-height', getLineHeight());
  applyFont();

  requestAnimationFrame(() => { requestAnimationFrame(() => { pgCalculatePages(); loadParaCommentCounts(d.chapters[idx].ChapterID); }); });
  setupPgGestures();
  preloadAdjacent(bid, idx);
  refreshIcons(app);
}

// ====== Page calculation ======
function pgCalculatePages() {
  const container = $('pageContainer');
  const viewport = $('pageViewport');
  if (!container || !viewport) return;
  
  const cw = container.clientWidth;
  const ch = container.clientHeight;
  if (cw <= 0 || ch <= 0) return;
  
  const bid = pg.bid;
  const idx = pg.idx;
  const d = cache.detail[bid];
  if (!d) return;
  const chapterId = d.chapters[idx].ChapterID;
  const c = getContentCache(chapterId);
  if (!c) return;
  
  const paras = c.paragraphs;
  const authorSpeak = c.authorSpeak;
  
  // Build paragraph HTML array
  const paraHtmls = [];
  for (let pi = 0; pi < paras.length; pi++) {
    const text = paras[pi];
    if (!text) continue;
    const imgMatch = text.match(/^https?:\/\/\S+\.(jpg|jpeg|png|webp|gif)(\?\S*)?$/i);
    if (imgMatch) { paraHtmls.push(`<img class="content-img" src="${text}">`); continue; }
    const innerImgs = text.match(/<img[^>]+src="([^"]+)"[^>]*>/gi);
    if (innerImgs) { innerImgs.forEach(imgTag => { const src = imgTag.match(/src="([^"]+)"/); if (src) paraHtmls.push(`<img class="content-img" src="${src[1]}">`); }); continue; }
    paraHtmls.push(`<div class="para-wrap" data-para-idx="${pi}"><p>${escapeHtml(text)}<span class="para-badge" data-para-idx="${pi}" onclick="event.stopPropagation();openParaComment(${pi})"></span></p></div>`);
  }
  if (authorSpeak) paraHtmls.push(`<div class="author-speak"><i data-lucide="message-circle" width="14" height="14"></i> 作者说：${escapeHtml(authorSpeak)}</div>`);
  
  // Use a real .page-page element as measurer to guarantee layout consistency.
  const measurer = document.createElement('div');
  measurer.className = 'page-page';
  measurer.style.cssText = 'position:fixed;visibility:hidden;left:-9999px;top:0;width:' + cw + 'px;height:' + ch + 'px;inset:auto;overflow:hidden;';
  // Attach inside the reader so CSS variables are available
  const reader = $('paginatedReader');
  if (reader) reader.appendChild(measurer);
  else document.body.appendChild(measurer);
  
  // With border-box + explicit height + overflow:hidden:
  //   clientHeight = height = ch (no border)
  //   scrollHeight = max(clientHeight, content_height + paddingTop + paddingBottom)
  // When scrollHeight > clientHeight, content overflows the visible area.
  // But we want to use the *content area* height (excluding padding) as threshold,
  // because .page-page padding pushes content inward from the edges.
  // scrollHeight > clientHeight means content+padding > ch, which is correct
  // since padding is part of the page and content must fit inside it.
  // The issue is scrollHeight already includes padding, so the comparison
  // scrollHeight vs clientHeight correctly detects overflow when content
  // pushes beyond the padded area.
  
  // Split paragraphs into pages
  // scrollHeight includes padding, but we want to compare pure content height
  // against pure content area (clientHeight - padding).
  // Equivalent: scrollHeight - padTotal > clientHeight - padTotal
  // Simplifies to: scrollHeight > clientHeight (same as before)
  // BUT the issue is scrollHeight = max(clientHeight, content+padding),
  // so when content+padding <= clientHeight, scrollHeight = clientHeight (no overflow detected)
  // even though content could still fit more paragraphs.
  // Fix: use scrollHeight of inner content by temporarily removing padding from comparison.
  const cs = getComputedStyle(measurer);
  const padTotal = parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom);
  const maxH = measurer.clientHeight - padTotal;
  const pages = [];
  let currentPage = '';
  
  for (const ph of paraHtmls) {
    measurer.innerHTML = currentPage + ph;
    if ((measurer.scrollHeight - padTotal) > maxH) {
      // Adding ph overflows. Try to split ph's text to fill current page.
      const pMatch = ph.match(/^(<div class="para-wrap"><p>)([\s\S]*?)(<\/p><\/div>)$/);
      if (pMatch && pMatch[2].length > 1) {
        // Binary search for max characters that fit on current page
        const [prefix, text, suffix] = [pMatch[1], pMatch[2], pMatch[3]];
        let lo = 0, hi = text.length, best = 0;
        while (lo <= hi) {
          const mid = Math.floor((lo + hi) / 2);
          measurer.innerHTML = currentPage + prefix + text.substring(0, mid) + suffix;
          if ((measurer.scrollHeight - padTotal) <= maxH) { best = mid; lo = mid + 1; }
          else hi = mid - 1;
        }
        if (best > 0) {
          currentPage += prefix + text.substring(0, best) + suffix;
          pages.push(currentPage);
          currentPage = prefix + text.substring(best) + suffix;
          measurer.innerHTML = currentPage;
        } else {
          // Can't fit even one char — push whole paragraph to next page
          if (currentPage) { pages.push(currentPage); }
          currentPage = ph;
          measurer.innerHTML = currentPage;
        }
      } else {
        // Image or unsplittable element
        if (currentPage) { pages.push(currentPage); }
        currentPage = ph;
        measurer.innerHTML = currentPage;
      }
    } else {
      currentPage += ph;
    }
  }
  if (currentPage) pages.push(currentPage);
  
  // Cleanup measurer
  if (measurer.parentNode) measurer.parentNode.removeChild(measurer);
  
  if (pages.length === 0) pages.push('');
  
  // Store pages in state
  pg.pages = pages;
  pg.totalPages = pages.length;
  pg.curPage = 0;
  
  // Render pages into viewport
  viewport.innerHTML = '';
  pages.forEach((html, i) => {
    const div = document.createElement('div');
    div.className = 'page-page';
    div.dataset.page = i;
    div.innerHTML = html;
    div.style.display = i === 0 ? '' : 'none';
    viewport.appendChild(div);
  });
  
  pgUpdateUI();
}

function pgUpdateUI() {
  const info = $('pageInfo');
  const pct = $('pagePercent');
  const slider = $('pageSlider');
  const sliderLabel = $('pageSliderLabel');
  if (info) info.textContent = `${pg.curPage+1}/${pg.totalPages}`;
  if (pct) pct.textContent = pg.totalPages > 1 ? Math.round(pg.curPage/(pg.totalPages-1)*100)+'%' : '0%';
  if (slider) { slider.max = pg.totalPages; slider.value = pg.curPage + 1; }
  if (sliderLabel) sliderLabel.textContent = `${pg.curPage+1}/${pg.totalPages}`;
}

// ====== Page navigation ======
function pgGoToPage(target, animate) {
  if (pg.animating && animate) return;
  if (target < 0 || target >= pg.totalPages) return;
  
  const viewport = $('pageViewport');
  if (!viewport) return;
  const mode = getReadMode();
  
  // no-anim mode: always instant
  if (mode === 'no-anim') animate = false;
  
  const oldPage = viewport.querySelector(`.page-page[data-page="${pg.curPage}"]`);
  const newPage = viewport.querySelector(`.page-page[data-page="${target}"]`);
  if (!newPage) return;
  
  if (animate && oldPage && oldPage !== newPage) {
    pg.animating = true;
    const dir = target > pg.curPage ? 1 : -1;
    
    // Position new page off-screen
    newPage.style.display = '';
    newPage.style.transform = `translateX(${dir * 100}%)`;
    newPage.style.transition = 'none';
    
    // Force reflow
    newPage.offsetHeight;
    
    newPage.style.transition = 'transform 0.3s ease';
    oldPage.style.transition = 'transform 0.3s ease';
    oldPage.style.transform = `translateX(${-dir * 100}%)`;
    newPage.style.transform = 'translateX(0)';
    setTimeout(() => {
      oldPage.style.display = 'none';
      oldPage.style.transform = '';
      oldPage.style.transition = '';
      newPage.style.transform = '';
      newPage.style.transition = '';
      pg.animating = false;
    }, 320);
  } else {
    // Instant switch
    if (oldPage && oldPage !== newPage) oldPage.style.display = 'none';
    newPage.style.display = '';
  }
  
  pg.curPage = target;
  pgUpdateUI();
}

function pageNext() {
  if (pg.curPage < pg.totalPages - 1) pgGoToPage(pg.curPage + 1, true);
  else if (pg.idx < pg.total - 1) pgSwitchChapter(pg.idx + 1, 0);
}
function pagePrev() {
  if (pg.curPage > 0) pgGoToPage(pg.curPage - 1, true);
  else if (pg.idx > 0) pgSwitchChapter(pg.idx - 1, -1);
}

// Handle tap-zone clicks: ignore if clicking on para-badge
function handlePageTap(e, zone) {
  if (e.target.closest('.para-badge')) return;
  if (zone === 'prev') pagePrev();
  else if (zone === 'next') pageNext();
  else toggleToolbar();
}

// Handle clicks on page-container: delegate to tap zones, but let badge clicks through
function handlePageContainerClick(e) {
  // If badge was clicked, let its own onclick handle it
  if (e.target.closest('.para-badge')) return;
  // If toolbar is open, let toolbar handle its own clicks
  if ($('pageToolbar') && $('pageToolbar').classList.contains('open')) return;

  const container = $('pageContainer');
  if (!container) return;
  const rect = container.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width;

  if (x < 0.28) pagePrev();
  else if (x > 0.72) pageNext();
  else toggleToolbar();
}
async function pgSwitchChapter(newIdx, startPageHint) {
  if (pg.animating) return;
  const d = cache.detail[pg.bid];
  if (!d || newIdx < 0 || newIdx >= d.chapters.length) return;

  const dir = newIdx > pg.idx ? 1 : -1;
  const viewport = $('pageViewport');
  if (!viewport) return;

  // Grab the current page element for animation
  const oldPageEl = viewport.querySelector(`.page-page[data-page="${pg.curPage}"]`);

  const chapterId = d.chapters[newIdx].ChapterID;
  // Ensure content is loaded
  if (!getContentCache(chapterId)) {
    if (oldPageEl) {
      oldPageEl.style.opacity = '0.5';
      oldPageEl.style.pointerEvents = 'none';
    }
    await fetchContent(chapterId);
    if (oldPageEl) {
      oldPageEl.style.opacity = '';
      oldPageEl.style.pointerEvents = '';
    }
  }
  const c = getContentCache(chapterId);
  if (!c || !c.paragraphs.length) return;

  // Update state
  pg.idx = newIdx;
  pg.curPage = 0;
  currentBookId = pg.bid;
  currentChapterIdx = newIdx;
  const chapterTitle = d.chapters[newIdx].Name || `第${newIdx+1}章`;
  saveReaderProgress(pg.bid, newIdx, chapterId, chapterTitle, pg.total);

  // Update header & footer
  const headerName = document.querySelector('.page-header .chapter-name');
  if (headerName) headerName.textContent = chapterTitle;
  const toolbarName = document.querySelector('.page-toolbar-top .chapter-name');
  if (toolbarName) toolbarName.textContent = chapterTitle;
  const footerLeft = document.querySelector('.page-footer-left');
  if (footerLeft) footerLeft.innerHTML = `<span>${escapeHtml(d.bookName)}</span><span>·</span><span>第${newIdx+1}/${pg.total}章</span>`;

  const navRow = document.querySelector('.page-toolbar-bottom .nav-row');
  if (navRow) {
    const btns = navRow.querySelectorAll('button');
    if (btns[0]) { btns[0].disabled = newIdx <= 0; btns[0].onclick = () => pgSwitchChapter(newIdx - 1, -1); }
    if (btns[2]) { btns[2].disabled = newIdx >= pg.total - 1; btns[2].onclick = () => pgSwitchChapter(newIdx + 1, 0); }
  }

  // Recalculate pages — this rebuilds viewport children
  pgCalculatePages();
  loadParaCommentCounts(chapterId);

  // Determine target page
  const targetPage = (startPageHint === -1) ? pg.totalPages - 1 : 0;

  // Hide all new pages except the target
  viewport.querySelectorAll('.page-page').forEach(p => {
    p.style.display = p.dataset.page == targetPage ? '' : 'none';
  });

  // Animate: old page slides out, new page slides in
  const mode = getReadMode();
  const newPageEl = viewport.querySelector(`.page-page[data-page="${targetPage}"]`);
  if (oldPageEl && newPageEl && oldPageEl.parentNode !== viewport) {
    if (mode === 'no-anim') {
      oldPageEl.remove();
    } else {
      // oldPageEl was removed by pgCalculatePages — re-add it for animation
      oldPageEl.style.display = '';
      oldPageEl.style.position = 'absolute';
      oldPageEl.style.inset = '0';
      viewport.insertBefore(oldPageEl, viewport.firstChild);

      pg.animating = true;
      newPageEl.style.display = '';
      newPageEl.style.transform = `translateX(${dir * 100}%)`;
      newPageEl.style.transition = 'none';
      oldPageEl.offsetHeight; // force reflow

      newPageEl.style.transition = 'transform 0.3s ease';
      oldPageEl.style.transition = 'transform 0.3s ease';
      oldPageEl.style.transform = `translateX(${-dir * 100}%)`;
      newPageEl.style.transform = 'translateX(0)';
      setTimeout(() => {
        oldPageEl.remove();
        newPageEl.style.transform = '';
        newPageEl.style.transition = '';
        pg.animating = false;
      }, 320);
    }
  }

  pg.curPage = targetPage;
  pgUpdateUI();

  // Update URL without full re-render
  const newHash = `reader?book_id=${pg.bid}&chapter_idx=${newIdx}`;
  if (location.hash.slice(1) !== newHash) {
    history.replaceState(null, '', '#' + newHash);
  }

  preloadAdjacent(pg.bid, newIdx);
}
function pgSliderGo(val) { pgGoToPage(parseInt(val) - 1, false); }

// ====== Chapter list panel ======
function showChapterList() {
  const bid = currentBookId || pg.bid;
  const d = cache.detail[bid];
  if (!d) return;
  const chapters = d.chapters;
  const resumeIdx = currentChapterIdx >= 0 ? currentChapterIdx : pg.idx;

  // Remove existing panel if any
  const old = $('chapterListOverlay');
  if (old) { old.remove(); return; }

  const panel = document.createElement('div');
  panel.id = 'chapterListOverlay';
  panel.className = 'chapter-list-overlay';
  panel.innerHTML = `
    <div class="chapter-list-header">
      <span>目录 · ${chapters.length} 章</span>
      <button class="icon-btn" onclick="closeChapterList()"><i data-lucide="x" width="16" height="16"></i></button>
    </div>
    <div class="chapter-list-scroll">${renderChapterItems(chapters, bid, resumeIdx)}</div>
  `;
  document.body.appendChild(panel);
  refreshIcons(panel);

  // Override chapter item clicks to close panel first
  panel.querySelectorAll('.chapter-item').forEach(item => {
    item.addEventListener('click', () => closeChapterList(), true);
  });

  // Scroll to current chapter
  requestAnimationFrame(() => {
    const cur = panel.querySelector('.chapter-item-current');
    if (cur) cur.scrollIntoView({ block: 'center' });
  });
}

function closeChapterList() {
  const panel = $('chapterListOverlay');
  if (panel) panel.remove();
}

// ====== Toolbar ======
function toggleToolbar() {
  const ptb = $('pageToolbar');
  if (ptb) { ptb.classList.toggle('open'); return; }
  const stb = $('readerToolbar');
  if (stb) stb.classList.toggle('hidden-toolbar');
}
function pgFontSize(d) { changeFontSize(d); setTimeout(()=>pgCalculatePages(),150); }
function pgLineHeight(d) { changeLineHeight(d); setTimeout(()=>pgCalculatePages(),150); }
function pgCalcDelayed() { setTimeout(()=>pgCalculatePages(),150); }

// ====== Touch gestures ======
function setupPgGestures() {
  const container = $('pageContainer');
  if (!container) return;
  
  let dragCurrentPage = null;
  
  container.addEventListener('touchstart', e => {
    if (pg.animating) return;
    pg.swipeStartX = e.touches[0].clientX;
    pg.swipeStartY = e.touches[0].clientY;
    pg.swipeStartTime = Date.now();
    pg.swipeActive = false;
    dragCurrentPage = null;
  }, { passive: true });
  
  container.addEventListener('touchmove', e => {
    if (pg.animating) return;
    const dx = e.touches[0].clientX - pg.swipeStartX;
    const dy = e.touches[0].clientY - pg.swipeStartY;
    if (!pg.swipeActive) {
      if (Math.abs(dx) < 15 || Math.abs(dy) > Math.abs(dx)) return;
      pg.swipeActive = true;
      const viewport = $('pageViewport');
      dragCurrentPage = viewport ? viewport.querySelector(`.page-page[data-page="${pg.curPage}"]`) : null;
      if (getReadMode() !== 'no-anim' && viewport && dragCurrentPage) {
        const prev = viewport.querySelector(`.page-page[data-page="${pg.curPage - 1}"]`);
        const next = viewport.querySelector(`.page-page[data-page="${pg.curPage + 1}"]`);
        if (prev) { prev.style.display = ''; prev.style.transform = 'translateX(-100%)'; }
        if (next) { next.style.display = ''; next.style.transform = 'translateX(100%)'; }
      }
    }
    const mode = getReadMode();
    if (mode !== 'no-anim' && pg.swipeActive && dragCurrentPage) {
      dragCurrentPage.style.transition = 'none';
      dragCurrentPage.style.transform = `translateX(${dx}px)`;
      const cw = container.clientWidth;
      const prev = $('pageViewport')?.querySelector(`.page-page[data-page="${pg.curPage - 1}"]`);
      const next = $('pageViewport')?.querySelector(`.page-page[data-page="${pg.curPage + 1}"]`);
      if (prev && prev.style.display !== 'none') { prev.style.transition = 'none'; prev.style.transform = `translateX(${-cw + dx}px)`; }
      if (next && next.style.display !== 'none') { next.style.transition = 'none'; next.style.transform = `translateX(${cw + dx}px)`; }
    }
  }, { passive: true });
  
  container.addEventListener('touchend', e => {
    if (!pg.swipeActive) return;
    pg.swipeActive = false;
    
    const dx = e.changedTouches[0].clientX - pg.swipeStartX;
    const cw = container.clientWidth;
    const threshold = cw * 0.15;
    
    let target = pg.curPage;
    if (Math.abs(dx) > threshold) {
      if (dx < -threshold && pg.curPage < pg.totalPages - 1) target = pg.curPage + 1;
      else if (dx > threshold && pg.curPage > 0) target = pg.curPage - 1;
    }
    
    const viewport = $('pageViewport');
    const dir = target > pg.curPage ? 1 : -1;

    const mode = getReadMode();
    if (target !== pg.curPage) {
      if (mode === 'no-anim') {
        if (viewport) {
          const prev = viewport.querySelector(`.page-page[data-page="${pg.curPage - 1}"]`);
          const next = viewport.querySelector(`.page-page[data-page="${pg.curPage + 1}"]`);
          if (prev) { prev.style.display = 'none'; prev.style.transform = ''; prev.style.transition = ''; }
          if (next) { next.style.display = 'none'; next.style.transform = ''; next.style.transition = ''; }
        }
        pgGoToPage(target, false);
        dragCurrentPage = null;
        return;
      }
      // Animate current page and target page to their final positions from drag position
      pg.animating = true;
      const targetEl = viewport ? viewport.querySelector(`.page-page[data-page="${target}"]`) : null;
      // Hide the other adjacent page
      const otherDir = -dir;
      const otherIdx = pg.curPage + otherDir;
      const otherEl = viewport ? viewport.querySelector(`.page-page[data-page="${otherIdx}"]`) : null;
      if (otherEl) { otherEl.style.display = 'none'; otherEl.style.transition = ''; otherEl.style.transform = ''; }
      // Apply transition animation
      const dur = '0.3s ease';
      if (dragCurrentPage) {
        dragCurrentPage.style.transition = `transform ${dur}`;
        dragCurrentPage.style.transform = `translateX(${-dir * 100}%)`;
      }
      if (targetEl) {
        targetEl.style.transition = `transform ${dur}`;
        targetEl.style.transform = 'translateX(0)';
      }
      const timeout = 320;
      setTimeout(() => {
        if (dragCurrentPage && dragCurrentPage.parentNode) { dragCurrentPage.style.display = 'none'; dragCurrentPage.style.transition = ''; dragCurrentPage.style.transform = ''; dragCurrentPage.style.boxShadow = ''; }
        if (targetEl) { targetEl.style.transition = ''; targetEl.style.transform = ''; }
        pg.animating = false;
      }, timeout);
      pg.curPage = target;
      pgUpdateUI();
    } else {
      if (mode === 'no-anim') {
        if (dragCurrentPage) { dragCurrentPage.style.transform = ''; dragCurrentPage.style.boxShadow = ''; }
        if (viewport) {
          const prev = viewport.querySelector(`.page-page[data-page="${pg.curPage - 1}"]`);
          const next = viewport.querySelector(`.page-page[data-page="${pg.curPage + 1}"]`);
          if (prev) { prev.style.display = 'none'; prev.style.transform = ''; }
          if (next) { next.style.display = 'none'; next.style.transform = ''; }
        }
        dragCurrentPage = null;
        return;
      }
      // Cancelled drag — animate everything back
      const snapDur = '0.25s ease';
      if (dragCurrentPage) {
        dragCurrentPage.style.transition = `transform ${snapDur}`;
        dragCurrentPage.style.transform = 'translateX(0)';
        dragCurrentPage.style.boxShadow = '';
      }
      const prev = viewport ? viewport.querySelector(`.page-page[data-page="${pg.curPage - 1}"]`) : null;
      const next = viewport ? viewport.querySelector(`.page-page[data-page="${pg.curPage + 1}"]`) : null;
      if (prev && prev.style.display !== 'none') {
        prev.style.transition = `transform ${snapDur}`;
        prev.style.transform = 'translateX(-100%)';
      }
      if (next && next.style.display !== 'none') {
        next.style.transition = `transform ${snapDur}`;
        next.style.transform = 'translateX(100%)';
      }
      pg.animating = true;
      setTimeout(() => {
        if (dragCurrentPage) { dragCurrentPage.style.transition = ''; dragCurrentPage.style.transform = ''; }
        if (prev) { prev.style.display = 'none'; prev.style.transition = ''; prev.style.transform = ''; }
        if (next) { next.style.display = 'none'; next.style.transition = ''; next.style.transform = ''; }
        pg.animating = false;
      }, 260);
    }
    dragCurrentPage = null;
  }, { passive: true });
  
  document.onkeydown = e => {
    if (e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA') return;
    const hash = location.hash.slice(1)||'';
    if (!hash.startsWith('reader')) return;
    if (getReadMode() === 'scroll') return;
    // If paragraph comment panel is open, Escape closes it first
    const paraPanel = $('paraCommentPanel');
    if (paraPanel && paraPanel.classList.contains('open')) {
      if (e.key==='Escape') { closeParaComment(); e.preventDefault(); }
      return;
    }
    if (e.key==='ArrowLeft'||e.key==='a') pagePrev();
    else if (e.key==='ArrowRight'||e.key==='d') pageNext();
    else if (e.key==='Escape') goBack();
  };
}

// ====== Read mode / settings change ======
window.addEventListener('read-mode-change', () => {
  const hash = location.hash.slice(1) || '';
  if (!hash.startsWith('reader')) return;
  const q = {};
  const qs = hash.split('?')[1];
  if (qs) new URLSearchParams(qs).forEach((v,k) => q[k] = v);
  if (q.book_id) renderReader($('app'), q.book_id, parseInt(q.chapter_idx||'0'));
});
window.addEventListener('reader-settings-change', () => {
  if (getReadMode() === 'scroll') return;
  const vp = $('pageViewport');
  if (vp) setTimeout(() => {
    pgCalculatePages();
  }, 150);
});

// ====== Resize handling ======
let pgResizeTimer = 0;
window.addEventListener('resize', () => {
  if (getReadMode() === 'scroll' || !$('pageViewport')) return;
  clearTimeout(pgResizeTimer);
  pgResizeTimer = setTimeout(() => {
    pgCalculatePages();
  }, 200);
});

// ====== Paragraph comments ======
async function loadParaCommentCounts(chapterId, bookId) {
  const bid = bookId || currentBookId;
  const counts = await fetchParagraphCommentCounts(chapterId, bid);
  // Update badges in both scroll and page readers
  document.querySelectorAll('.para-wrap').forEach(wrap => {
    const idx = wrap.dataset.paraIdx;
    if (idx === undefined) return;
    const count = counts[idx] || counts[String(idx)] || 0;
    const badge = wrap.querySelector('.para-badge');
    if (!badge) return;
    if (count > 0) {
      badge.textContent = count;
      wrap.classList.add('has-comments');
    } else {
      wrap.classList.remove('has-comments');
    }
  });
}

function openParaComment(paraIdx) {
  const chapterId = currentChapterKey || (cache.detail[currentBookId] ? cache.detail[currentBookId].chapters[currentChapterIdx].ChapterID : null);
  if (!chapterId) return;
  const c = getContentCache(chapterId);
  if (!c) return;
  const text = c.paragraphs[paraIdx] || '';

  // Show immediate feedback toast (comment API can be slow ~1-3s)
  showToast('段评加载中...');

  // Create or reuse panel
  let backdrop = $('paraCommentBackdrop');
  let panel = $('paraCommentPanel');
  if (!backdrop) {
    backdrop = document.createElement('div');
    backdrop.id = 'paraCommentBackdrop';
    backdrop.className = 'para-comment-backdrop';
    backdrop.onclick = closeParaComment;
    document.body.appendChild(backdrop);
  }
  if (!panel) {
    panel = document.createElement('div');
    panel.id = 'paraCommentPanel';
    panel.className = 'para-comment-panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    panel.innerHTML = `
      <div class="panel-drag-handle"></div>
      <div class="panel-header">
        <span class="panel-title" id="paraPanelTitle">段评</span>
        <button class="panel-close" onclick="closeParaComment()" aria-label="关闭"><i data-lucide="x" width="16" height="16"></i></button>
      </div>
      <div class="panel-quote"><p id="paraPanelQuote"></p></div>
      <div class="panel-comments" id="paraPanelComments"><div class="loading" style="padding:20px">加载中...</div></div>
    `;
    document.body.appendChild(panel);
    refreshIcons(panel);
  }

  // Set quote text
  $('paraPanelQuote').textContent = text;

  // Show panel
  backdrop.classList.add('open');
  panel.classList.remove('closing');
  panel.classList.add('open');

  // Store current context for comment loading
  panel.dataset.chapterId = chapterId;
  panel.dataset.paraIdx = paraIdx;

  // Load comments
  loadParaCommentList(chapterId, paraIdx, currentBookId);
}

async function loadParaCommentList(chapterId, paraIdx, bookId) {
  const container = $('paraPanelComments');
  if (!container) return;
  container.innerHTML = '<div class="panel-loading"><div class="panel-loading-spinner"></div><div style="font-size:12px;color:var(--text-muted)">加载中...</div></div>';
  try {
    const comments = await fetchParagraphComments(chapterId, paraIdx, bookId);
    if (!comments || !comments.length) {
      container.innerHTML = '<div class="panel-empty"><div class="panel-empty-icon">💬</div><div class="panel-empty-text">暂无段评</div></div>';
      return;
    }
    let html = '';
    for (const c of comments) {
      const user = c.user_name || c.nick_name || '匿名';
      const avatar = c.avatar_url || '';
      const text = c.content || c.text || '';
      const time = c.create_time || c.create_timestamp || 0;
      const digg = c.digg_count || 0;
      const imgs = c.images || [];
      const reply = c.reply_list || c.reply_comment || c.child_comments || null;
      const avatarHtml = avatar ? `<img class="comment-avatar" src="${avatar}" alt="" loading="lazy">` : `<div class="comment-avatar comment-avatar-placeholder">${escapeHtml(user.charAt(0))}</div>`;
      html += `<div class="comment-item">${avatarHtml}<div class="comment-body"><div class="comment-user">${escapeHtml(user)}</div><div class="comment-text">${escapeHtml(text)}</div>`;
      if (imgs.length > 0) {
        const imgGridClass = imgs.length === 1 ? 'comment-images single-img' : 'comment-images';
        html += `<div class="${imgGridClass}">`;
        for (const src of imgs) html += `<img class="comment-img" src="${src}" alt="评论图片" loading="lazy" onclick="openImageViewer(this.src)">`;
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
          const rcAvatar = rc.avatar_url || '';
          const rcUser = rc.user_name || rc.nick_name || '匿名';
          const rcAvatarHtml = rcAvatar ? `<img class="comment-avatar comment-avatar-sm" src="${rcAvatar}" alt="" loading="lazy">` : `<div class="comment-avatar comment-avatar-sm comment-avatar-placeholder">?</div>`;
          html += `<div class="comment-item">${rcAvatarHtml}<div class="comment-body"><div class="comment-user">${escapeHtml(rcUser)}</div><div class="comment-text">${escapeHtml(rc.content||rc.text||'')}</div></div></div>`;
        }
        html += '</div>';
      }
      html += '</div></div>';
    }
    container.innerHTML = html;
  } catch(e) {
    container.innerHTML = '<div class="panel-empty"><div class="panel-empty-icon">⚠️</div><div class="panel-empty-text">加载失败，请稍后重试</div></div>';
    showToast('段评加载失败');
  }
}

function closeParaComment() {
  const backdrop = $('paraCommentBackdrop');
  const panel = $('paraCommentPanel');
  if (!panel || !panel.classList.contains('open')) return;
  backdrop.classList.remove('open');
  panel.classList.remove('open');
  panel.classList.add('closing');
  panel.addEventListener('animationend', function handler() {
    panel.classList.remove('closing');
    panel.removeEventListener('animationend', handler);
  });
}

// Long-press on paragraph to open comment panel (mobile support)
(function() {
  let longPressTimer = null;
  let longPressTarget = null;
  document.addEventListener('touchstart', function(e) {
    const wrap = e.target.closest('.para-wrap');
    if (!wrap) return;
    // Don't trigger on badge click
    if (e.target.closest('.para-badge')) return;
    longPressTarget = wrap;
    longPressTimer = setTimeout(() => {
      const idx = wrap.dataset.paraIdx;
      if (idx !== undefined) openParaComment(parseInt(idx));
      longPressTarget = null;
    }, 600);
  }, { passive: true });
  document.addEventListener('touchmove', function() {
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
  }, { passive: true });
  document.addEventListener('touchend', function() {
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
  }, { passive: true });
})();

// Close panel on Escape key
document.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') closeParaComment();
});

// ====== Image click ======
document.addEventListener('click', function(e) {
  const img = e.target.closest('.content-img');
  if (img && !e.target.closest('.img-viewer-overlay')) openImageViewer(img.src);
});