const chatMessages = document.getElementById('chatMessages');
const userInput = document.getElementById('userInput');
const sendBtn = document.getElementById('sendBtn');

// 自动滚动到最新消息
function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// 添加消息到界面
function appendMessage(content, isUser) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${isUser ? 'user-message' : 'ai-message'}`;
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = content;
    
    messageDiv.appendChild(contentDiv);
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
    
    return contentDiv;
}

// 处理发送事件
async function sendMessage() {
    const message = userInput.value.trim();
    if (!message) return;
    
    // 清空输入框并禁用按钮
    userInput.value = '';
    sendBtn.disabled = true;
    
    // 显示用户消息
    appendMessage(message, true);
    
    // 准备显示 AI 消息（包含光标）
    const aiContentDiv = appendMessage('', false);
    aiContentDiv.innerHTML = '<span class="cursor-blink"></span>';
    
    try {
        // 使用 Fetch API 进行流式请求
        const response = await fetch(`/api/chat/stream?message=${encodeURIComponent(message)}`, {
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
        
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            
            // 解码二进制块为字符串
            const chunk = decoder.decode(value, { stream: true });
            
            // Server-Sent Events 格式通常是以 "data: xxx\n\n" 发送的
            // 这里做一个简单的处理，提取数据
            const lines = chunk.split('\n');
            for (const line of lines) {
                if (line.startsWith('data:')) {
                    let dataText = line.substring(5).trim();
                    // 处理可能的 JSON 包裹情况
                    // 注意：Spring AI stream 默认只输出字符串文本，所以我们直接累加
                    if (dataText) {
                        fullResponse += dataText;
                        // 更新 UI，保持光标在最后
                        aiContentDiv.innerHTML = fullResponse + '<span class="cursor-blink"></span>';
                        scrollToBottom();
                    }
                } else if (line.trim() && !line.startsWith('event:') && !line.startsWith('id:') && !line.startsWith(':')) {
                    // 如果 Spring MVC 没有严格按照 SSE 'data:' 前缀发送，直接作为文本接收
                    fullResponse += line;
                    aiContentDiv.innerHTML = fullResponse + '<span class="cursor-blink"></span>';
                    scrollToBottom();
                }
            }
        }
        
        // 传输完成后，移除光标
        aiContentDiv.innerHTML = fullResponse;
        
    } catch (error) {
        console.error('Error during streaming:', error);
        aiContentDiv.innerHTML = `<em>哎呀，出错了：${error.message}。请检查后端服务是否启动，或者 API Key 是否配置正确。</em>`;
    } finally {
        // 恢复按钮状态
        sendBtn.disabled = false;
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
