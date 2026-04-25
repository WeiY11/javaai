// ===== 全局 =====
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    const iconMap = { success: 'check_circle', error: 'error', info: 'info' };
    toast.innerHTML = `
        <span class="material-icons-round toast-icon">${iconMap[type]}</span>
        <div class="toast-message">${message}</div>
    `;
    container.appendChild(toast);
    requestAnimationFrame(() => {
        requestAnimationFrame(() => toast.classList.add('show'));
    });
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

const chatMessages = document.getElementById('chatMessages');
const userInput = document.getElementById('userInput');
const sendBtn = document.getElementById('sendBtn');
const modelSelect = document.getElementById('modelSelect');
const sessionId = 'session-' + Math.random().toString(36).substring(2, 9);

marked.setOptions({
    highlight: (code, lang) => {
        const language = hljs.getLanguage(lang) ? lang : 'plaintext';
        return hljs.highlight(code, { language }).value;
    },
    langPrefix: 'hljs language-',
    breaks: true
});

// ========== PART 1: Chat ==========
function scrollToBottom() { chatMessages.scrollTop = chatMessages.scrollHeight; }

function appendMessage(content, isUser) {
    const msg = document.createElement('div');
    msg.className = `message ${isUser ? 'user-message' : 'ai-message'}`;
    const av = document.createElement('div');
    av.className = `avatar ${isUser ? 'user-avatar' : 'ai-avatar'}`;
    av.innerHTML = `<span class="material-icons-round">${isUser ? 'person' : 'smart_toy'}</span>`;
    const cd = document.createElement('div');
    cd.className = 'message-content markdown-body';
    if (isUser) cd.textContent = content;
    msg.appendChild(av);
    msg.appendChild(cd);
    chatMessages.appendChild(msg);
    scrollToBottom();
    return cd;
}

async function streamFetch(url, targetDiv) {
    const res = await fetch(url, { method: 'GET', headers: { 'Accept': 'text/event-stream' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let full = '', buf = '';
    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        const evts = buf.split(/\r?\n\r?\n/);
        buf = evts.pop() || '';
        for (const evt of evts) {
            const lines = evt.split(/\r?\n/);
            let data = [];
            for (const l of lines) { if (l.startsWith('data:')) { let d = l.substring(5); if (d.startsWith(' ')) d = d.substring(1); data.push(d); } }
            if (data.length) {
                full += data.join('\n');
                if (targetDiv) {
                    targetDiv.innerHTML = DOMPurify.sanitize(marked.parse(full)) + '<span class="cursor-blink"></span>';
                    targetDiv.scrollTop = targetDiv.scrollHeight;
                    if (targetDiv.closest('.chat-messages')) scrollToBottom();
                }
            }
        }
    }
    return full;
}

async function sendMessage() {
    const msg = userInput.value.trim();
    if (!msg) return;
    userInput.value = '';
    sendBtn.disabled = true;
    appendMessage(msg, true);
    const ai = appendMessage('', false);
    ai.innerHTML = '<span class="cursor-blink"></span>';
    try {
        const full = await streamFetch(`/api/chat/stream?message=${encodeURIComponent(msg)}&provider=${encodeURIComponent(modelSelect.value)}&sessionId=${encodeURIComponent(sessionId)}`, ai);
        ai.innerHTML = DOMPurify.sanitize(marked.parse(full));
    } catch (e) { ai.innerHTML = `<em>出错：${e.message}</em>`; }
    finally { sendBtn.disabled = false; userInput.focus(); }
}

userInput.addEventListener('keypress', e => { if (e.key === 'Enter') sendMessage(); });
sendBtn.addEventListener('click', sendMessage);

// ========== PART 2: Tab Nav ==========
document.querySelectorAll('.sidebar-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.sidebar-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const t = btn.dataset.tab;
        document.getElementById('panelChat').classList.toggle('hidden', t !== 'chat');
        document.getElementById('panelData').classList.toggle('hidden', t !== 'data');
        if (t === 'data' && allFiles.length === 0) loadFiles('');
    });
});

