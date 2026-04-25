// ===== 全局变量 =====
const chatMessages = document.getElementById('chatMessages');
const userInput = document.getElementById('userInput');
const sendBtn = document.getElementById('sendBtn');
const modelSelect = document.getElementById('modelSelect');
const sessionId = 'session-' + Math.random().toString(36).substring(2, 9);

// ===== Marked.js 配置 =====
marked.setOptions({
    highlight: function(code, lang) {
        const language = hljs.getLanguage(lang) ? lang : 'plaintext';
        return hljs.highlight(code, { language }).value;
    },
    langPrefix: 'hljs language-',
    breaks: true
});

// ====================================================================
//  PART 1: AI 对话功能 (与之前一致)
// ====================================================================

function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function appendMessage(content, isUser) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${isUser ? 'user-message' : 'ai-message'}`;
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content markdown-body';
    if (isUser) { contentDiv.textContent = content; }
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
    return contentDiv;
}

async function sendMessage() {
    const message = userInput.value.trim();
    if (!message) return;
    const selectedModel = modelSelect.value;
    userInput.value = '';
    sendBtn.disabled = true;
    modelSelect.disabled = true;
    appendMessage(message, true);
    const aiContentDiv = appendMessage('', false);
    aiContentDiv.innerHTML = '<span class="cursor-blink"></span>';

    try {
        const url = `/api/chat/stream?message=${encodeURIComponent(message)}&provider=${encodeURIComponent(selectedModel)}&sessionId=${encodeURIComponent(sessionId)}`;
        const fullResponse = await streamFetch(url, aiContentDiv);
        let html = marked.parse(fullResponse);
        aiContentDiv.innerHTML = DOMPurify.sanitize(html);
    } catch (error) {
        aiContentDiv.innerHTML = `<em>出错了：${error.message}</em>`;
    } finally {
        sendBtn.disabled = false;
        modelSelect.disabled = false;
        userInput.focus();
    }
}

/**
 * 通用的 SSE 流式读取函数
 * @returns {Promise<string>} 完整的响应文本
 */
async function streamFetch(url, targetDiv) {
    const response = await fetch(url, { method: 'GET', headers: { 'Accept': 'text/event-stream' } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let fullResponse = '';
    let buffer = '';

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split(/\r?\n\r?\n/);
        buffer = events.pop() || '';
        for (const event of events) {
            const lines = event.split(/\r?\n/);
            let eventData = [];
            for (const line of lines) {
                if (line.startsWith('data:')) {
                    let d = line.substring(5);
                    if (d.startsWith(' ')) d = d.substring(1);
                    eventData.push(d);
                }
            }
            if (eventData.length > 0) {
                fullResponse += eventData.join('\n');
                if (targetDiv) {
                    let html = marked.parse(fullResponse);
                    html = DOMPurify.sanitize(html);
                    targetDiv.innerHTML = html + '<span class="cursor-blink"></span>';
                    targetDiv.scrollTop = targetDiv.scrollHeight;
                    // 也滚动父容器
                    if (targetDiv.closest('.chat-messages')) scrollToBottom();
                }
            }
        }
    }
    return fullResponse;
}

userInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') sendMessage(); });
sendBtn.addEventListener('click', sendMessage);

// ====================================================================
//  PART 2: Tab 导航
// ====================================================================

document.querySelectorAll('.nav-tab').forEach(tab => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        const targetPanel = tab.dataset.tab;
        document.getElementById('panelChat').classList.toggle('hidden', targetPanel !== 'chat');
        document.getElementById('panelData').classList.toggle('hidden', targetPanel !== 'data');
        if (targetPanel === 'data' && allFiles.length === 0) {
            loadFiles('');
        }
    });
});

// ====================================================================
//  PART 3: 数据管理面板
// ====================================================================

let allFiles = [];
let currentDir = '';
let currentSort = { key: 'name', asc: true };
let currentPreviewPath = null;

// ----- 文件列表加载 -----
async function loadFiles(dir) {
    currentDir = dir;
    updateBreadcrumb(dir);
    try {
        const res = await fetch(`/api/files?dir=${encodeURIComponent(dir)}`);
        const data = await res.json();
        allFiles = data.items || [];
        renderFileTable();
    } catch (e) {
        document.getElementById('fileTableBody').innerHTML = `<tr><td colspan="5" style="text-align:center;color:#f87171;">加载失败: ${e.message}</td></tr>`;
    }
}

// ----- 面包屑导航 -----
function updateBreadcrumb(dir) {
    const bc = document.getElementById('breadcrumb');
    bc.innerHTML = '';
    const root = document.createElement('span');
    root.className = 'breadcrumb-item';
    root.textContent = '🏠 根目录';
    root.dataset.dir = '';
    root.addEventListener('click', () => loadFiles(''));
    bc.appendChild(root);

    if (dir) {
        const parts = dir.split('/');
        let accumulated = '';
        for (const part of parts) {
            accumulated += (accumulated ? '/' : '') + part;
            const sep = document.createElement('span');
            sep.className = 'breadcrumb-sep';
            sep.textContent = ' / ';
            bc.appendChild(sep);
            const item = document.createElement('span');
            item.className = 'breadcrumb-item';
            item.textContent = part;
            const dirPath = accumulated;
            item.addEventListener('click', () => loadFiles(dirPath));
            bc.appendChild(item);
        }
    }
}

// ----- 文件图标 -----
function getFileIcon(item) {
    if (item.isDir) return '📁';
    const cat = item.category;
    const icons = { json: '📋', csv: '📊', image: '🖼️', model: '🧠', python: '🐍', markdown: '📝', latex: '📄', pdf: '📕' };
    return icons[cat] || '📎';
}

// ----- 格式化文件大小 -----
function formatSize(bytes) {
    if (!bytes || bytes === 0) return '-';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// ----- 格式化时间 -----
function formatDate(ts) {
    if (!ts) return '-';
    const d = new Date(ts);
    return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

// ----- 渲染文件表格 -----
function renderFileTable() {
    const tbody = document.getElementById('fileTableBody');
    const search = document.getElementById('searchInput').value.toLowerCase();
    const filter = document.getElementById('filterCategory').value;

    let items = [...allFiles];

    // 搜索过滤
    if (search) {
        items = items.filter(f => f.name.toLowerCase().includes(search));
    }
    // 类型过滤
    if (filter !== 'all') {
        items = items.filter(f => f.category === filter);
    }

    // 排序
    items.sort((a, b) => {
        // 目录永远排在前面
        if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
        let va = a[currentSort.key];
        let vb = b[currentSort.key];
        if (typeof va === 'string') va = va.toLowerCase();
        if (typeof vb === 'string') vb = vb.toLowerCase();
        let cmp = va < vb ? -1 : va > vb ? 1 : 0;
        return currentSort.asc ? cmp : -cmp;
    });

    if (items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5"><div class="empty-state"><div class="empty-icon">📭</div><div>没有找到匹配的文件</div></div></td></tr>`;
        return;
    }

    tbody.innerHTML = items.map(item => `
        <tr>
            <td>
                <span class="file-name" data-path="${item.path}" data-isdir="${item.isDir}">
                    <span class="file-icon">${getFileIcon(item)}</span>
                    ${item.name}
                </span>
            </td>
            <td><span class="category-badge badge-${item.category}">${item.category}</span></td>
            <td>${item.isDir ? '-' : formatSize(item.size)}</td>
            <td>${formatDate(item.lastModified)}</td>
            <td>
                ${item.isDir
                    ? `<button class="btn btn-view" onclick="loadFiles('${item.path}')">打开</button>`
                    : `<button class="btn btn-view" onclick="previewFile('${item.path}')">查看</button>`
                }
            </td>
        </tr>
    `).join('');

    // 绑定文件名点击事件
    tbody.querySelectorAll('.file-name').forEach(el => {
        el.addEventListener('click', () => {
            if (el.dataset.isdir === 'true') {
                loadFiles(el.dataset.path);
            } else {
                previewFile(el.dataset.path);
            }
        });
    });
}

