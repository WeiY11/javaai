const chatMessages = document.getElementById('chatMessages');
const userInput = document.getElementById('userInput');
const sendBtn = document.getElementById('sendBtn');
const modelSelect = document.getElementById('modelSelect');

// 生成一个简单的随机字符串作为当前对话的 sessionId
const sessionId = 'session-' + Math.random().toString(36).substring(2, 9);

// 配置 marked.js 的渲染选项，结合 highlight.js
marked.setOptions({
    highlight: function(code, lang) {
        const language = hljs.getLanguage(lang) ? lang : 'plaintext';
        return hljs.highlight(code, { language }).value;
    },
    langPrefix: 'hljs language-', // highlight.js css expects a top-level 'hljs' class.
    breaks: true // 允许 Markdown 中的回车直接换行
});

// 自动滚动到最新消息
function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// 添加消息到界面
function appendMessage(content, isUser) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${isUser ? 'user-message' : 'ai-message'}`;
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content markdown-body';
    
    // 如果是用户消息，直接作为纯文本添加（防止用户输入导致XSS）
    if (isUser) {
        contentDiv.textContent = content;
    }
    // AI消息初始为空，后续通过流式更新渲染
    
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
    
    return contentDiv;
}

// 处理发送事件
async function sendMessage() {
    const message = userInput.value.trim();
    if (!message) return;
    
    const selectedModel = modelSelect.value;
    
    // 清空输入框并禁用相关交互
    userInput.value = '';
    sendBtn.disabled = true;
    modelSelect.disabled = true;
    
    // 显示用户消息
    appendMessage(message, true);
    
    // 准备显示 AI 消息
    const aiContentDiv = appendMessage('', false);
    aiContentDiv.innerHTML = '<span class="cursor-blink"></span>';
    
    try {
        // 使用 Fetch API 进行流式请求，带上 provider 和 sessionId 参数
        const url = `/api/chat/stream?message=${encodeURIComponent(message)}&provider=${encodeURIComponent(selectedModel)}&sessionId=${encodeURIComponent(sessionId)}`;
        const response = await fetch(url, {
            method: 'GET',
            headers: {
                'Accept': 'text/event-stream'
            }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let fullResponse = '';
        let buffer = '';
        
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            
            buffer += decoder.decode(value, { stream: true });
            
            // 按 SSE 规范，事件以双换行分隔
            const events = buffer.split(/\r?\n\r?\n/);
            // 最后一个可能是不完整的事件，放回 buffer
            buffer = events.pop() || '';
            
            for (const event of events) {
                const lines = event.split(/\r?\n/);
                let eventData = [];
                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        let dataText = line.substring(5);
                        // SSE 规范：如果数据以一个空格开头，去掉它
                        if (dataText.startsWith(' ')) {
                            dataText = dataText.substring(1);
                        }
                        eventData.push(dataText);
                    }
                }
                if (eventData.length > 0) {
                    // 同一个事件中的多行 data 需要用换行符连接
                    fullResponse += eventData.join('\n');
                    
                    // Markdown 渲染核心逻辑
                    let htmlContent = marked.parse(fullResponse);
                    htmlContent = DOMPurify.sanitize(htmlContent);
                    
                    // 更新界面，保留闪烁光标
                    aiContentDiv.innerHTML = htmlContent + '<span class="cursor-blink"></span>';
                    scrollToBottom();
                }
            }
        }
        
        // 传输完成后移除光标
        let htmlContent = marked.parse(fullResponse);
        htmlContent = DOMPurify.sanitize(htmlContent);
        aiContentDiv.innerHTML = htmlContent;
        
    } catch (error) {
        console.error('Error during streaming:', error);
        aiContentDiv.innerHTML = `<em>哎呀，出错了：${error.message}。请检查后端服务日志。</em>`;
    } finally {
        // 恢复按钮和交互状态
        sendBtn.disabled = false;
        modelSelect.disabled = false;
        userInput.focus();
    }
}

// 绑定回车键发送
userInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        sendMessage();
    }
});

// 绑定点击发送
sendBtn.addEventListener('click', sendMessage);

// 页面加载完成后输入框获取焦点
window.onload = () => {
    userInput.focus();
};