// ========== PART 3: Data Manager ==========
let allFiles = [], currentDir = '', currentSort = { key: 'name', asc: true }, currentPreviewPath = null;

async function loadFiles(dir) {
    currentDir = dir;
    updateBreadcrumb(dir);
    const skel = document.getElementById('skeletonLoader');
    const tbody = document.getElementById('fileTableBody');
    skel.classList.remove('hidden');
    tbody.innerHTML = '';
    try {
        const res = await fetch(`/api/files?dir=${encodeURIComponent(dir)}`);
        const json = await res.json();
        if (json.code !== 200) { showToast(json.message, 'error'); throw new Error(json.message); }
        const data = json.data;
        allFiles = data.items || [];
        updateStats();
        renderFileTable();
    } catch (e) { tbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:#f87171;padding:30px;">加载失败: ${e.message}</td></tr>`; }
    finally { skel.classList.add('hidden'); }
}

function updateStats() {
    const counts = { total: 0, json: 0, csv: 0, image: 0, model: 0 };
    allFiles.forEach(f => { if (!f.isDir) { counts.total++; if (counts[f.category] !== undefined) counts[f.category]++; } });
    document.getElementById('statTotal').textContent = counts.total;
    document.getElementById('statJson').textContent = counts.json;
    document.getElementById('statCsv').textContent = counts.csv;
    document.getElementById('statImage').textContent = counts.image;
    document.getElementById('statModel').textContent = counts.model;
}

function updateBreadcrumb(dir) {
    const bc = document.getElementById('breadcrumb');
    bc.innerHTML = '';
    const root = document.createElement('span');
    root.className = 'bc-item';
    root.innerHTML = '<span class="material-icons-round">home</span> 根目录';
    root.addEventListener('click', () => loadFiles(''));
    bc.appendChild(root);
    if (dir) {
        const parts = dir.split('/');
        let acc = '';
        parts.forEach(p => {
            acc += (acc ? '/' : '') + p;
            const sep = document.createElement('span');
            sep.className = 'bc-sep';
            sep.textContent = '›';
            bc.appendChild(sep);
            const item = document.createElement('span');
            item.className = 'bc-item';
            item.textContent = p;
            const path = acc;
            item.addEventListener('click', () => loadFiles(path));
            bc.appendChild(item);
        });
    }
}

const iconMap = { folder:'📁', json:'📋', csv:'📊', image:'🖼️', model:'🧠', python:'🐍', markdown:'📝', latex:'📄', pdf:'📕' };
function getIcon(item) { return item.isDir ? '📁' : (iconMap[item.category] || '📎'); }
function fmtSize(b) { if (!b) return '-'; if (b < 1024) return b + ' B'; if (b < 1048576) return (b/1024).toFixed(1) + ' KB'; return (b/1048576).toFixed(1) + ' MB'; }
function fmtDate(ts) { if (!ts) return '-'; const d = new Date(ts); return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', {hour:'2-digit',minute:'2-digit'}); }

function renderFileTable() {
    const tbody = document.getElementById('fileTableBody');
    const search = document.getElementById('searchInput').value.toLowerCase();
    const filter = document.getElementById('filterCategory').value;
    let items = [...allFiles];
    if (search) items = items.filter(f => f.name.toLowerCase().includes(search));
    if (filter !== 'all') items = items.filter(f => f.category === filter);
    items.sort((a, b) => {
        if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
        let va = a[currentSort.key], vb = b[currentSort.key];
        if (typeof va === 'string') { va = va.toLowerCase(); vb = (vb||'').toLowerCase(); }
        let c = va < vb ? -1 : va > vb ? 1 : 0;
        return currentSort.asc ? c : -c;
    });
    if (!items.length) { tbody.innerHTML = `<tr><td colspan="5"><div class="empty-state"><div class="empty-icon">📭</div><div class="empty-text">没有匹配的文件</div></div></td></tr>`; return; }
    tbody.innerHTML = items.map(item => `
        <tr data-path="${item.path}" data-isdir="${item.isDir}">
            <td><div class="file-name"><div class="file-icon-wrap icon-${item.category}">${getIcon(item)}</div><span>${item.name}</span></div></td>
            <td><span class="badge badge-${item.category}">${item.category}</span></td>
            <td style="color:#64748b">${item.isDir ? '-' : fmtSize(item.size)}</td>
            <td style="color:#64748b">${fmtDate(item.lastModified)}</td>
            <td>${item.isDir ? `<button class="btn-action" onclick="event.stopPropagation();loadFiles('${item.path}')">打开</button>` : `<button class="btn-action" onclick="event.stopPropagation();previewFile('${item.path}')">查看</button>`}</td>
        </tr>
    `).join('');
    tbody.querySelectorAll('tr').forEach(tr => {
        tr.addEventListener('click', () => {
            if (tr.dataset.isdir === 'true') loadFiles(tr.dataset.path);
            else previewFile(tr.dataset.path);
        });
    });
}

document.querySelectorAll('.file-table th.sortable').forEach(th => {
    th.addEventListener('click', () => {
        const key = th.dataset.sort;
        if (currentSort.key === key) currentSort.asc = !currentSort.asc;
        else currentSort = { key, asc: true };
        document.querySelectorAll('.file-table th.sortable').forEach(t => { t.classList.remove('sort-active'); t.querySelector('.sort-arrow').textContent = 'unfold_more'; });
        th.classList.add('sort-active');
        th.querySelector('.sort-arrow').textContent = currentSort.asc ? 'expand_less' : 'expand_more';
        renderFileTable();
    });
});

document.getElementById('searchInput').addEventListener('input', renderFileTable);
document.getElementById('filterCategory').addEventListener('change', renderFileTable);

async function previewFile(path) {
    currentPreviewPath = path;
    const panel = document.getElementById('previewPanel');
    const content = document.getElementById('previewContent');
    const title = document.getElementById('previewTitle');
    const ar = document.getElementById('analyzeResult');
    panel.classList.remove('hidden');
    ar.classList.add('hidden');
    content.innerHTML = '<div style="text-align:center;padding:50px;color:#475569;"><span class="material-icons-round" style="font-size:32px;display:block;margin-bottom:8px;">hourglass_empty</span>加载中...</div>';
    title.textContent = path.split('/').pop();
    // highlight active row
    document.querySelectorAll('.file-table tbody tr').forEach(tr => tr.classList.toggle('active-row', tr.dataset.path === path));
    try {
        const res = await fetch(`/api/files/content?path=${encodeURIComponent(path)}`);
        const json = await res.json();
        if (json.code !== 200) { content.innerHTML = `<div style="color:#f87171;">${json.message}</div>`; showToast(json.message, 'error'); return; }
        const data = json.data;
        if (data.type === 'image') content.innerHTML = `<img src="${data.content}" alt="${data.name}">`;
        else if (data.type === 'json') { try { content.innerHTML = `<pre><code class="hljs language-json">${hljs.highlight(JSON.stringify(JSON.parse(data.content),null,2),{language:'json'}).value}</code></pre>`; } catch { content.innerHTML = `<pre>${escHtml(data.content)}</pre>`; } }
        else if (data.type === 'csv') content.innerHTML = csvTable(data.content);
        else if (data.type === 'markdown') content.innerHTML = DOMPurify.sanitize(marked.parse(data.content));
        else if (data.type === 'binary') content.innerHTML = `<div class="empty-state"><span class="material-icons-round empty-icon">inventory_2</span><div class="empty-text">${data.content}</div></div>`;
        else content.innerHTML = `<pre>${escHtml(data.content)}</pre>`;
    } catch (e) { content.innerHTML = `<div style="color:#f87171;">加载失败: ${e.message}</div>`; }
}

function csvTable(csv) {
    const lines = csv.trim().split('\n');
    if (!lines.length) return '<p>空文件</p>';
    const hd = lines[0].split(',');
    let h = '<table><thead><tr>' + hd.map(c => `<th>${escHtml(c.trim())}</th>`).join('') + '</tr></thead><tbody>';
    for (let i = 1; i < lines.length; i++) { if (!lines[i].trim()) continue; h += '<tr>' + lines[i].split(',').map(c => `<td>${escHtml(c.trim())}</td>`).join('') + '</tr>'; }
    return h + '</tbody></table>';
}
function escHtml(t) { const d = document.createElement('div'); d.textContent = t; return d.innerHTML; }

document.getElementById('btnClosePreview').addEventListener('click', () => {
    document.getElementById('previewPanel').classList.add('hidden');
    document.querySelectorAll('.file-table tbody tr').forEach(tr => tr.classList.remove('active-row'));
    currentPreviewPath = null;
});

document.getElementById('btnAnalyze').addEventListener('click', async () => {
    if (!currentPreviewPath) return;
    const ar = document.getElementById('analyzeResult');
    const ac = document.getElementById('analyzeContent');
    const btn = document.getElementById('btnAnalyze');
    ar.classList.remove('hidden');
    ac.innerHTML = '<span class="cursor-blink"></span>';
    btn.disabled = true;
    btn.querySelector('span:last-child').textContent = '分析中...';
    try {
        const full = await streamFetch(`/api/files/analyze?path=${encodeURIComponent(currentPreviewPath)}&provider=${encodeURIComponent(modelSelect.value)}&sessionId=${encodeURIComponent('a-' + sessionId)}`, ac);
        ac.innerHTML = DOMPurify.sanitize(marked.parse(full));
    } catch (e) { ac.innerHTML = `<em>分析出错：${e.message}</em>`; }
    finally { btn.disabled = false; btn.querySelector('span:last-child').textContent = 'AI 分析'; }
});

// ========== PART 4: Batch Dir Analysis ==========
let batchTaskId = null;
let batchPollTimer = null;

document.getElementById('btnAnalyzeAll').addEventListener('click', async () => {
    const btn = document.getElementById('btnAnalyzeAll');
    const modal = document.getElementById('batchModal');
    const progressSection = document.getElementById('batchProgressSection');
    const resultsSection = document.getElementById('batchResultsSection');
    const progressText = document.getElementById('batchProgressText');
    const progressCount = document.getElementById('batchProgressCount');
    const progressBar = document.getElementById('batchProgressBar');
    const currentFile = document.getElementById('batchCurrentFile');
    const resultsList = document.getElementById('batchResultsList');
    const title = document.getElementById('batchModalTitle');

    // 重置 UI
    progressSection.classList.remove('hidden');
    resultsSection.classList.add('hidden');
    progressText.textContent = '正在扫描文件...';
    progressCount.textContent = '0/0';
    progressBar.style.width = '0%';
    currentFile.textContent = '等待开始...';
    resultsList.innerHTML = '';
    title.textContent = currentDir ? `分析目录: ${currentDir}` : '分析根目录所有文件';

    // 显示模态框
    modal.classList.remove('hidden');
    requestAnimationFrame(() => modal.classList.add('show'));
    btn.disabled = true;
    btn.querySelector('span:last-child').textContent = '分析中...';

    try {
        // 发起批量分析请求
        const res = await fetch('/api/analysis/batch-dir', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ dir: currentDir, provider: modelSelect.value })
        });
        const json = await res.json();

        if (json.code !== 200) {
            progressText.textContent = '⚠️ ' + json.message;
            currentFile.textContent = '';
            btn.disabled = false;
            btn.querySelector('span:last-child').textContent = '分析全部';
            showToast(json.message, 'error');
            return;
        }

        const data = json.data;

        batchTaskId = data.taskId;
        progressText.textContent = `正在分析 ${data.fileCount} 个文件...`;
        progressCount.textContent = `0/${data.fileCount}`;

        // 轮询进度
        batchPollTimer = setInterval(async () => {
            try {
                const pRes = await fetch(`/api/analysis/batch/progress?taskId=${encodeURIComponent(batchTaskId)}`);
                const pJson = await pRes.json();
                if (pJson.code !== 200) return;
                const progress = pJson.data;

                const pct = progress.totalCount > 0 ? Math.round(progress.completedCount / progress.totalCount * 100) : 0;
                progressBar.style.width = pct + '%';
                progressCount.textContent = `${progress.completedCount}/${progress.totalCount}`;
                currentFile.textContent = progress.currentFile ? `正在分析: ${progress.currentFile}` : '处理中...';

                if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
                    clearInterval(batchPollTimer);
                    batchPollTimer = null;
                    progressBar.style.width = '100%';

                    if (progress.status === 'COMPLETED') {
                        progressText.textContent = '✅ 分析完成！';
                        currentFile.textContent = `耗时: ${formatDuration(progress.startTime, progress.endTime)}`;
                    } else {
                        progressText.textContent = '❌ 分析失败';
                        currentFile.textContent = progress.errorMessage || '';
                    }

                    // 获取结果
                    await loadBatchResults(batchTaskId);
                    btn.disabled = false;
                    btn.querySelector('span:last-child').textContent = '分析全部';
                }
            } catch (e) {
                console.error('轮询进度失败', e);
            }
        }, 1500);

    } catch (e) {
        progressText.textContent = '❌ 请求失败: ' + e.message;
        currentFile.textContent = '';
        btn.disabled = false;
        btn.querySelector('span:last-child').textContent = '分析全部';
    }
});