// ----- 排序 -----
document.querySelectorAll('.file-table th.sortable').forEach(th => {
    th.addEventListener('click', () => {
        const key = th.dataset.sort;
        if (currentSort.key === key) {
            currentSort.asc = !currentSort.asc;
        } else {
            currentSort = { key, asc: true };
        }
        document.querySelectorAll('.file-table th.sortable').forEach(t => t.classList.remove('sort-active'));
        th.classList.add('sort-active');
        renderFileTable();
    });
});

// ----- 搜索和过滤 -----
document.getElementById('searchInput').addEventListener('input', renderFileTable);
document.getElementById('filterCategory').addEventListener('change', renderFileTable);

// ----- 文件预览 -----
async function previewFile(path) {
    currentPreviewPath = path;
    const panel = document.getElementById('previewPanel');
    const content = document.getElementById('previewContent');
    const title = document.getElementById('previewTitle');
    const analyzeResult = document.getElementById('analyzeResult');

    panel.classList.remove('hidden');
    analyzeResult.classList.add('hidden');
    content.innerHTML = '<div style="text-align:center;padding:40px;color:#64748b;">加载中...</div>';
    title.textContent = path.split('/').pop();

    // 调整文件列表宽度
    document.getElementById('fileListPanel').style.flex = '1';

    try {
        const res = await fetch(`/api/files/content?path=${encodeURIComponent(path)}`);
        const data = await res.json();

        if (data.error) {
            content.innerHTML = `<div style="color:#f87171;">${data.error}</div>`;
            return;
        }

        if (data.type === 'image') {
            content.innerHTML = `<img src="${data.content}" alt="${data.name}">`;
        } else if (data.type === 'json') {
            try {
                const parsed = JSON.parse(data.content);
                const pretty = JSON.stringify(parsed, null, 2);
                content.innerHTML = `<pre><code class="hljs language-json">${hljs.highlight(pretty, {language: 'json'}).value}</code></pre>`;
            } catch {
                content.innerHTML = `<pre>${data.content}</pre>`;
            }
        } else if (data.type === 'csv') {
            content.innerHTML = csvToTable(data.content);
        } else if (data.type === 'markdown') {
            content.innerHTML = DOMPurify.sanitize(marked.parse(data.content));
        } else if (data.type === 'binary') {
            content.innerHTML = `<div class="empty-state"><div class="empty-icon">📦</div><div>${data.content}</div></div>`;
        } else {
            content.innerHTML = `<pre>${escapeHtml(data.content)}</pre>`;
        }
    } catch (e) {
        content.innerHTML = `<div style="color:#f87171;">加载失败: ${e.message}</div>`;
    }
}

// ----- CSV 转表格 -----
function csvToTable(csvText) {
    const lines = csvText.trim().split('\n');
    if (lines.length === 0) return '<p>空文件</p>';
    const headers = lines[0].split(',');
    let html = '<table><thead><tr>';
    headers.forEach(h => { html += `<th>${escapeHtml(h.trim())}</th>`; });
    html += '</tr></thead><tbody>';
    for (let i = 1; i < lines.length; i++) {
        if (!lines[i].trim()) continue;
        const cols = lines[i].split(',');
        html += '<tr>';
        cols.forEach(c => { html += `<td>${escapeHtml(c.trim())}</td>`; });
        html += '</tr>';
    }
    html += '</tbody></table>';
    return html;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ----- 关闭预览 -----
document.getElementById('btnClosePreview').addEventListener('click', () => {
    document.getElementById('previewPanel').classList.add('hidden');
    currentPreviewPath = null;
});

// ----- AI 分析 -----
document.getElementById('btnAnalyze').addEventListener('click', async () => {
    if (!currentPreviewPath) return;

    const analyzeResult = document.getElementById('analyzeResult');
    const analyzeContent = document.getElementById('analyzeContent');
    const btnAnalyze = document.getElementById('btnAnalyze');
    const selectedModel = modelSelect.value;

    analyzeResult.classList.remove('hidden');
    analyzeContent.innerHTML = '<span class="cursor-blink"></span>';
    btnAnalyze.disabled = true;
    btnAnalyze.textContent = '🤖 分析中...';

    try {
        const url = `/api/files/analyze?path=${encodeURIComponent(currentPreviewPath)}&provider=${encodeURIComponent(selectedModel)}&sessionId=${encodeURIComponent('analyze-' + sessionId)}`;
        const fullResponse = await streamFetch(url, analyzeContent);
        let html = marked.parse(fullResponse);
        analyzeContent.innerHTML = DOMPurify.sanitize(html);
    } catch (error) {
        analyzeContent.innerHTML = `<em>分析出错：${error.message}</em>`;
    } finally {
        btnAnalyze.disabled = false;
        btnAnalyze.textContent = '🤖 AI 分析';
    }
});

// ===== 页面初始化 =====
window.onload = () => {
    userInput.focus();
};