async function loadBatchResults(taskId) {
    const resultsSection = document.getElementById('batchResultsSection');
    const resultsList = document.getElementById('batchResultsList');

    try {
        const res = await fetch(`/api/analysis/batch/result?taskId=${encodeURIComponent(taskId)}`);
        const json = await res.json();
        if (json.code !== 200) { showToast(json.message, 'error'); return; }
        const data = json.data;

        if (!data.results || data.results.length === 0) {
            resultsList.innerHTML = '<div class="empty-state"><div class="empty-text">暂无结果</div></div>';
            resultsSection.classList.remove('hidden');
            return;
        }

        resultsList.innerHTML = data.results.map((item, idx) => `
            <div class="batch-result-item ${item.success ? 'success' : 'failed'}">
                <div class="batch-result-header" onclick="toggleBatchResult(${idx})">
                    <div class="batch-result-left">
                        <span class="material-icons-round batch-status-icon">${item.success ? 'check_circle' : 'error'}</span>
                        <span class="batch-result-filename">${escHtml(item.fileName || item.filePath)}</span>
                    </div>
                    <span class="material-icons-round batch-expand-icon" id="batchExpand${idx}">expand_more</span>
                </div>
                <div class="batch-result-body hidden" id="batchBody${idx}">
                    <div class="markdown-body">${item.success
                        ? DOMPurify.sanitize(marked.parse(item.content || ''))
                        : `<div style="color:#f87171;">${escHtml(item.error || '未知错误')}</div>`
                    }</div>
                </div>
            </div>
        `).join('');

        resultsSection.classList.remove('hidden');
    } catch (e) {
        resultsList.innerHTML = `<div style="color:#f87171;padding:16px;">获取结果失败: ${e.message}</div>`;
        resultsSection.classList.remove('hidden');
    }
}

function toggleBatchResult(idx) {
    const body = document.getElementById(`batchBody${idx}`);
    const icon = document.getElementById(`batchExpand${idx}`);
    const isHidden = body.classList.contains('hidden');
    body.classList.toggle('hidden');
    icon.textContent = isHidden ? 'expand_less' : 'expand_more';
}

function formatDuration(startStr, endStr) {
    if (!startStr || !endStr) return '-';
    const start = new Date(startStr.replace(' ', 'T'));
    const end = new Date(endStr.replace(' ', 'T'));
    const diff = Math.round((end - start) / 1000);
    if (diff < 60) return diff + '秒';
    return Math.floor(diff / 60) + '分' + (diff % 60) + '秒';
}

// 关闭模态框
document.getElementById('btnCloseBatchModal').addEventListener('click', () => {
    const modal = document.getElementById('batchModal');
    modal.classList.remove('show');
    setTimeout(() => modal.classList.add('hidden'), 300);
    if (batchPollTimer) {
        clearInterval(batchPollTimer);
        batchPollTimer = null;
    }
    // 恢复按钮状态
    const btn = document.getElementById('btnAnalyzeAll');
    btn.disabled = false;
    btn.querySelector('span:last-child').textContent = '分析全部';
});

window.onload = () => userInput.focus();
