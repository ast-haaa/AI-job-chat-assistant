/**
 * HireMate AI - Autonomous Career Agent Frontend Application
 */
document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements
    const chatForm = document.getElementById('chatForm');
    const chatInput = document.getElementById('chatInput');
    const sendBtn = document.getElementById('sendBtn');
    const attachBtn = document.getElementById('attachBtn');
    const resumeFileInput = document.getElementById('resumeFileInput');
    const messagesContainer = document.getElementById('messagesContainer');
    const welcomeHero = document.getElementById('welcomeHero');
    const jobCountBadge = document.getElementById('jobCountBadge');
    const serverStatusText = document.getElementById('serverStatusText');
    const newChatBtn = document.getElementById('newChatBtn');

    // Sidebar elements
    const sidebar = document.getElementById('sidebar');
    const openSidebarBtn = document.getElementById('openSidebarBtn');
    const closeSidebarBtn = document.getElementById('closeSidebarBtn');
    const conversationHistoryContainer = document.getElementById('conversationHistory');
    const resumeStatusDot = document.getElementById('resumeStatusDot');
    const resumeStatusLabel = document.getElementById('resumeStatusLabel');
    const profileHintText = document.getElementById('profileHintText');
    const profileDetailsPreview = document.getElementById('profileDetailsPreview');
    const previewCandidateName = document.getElementById('previewCandidateName');
    const previewTargetRole = document.getElementById('previewTargetRole');
    const previewSkillsCount = document.getElementById('previewSkillsCount');

    // Delete Modal Elements
    const deleteModalOverlay = document.getElementById('deleteModalOverlay');
    const deleteModalDesc = document.getElementById('deleteModalDesc');
    const cancelDeleteBtn = document.getElementById('cancelDeleteBtn');
    const confirmDeleteBtn = document.getElementById('confirmDeleteBtn');
    let pendingDeleteConvId = null;

    // Attachment Preview
    const attachmentPreview = document.getElementById('attachmentPreview');
    const attachmentName = document.getElementById('attachmentName');
    const attachmentSize = document.getElementById('attachmentSize');
    const removeAttachmentBtn = document.getElementById('removeAttachmentBtn');

    // Drag and Drop
    const dragDropOverlay = document.getElementById('dragDropOverlay');

    // State
    let selectedFile = null;
    let isWaitingResponse = false;

    // Conversation History Management
    const STORAGE_KEY_CONVERSATIONS = 'hiremate_conversations';
    const STORAGE_KEY_ACTIVE_ID = 'hiremate_active_conv_id';

    let conversations = loadConversations();
    let currentConversationId = getInitialConversationId();

    // Initial Application Setup
    initApp();

    async function initApp() {
        // Render conversations and active messages from persistence
        renderSidebarConversations();
        renderActiveConversationMessages();

        try {
            const healthRes = await fetch('/api/health');
            if (healthRes.ok) {
                const health = await healthRes.json();
                serverStatusText.textContent = `Online: ${health.service} (${health.status})`;
            }
        } catch (e) {
            serverStatusText.textContent = 'Server connecting...';
        }

        try {
            const jobsRes = await fetch('/api/jobs/count');
            if (jobsRes.ok) {
                const data = await jobsRes.json();
                jobCountBadge.textContent = `${data.count ? data.count.toLocaleString() : '0'} verified jobs`;
            }
        } catch (e) {
            jobCountBadge.textContent = 'Job Database Ready';
        }

        // Check if session already has resume loaded
        try {
            const profileRes = await fetch('/api/resume/current');
            if (profileRes.ok) {
                const profile = await profileRes.json();
                updateSidebarProfile(profile);
            }
        } catch (ignored) {}
    }

    // Conversation Persistence Helpers
    function loadConversations() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY_CONVERSATIONS);
            if (raw) {
                const parsed = JSON.parse(raw);
                if (Array.isArray(parsed)) {
                    // Only keep actual user chat conversations; exclude Tracked Applications
                    return parsed.filter(c => 
                        c && 
                        c.messages && 
                        c.messages.length > 0 && 
                        c.title !== 'Tracked Applications' &&
                        !c.title.toLowerCase().startsWith('tracked application')
                    );
                }
            }
        } catch (e) {
            console.error('Failed to load conversations from localStorage:', e);
        }
        return [];
    }

    function saveConversations() {
        try {
            // Only persist conversations with actual messages; exclude Tracked Applications
            const valid = conversations.filter(c => 
                c && 
                c.messages && 
                c.messages.length > 0 && 
                c.title !== 'Tracked Applications' &&
                !c.title.toLowerCase().startsWith('tracked application')
            );
            localStorage.setItem(STORAGE_KEY_CONVERSATIONS, JSON.stringify(valid));
        } catch (e) {
            console.error('Failed to save conversations to localStorage:', e);
        }
    }

    function generateConversationId() {
        return 'conv_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
    }

    function getInitialConversationId() {
        const storedActiveId = localStorage.getItem(STORAGE_KEY_ACTIVE_ID);
        if (storedActiveId && conversations.some(c => c.id === storedActiveId)) {
            return storedActiveId;
        }
        if (conversations.length > 0) {
            return conversations[0].id;
        }
        return generateConversationId();
    }

    function getCurrentConversation() {
        return conversations.find(c => c.id === currentConversationId) || null;
    }

    function generateMeaningfulTitle(text) {
        if (!text) return 'New Conversation';

        let clean = text.replace(/\[Attached:[^\]]+\]/g, '').trim();
        clean = clean.replace(/[*_#`]/g, '').trim();

        if (clean.toLowerCase().startsWith('uploaded resume:')) {
            const match = clean.match(/uploaded resume:\s*([^\s(]+)/i);
            return match ? `Resume: ${match[1]}` : 'Resume Profile';
        }

        const lower = clean.toLowerCase();

        if (lower.includes('applied') || lower.includes('applications')) {
            return 'My Application Status';
        }
        if (lower.includes('match') && lower.includes('resume')) {
            return 'Resume Job Matches';
        }
        if (lower.startsWith('apply to job') || lower.startsWith('apply job')) {
            const match = lower.match(/apply\s+(?:to\s+)?job\s*#?(\d+)/);
            return match ? `Job Application #${match[1]}` : 'Job Application';
        }

        // Clean conversational greetings & filler prefixes
        clean = clean.replace(/^(can you please |could you please |can you |could you |please |show me |find me |give me |search for |search |show |find )/i, '').trim();
        if (!clean) return 'Job Discussion';

        clean = clean.charAt(0).toUpperCase() + clean.slice(1);
        if (clean.length > 28) {
            const cut = clean.substring(0, 28);
            const lastSpace = cut.lastIndexOf(' ');
            return (lastSpace > 14 ? cut.substring(0, lastSpace) : cut) + '...';
        }
        return clean;
    }

    function renderSidebarConversations() {
        if (!conversationHistoryContainer) return;
        conversationHistoryContainer.innerHTML = '';

        // Only actual user-created chat conversations appear under Recent Conversations
        // "Tracked Applications" belongs strictly under the Activity section
        const userConversations = conversations.filter(c => 
            c && 
            c.messages && 
            c.messages.length > 0 && 
            c.title !== 'Tracked Applications' &&
            !c.title.toLowerCase().startsWith('tracked application')
        );

        if (userConversations.length === 0) {
            const emptyHint = document.createElement('div');
            emptyHint.className = 'history-empty-hint';
            emptyHint.textContent = 'No recent conversations';
            conversationHistoryContainer.appendChild(emptyHint);
            return;
        }

        userConversations.forEach(c => {
            const item = document.createElement('div');
            item.className = 'history-item' + (c.id === currentConversationId ? ' active' : '');
            item.setAttribute('data-conv-id', c.id);

            const content = document.createElement('div');
            content.className = 'history-item-content';
            content.innerHTML = `
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                </svg>
                <span class="history-item-title" title="${escapeHtml(c.title)}">${escapeHtml(c.title)}</span>
            `;

            const delBtn = document.createElement('span');
            delBtn.className = 'btn-delete-conv';
            delBtn.setAttribute('role', 'button');
            delBtn.setAttribute('tabindex', '0');
            delBtn.title = 'Delete conversation';
            delBtn.innerHTML = `
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    <line x1="10" y1="11" x2="10" y2="17"></line>
                    <line x1="14" y1="11" x2="14" y2="17"></line>
                </svg>
            `;
            delBtn.onclick = (e) => {
                e.stopPropagation();
                openDeleteModal(c.id, e);
            };

            item.appendChild(content);
            item.appendChild(delBtn);
            item.onclick = () => switchConversation(c.id);

            conversationHistoryContainer.appendChild(item);
        });
    }

    function switchConversation(convId) {
        currentConversationId = convId;
        localStorage.setItem(STORAGE_KEY_ACTIVE_ID, convId);
        renderSidebarConversations();
        renderActiveConversationMessages();
    }

    function renderActiveConversationMessages() {
        messagesContainer.innerHTML = '';
        const conv = getCurrentConversation();

        if (!conv || !conv.messages || conv.messages.length === 0) {
            if (welcomeHero) {
                welcomeHero.classList.remove('hidden');
                messagesContainer.appendChild(welcomeHero);
            }
            return;
        }

        if (welcomeHero) {
            welcomeHero.classList.add('hidden');
        }

        conv.messages.forEach(msg => {
            if (msg.role === 'user') {
                renderUserMessageToDom(msg.text);
            } else if (msg.role === 'assistant') {
                if (msg.data) {
                    renderAssistantResponseToDom(msg.data);
                } else {
                    renderPlainAssistantMessageToDom(msg.text);
                }
            }
        });

        scrollToBottom();
    }

    function startNewConversation() {
        const current = getCurrentConversation();
        // If current conversation already has 0 messages, focus input and stay in it
        if (!current || (current.messages && current.messages.length === 0)) {
            renderSidebarConversations();
            renderActiveConversationMessages();
            chatInput.focus();
            return;
        }

        currentConversationId = generateConversationId();
        localStorage.removeItem(STORAGE_KEY_ACTIVE_ID);
        renderSidebarConversations();
        renderActiveConversationMessages();
        chatInput.focus();
    }

    function openDeleteModal(convId, e) {
        if (e) e.stopPropagation();
        const conv = conversations.find(c => c.id === convId);
        if (!conv) return;

        pendingDeleteConvId = convId;
        if (deleteModalDesc) {
            deleteModalDesc.innerHTML = `Are you sure you want to delete <strong>"${escapeHtml(conv.title)}"</strong>? This action cannot be undone.`;
        }
        if (deleteModalOverlay) {
            deleteModalOverlay.classList.remove('hidden');
        }
    }

    function closeDeleteModal() {
        pendingDeleteConvId = null;
        if (deleteModalOverlay) {
            deleteModalOverlay.classList.add('hidden');
        }
    }

    function executeDeleteConversation(convId) {
        conversations = conversations.filter(c => c.id !== convId);
        saveConversations();

        // Notify backend to clean up context memory
        fetch(`/api/chat/conversations/${encodeURIComponent(convId)}`, { method: 'DELETE' }).catch(() => {});

        if (convId === currentConversationId) {
            const remaining = conversations.filter(c => c.messages && c.messages.length > 0 && c.title !== 'Tracked Applications');
            if (remaining.length > 0) {
                switchConversation(remaining[0].id);
            } else {
                startNewConversation();
            }
        } else {
            renderSidebarConversations();
        }
    }

    // Modal listeners
    if (cancelDeleteBtn) {
        cancelDeleteBtn.addEventListener('click', closeDeleteModal);
    }
    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener('click', () => {
            if (pendingDeleteConvId) {
                const idToDelete = pendingDeleteConvId;
                closeDeleteModal();
                executeDeleteConversation(idToDelete);
            }
        });
    }
    if (deleteModalOverlay) {
        deleteModalOverlay.addEventListener('click', (e) => {
            if (e.target === deleteModalOverlay) {
                closeDeleteModal();
            }
        });
    }
    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && deleteModalOverlay && !deleteModalOverlay.classList.contains('hidden')) {
            closeDeleteModal();
        }
    });



    // Mobile Sidebar Toggle
    if (openSidebarBtn) {
        openSidebarBtn.addEventListener('click', () => sidebar.classList.add('open'));
    }
    if (closeSidebarBtn) {
        closeSidebarBtn.addEventListener('click', () => sidebar.classList.remove('open'));
    }

    // New Chat Button
    newChatBtn.addEventListener('click', () => {
        startNewConversation();
    });

    // Input Auto-resize & Keybindings
    chatInput.addEventListener('input', () => {
        chatInput.style.height = 'auto';
        chatInput.style.height = Math.min(chatInput.scrollHeight, 140) + 'px';
    });

    chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            if (!isWaitingResponse) {
                chatForm.dispatchEvent(new Event('submit'));
            }
        }
    });

    // File Attachment & Direct Resume Upload Handling
    attachBtn.addEventListener('click', () => {
        resumeFileInput.click();
    });

    resumeFileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            uploadResumeFile(file);
        }
    });

    removeAttachmentBtn.addEventListener('click', () => {
        clearSelectedFile();
    });

    async function uploadResumeFile(file) {
        const ext = file.name.split('.').pop().toLowerCase();
        if (ext !== 'pdf' && ext !== 'docx') {
            addMessage('assistant', `⚠️ **Invalid file format**: \`${file.name}\`. Only **PDF** (.pdf) and **Word** (.docx) files are supported.`);
            resumeFileInput.value = '';
            return;
        }

        if (file.size > 5 * 1024 * 1024) {
            addMessage('assistant', `⚠️ **File too large**: \`${file.name}\` (${formatBytes(file.size)}). Maximum allowed resume upload size is **5 MB**.`);
            resumeFileInput.value = '';
            return;
        }

        // Hide welcome hero on upload
        if (welcomeHero) {
            welcomeHero.classList.add('hidden');
        }

        const userDisplayText = `📄 Uploaded resume: **${escapeHtml(file.name)}** (${formatBytes(file.size)})`;
        addMessage('user', userDisplayText);

        // Show upload & parsing loading state
        setLoading(true);

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('conversationId', currentConversationId);

            const res = await fetch('/api/resume/parse', {
                method: 'POST',
                body: formData
            });

            if (!res.ok) {
                const err = await res.json().catch(() => ({ error: 'Upload failed' }));
                throw new Error(err.error || err.message || 'Server rejected file upload');
            }

            const profile = await res.json();

            // Render Assistant Response with Profile Card
            renderAssistantResumeResponse(profile);

            // Update Sidebar Profile Status
            updateSidebarProfile(profile);

            // Auto-update conversation title with candidate or file name
            let conv = conversations.find(c => c.id === currentConversationId);
            if (conv) {
                const displayName = profile.name && !profile.name.toLowerCase().includes('candidate')
                    ? profile.name.trim()
                    : (profile.fileName || file.name);
                conv.title = `Resume: ${displayName}`;
                saveConversations();
                renderSidebarConversations();
            }

        } catch (error) {
            console.error('Resume parsing error:', error);
            addMessage('assistant', `⚠️ **Resume Parsing Error**: ${error.message || 'Could not process document contents. The document may be scanned, encrypted, or corrupted.'}`);
        } finally {
            setLoading(false);
            resumeFileInput.value = '';
        }
    }

    function clearSelectedFile() {
        selectedFile = null;
        resumeFileInput.value = '';
        attachmentPreview.classList.add('hidden');
    }

    // Drag and Drop Support
    window.addEventListener('dragover', (e) => {
        e.preventDefault();
        dragDropOverlay.classList.add('active');
    });

    window.addEventListener('dragleave', (e) => {
        if (e.relatedTarget === null || e.clientX === 0 || e.clientY === 0) {
            dragDropOverlay.classList.remove('active');
        }
    });

    window.addEventListener('drop', (e) => {
        e.preventDefault();
        dragDropOverlay.classList.remove('active');
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            uploadResumeFile(e.dataTransfer.files[0]);
        }
    });

    // Form Submission
    chatForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const text = chatInput.value.trim();
        const fileToUpload = selectedFile;

        if (!text && !fileToUpload) return;
        if (isWaitingResponse) return;

        // Hide welcome hero on first message
        if (welcomeHero) {
            welcomeHero.classList.add('hidden');
        }

        // Display user message in UI
        let userDisplayText = text;
        if (fileToUpload) {
            userDisplayText = `[Attached: ${fileToUpload.name}]\n` + (text || 'Upload and parse resume');
        }
        addMessage('user', userDisplayText);

        // Reset input fields
        chatInput.value = '';
        chatInput.style.height = 'auto';
        clearSelectedFile();

        // Show typing indicator
        setLoading(true);

        try {
            let responseData;

            if (fileToUpload) {
                // Submit via multipart/form-data to /api/chat with conversationId
                const formData = new FormData();
                formData.append('file', fileToUpload);
                formData.append('conversationId', currentConversationId);
                if (text) {
                    formData.append('message', text);
                }

                const res = await fetch('/api/chat', {
                    method: 'POST',
                    body: formData
                });

                if (!res.ok) {
                    const err = await res.json().catch(() => ({ error: 'Upload failed' }));
                    throw new Error(err.error || err.message || 'Server error processing file');
                }
                responseData = await res.json();

            } else {
                // Submit standard JSON to /api/chat with conversationId
                const res = await fetch('/api/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: text, conversationId: currentConversationId })
                });

                if (!res.ok) {
                    const err = await res.json().catch(() => ({ error: 'Request failed' }));
                    throw new Error(err.error || err.message || 'Server error processing message');
                }
                responseData = await res.json();
            }

            // Update UI and save response
            renderAssistantResponse(responseData);

            // Update sidebar if profile was returned
            if (responseData.resumeProfile) {
                updateSidebarProfile(responseData.resumeProfile);
            }

        } catch (error) {
            console.error('Chat error:', error);
            addMessage('assistant', `⚠️ **Error**: ${error.message || 'Failed to communicate with HireMate backend.'}`);
        } finally {
            setLoading(false);
        }
    });

    // Render Assistant Response & Persist
    function renderAssistantResponse(data) {
        renderAssistantResponseToDom(data);

        // Persist message in conversation
        let conv = conversations.find(c => c.id === currentConversationId);
        if (!conv) {
            conv = {
                id: currentConversationId,
                title: 'Job Recommendations',
                createdAt: Date.now(),
                updatedAt: Date.now(),
                messages: []
            };
            conversations.unshift(conv);
            localStorage.setItem(STORAGE_KEY_ACTIVE_ID, currentConversationId);
        }

        conv.messages.push({
            role: 'assistant',
            text: data.message || '',
            data: data,
            timestamp: Date.now()
        });
        conv.updatedAt = Date.now();
        saveConversations();
        renderSidebarConversations();
    }

    function renderAssistantResponseToDom(data) {
        const messageRow = document.createElement('div');
        messageRow.className = 'message-row assistant';

        const avatar = document.createElement('div');
        avatar.className = 'avatar avatar-assistant';
        avatar.textContent = 'HM';

        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';

        // Markdown text
        if (data.message) {
            bubble.innerHTML = formatMarkdown(data.message);
        }

        // Render Profile Card if parsed
        if (data.resumeProfile) {
            const profileCard = createProfileCard(data.resumeProfile);
            bubble.appendChild(profileCard);
        }

        // Render Job Recommendation Cards
        if (data.jobMatches && data.jobMatches.length > 0) {
            const jobList = createJobCardsList(data.jobMatches);
            bubble.appendChild(jobList);
        }

        // Render Confirmation Box if confirmation is required
        if (data.confirmationRequired && data.pendingConfirmation) {
            const confirmBox = createConfirmationBox(data.pendingConfirmation);
            bubble.appendChild(confirmBox);
        }

        messageRow.appendChild(avatar);
        messageRow.appendChild(bubble);
        messagesContainer.appendChild(messageRow);
        scrollToBottom();
    }

    function renderAssistantResumeResponse(profile) {
        const messageText = `I have successfully parsed and activated your resume **${escapeHtml(profile.fileName || 'document')}** in this session.\n\nBelow is the structured profile extracted without inventing missing data. You can now ask:\n• *"Find jobs matching my resume"*\n• *"Show remote Java internships"*\n• *"Show jobs requiring React"*`;

        renderAssistantResumeResponseToDom(profile, messageText);

        let conv = conversations.find(c => c.id === currentConversationId);
        if (!conv) {
            conv = {
                id: currentConversationId,
                title: 'Resume Discussion',
                createdAt: Date.now(),
                updatedAt: Date.now(),
                messages: []
            };
            conversations.unshift(conv);
            localStorage.setItem(STORAGE_KEY_ACTIVE_ID, currentConversationId);
        }

        conv.messages.push({
            role: 'assistant',
            text: messageText,
            data: { resumeProfile: profile, message: messageText },
            timestamp: Date.now()
        });
        conv.updatedAt = Date.now();
        saveConversations();
        renderSidebarConversations();
    }

    function renderAssistantResumeResponseToDom(profile, messageText) {
        const messageRow = document.createElement('div');
        messageRow.className = 'message-row assistant';

        const avatar = document.createElement('div');
        avatar.className = 'avatar avatar-assistant';
        avatar.textContent = 'HM';

        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        bubble.innerHTML = formatMarkdown(messageText);

        const profileCard = createProfileCard(profile);
        bubble.appendChild(profileCard);

        messageRow.appendChild(avatar);
        messageRow.appendChild(bubble);
        messagesContainer.appendChild(messageRow);
        scrollToBottom();
    }

    function renderPlainAssistantMessageToDom(text) {
        const row = document.createElement('div');
        row.className = 'message-row assistant';

        const avatar = document.createElement('div');
        avatar.className = 'avatar avatar-assistant';
        avatar.textContent = 'HM';

        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        bubble.innerHTML = formatMarkdown(text);

        row.appendChild(avatar);
        row.appendChild(bubble);
        messagesContainer.appendChild(row);
        scrollToBottom();
    }

    function renderUserMessageToDom(text) {
        const row = document.createElement('div');
        row.className = 'message-row user';

        const avatar = document.createElement('div');
        avatar.className = 'avatar avatar-user';
        avatar.textContent = 'You';

        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        bubble.innerHTML = formatMarkdown(text);

        row.appendChild(avatar);
        row.appendChild(bubble);
        messagesContainer.appendChild(row);
        scrollToBottom();
    }

    function addMessage(role, text) {
        if (role === 'user') {
            renderUserMessageToDom(text);
        } else {
            renderPlainAssistantMessageToDom(text);
        }

        let conv = conversations.find(c => c.id === currentConversationId);
        if (!conv) {
            conv = {
                id: currentConversationId,
                title: role === 'user' ? generateMeaningfulTitle(text) : 'New Conversation',
                createdAt: Date.now(),
                updatedAt: Date.now(),
                messages: []
            };
            conversations.unshift(conv);
            localStorage.setItem(STORAGE_KEY_ACTIVE_ID, currentConversationId);
        }

        conv.messages.push({
            role: role,
            text: text,
            timestamp: Date.now()
        });

        // If first user message in conversation, assign meaningful title
        if (role === 'user' && (conv.title === 'New Conversation' || !conv.title)) {
            conv.title = generateMeaningfulTitle(text);
        }

        conv.updatedAt = Date.now();
        saveConversations();
        renderSidebarConversations();
    }

    // Component Builders
    function createProfileCard(profile) {
        const card = document.createElement('div');
        card.className = 'profile-card';

        let skillsHtml = '';
        if (profile.skills && profile.skills.length > 0) {
            skillsHtml = profile.skills.map(s => `<span class="skill-tag">${escapeHtml(s)}</span>`).join('');
        }

        let experienceHtml = '';
        if (profile.experience && profile.experience.length > 0) {
            experienceHtml = `
                <div style="font-size: 0.78rem; font-weight: 700; color: #334155; margin-top: 10px;">Work Experience & Internships:</div>
                <ul style="font-size: 0.8rem; color: var(--text-secondary); margin: 4px 0 6px 18px;">
                    ${profile.experience.slice(0, 3).map(e => `<li>${escapeHtml(e)}</li>`).join('')}
                </ul>
            `;
        }

        let educationHtml = '';
        if (profile.education && profile.education.length > 0) {
            educationHtml = `
                <div style="font-size: 0.78rem; font-weight: 700; color: #334155; margin-top: 6px;">Education:</div>
                <ul style="font-size: 0.8rem; color: var(--text-secondary); margin: 4px 0 6px 18px;">
                    ${profile.education.slice(0, 2).map(ed => `<li>${escapeHtml(ed)}</li>`).join('')}
                </ul>
            `;
        }

        let projectsHtml = '';
        if (profile.projects && profile.projects.length > 0) {
            projectsHtml = `
                <div style="font-size: 0.78rem; font-weight: 700; color: #334155; margin-top: 6px;">Projects:</div>
                <ul style="font-size: 0.8rem; color: var(--text-secondary); margin: 4px 0 6px 18px;">
                    ${profile.projects.slice(0, 3).map(p => `<li>${escapeHtml(p)}</li>`).join('')}
                </ul>
            `;
        }

        card.innerHTML = `
            <div class="profile-card-header">
                <div>
                    <div class="profile-card-name">${escapeHtml(profile.name || 'Candidate Profile')}</div>
                    <div class="profile-card-role">${escapeHtml(profile.targetDesignation || 'Software Engineering Candidate')}</div>
                </div>
                <span class="skill-tag matching">Parsed & Active</span>
            </div>
            <div class="profile-contact-row">
                ${profile.email ? `<span>✉️ ${escapeHtml(profile.email)}</span>` : ''}
                ${profile.phone ? `<span>📞 ${escapeHtml(profile.phone)}</span>` : ''}
                ${profile.fileName ? `<span>📁 ${escapeHtml(profile.fileName)}</span>` : ''}
            </div>
            ${skillsHtml ? `
                <div style="font-size: 0.78rem; color: var(--text-muted); margin-bottom: 4px;">Detected Skills (${profile.skills.length}):</div>
                <div class="profile-skills-wrap">${skillsHtml}</div>
            ` : ''}
            ${experienceHtml}
            ${educationHtml}
            ${projectsHtml}
        `;
        return card;
    }

    function createJobCardsList(matches) {
        const wrap = document.createElement('div');
        wrap.className = 'job-cards-list';

        matches.forEach(item => {
            const job = item.job;
            const card = document.createElement('div');
            card.className = 'job-card';

            const scoreClass = item.matchPercentage >= 70 ? 'match-high' : (item.matchPercentage >= 50 ? 'match-mid' : 'match-low');

            let matchingSkillsHtml = '';
            if (item.matchingSkills && item.matchingSkills.length > 0) {
                matchingSkillsHtml = item.matchingSkills.slice(0, 5).map(s => `<span class="skill-tag matching">${escapeHtml(s)}</span>`).join('');
            }

            card.innerHTML = `
                <div class="job-card-top">
                    <div>
                        <span class="job-index-badge">#${item.displayIndex}</span>
                        <span class="job-card-title">${escapeHtml(job.title)}</span>
                        <div class="job-card-company">${escapeHtml(job.company)}</div>
                    </div>
                    <div class="match-score-badge ${scoreClass}">
                        <span>${item.matchPercentage}% Match</span>
                    </div>
                </div>
                <div class="job-meta-row">
                    <span class="job-meta-item">📍 ${escapeHtml(job.location || 'Flexible')}</span>
                    ${job.remote ? `<span class="remote-badge">Remote</span>` : ''}
                    <span class="job-meta-item">⏳ ${escapeHtml(job.experienceRequired || 'Relevant experience')}</span>
                    <span class="job-meta-item">💼 ${escapeHtml(job.employmentType || 'Full Time')}</span>
                </div>
                ${item.explanation ? `<div class="job-explanation">${escapeHtml(item.explanation)}</div>` : ''}
                ${item.eligibilityMismatch ? `<div class="job-explanation mismatch-alert">⚠️ ${escapeHtml(item.eligibilityMismatch)}</div>` : ''}
                ${matchingSkillsHtml ? `
                    <div class="profile-skills-wrap" style="margin-top: 6px;">
                        ${matchingSkillsHtml}
                    </div>
                ` : ''}
                <div class="job-card-actions">
                    <button class="btn-apply-action" onclick="window.sendQuickPrompt('Apply to job ${item.displayIndex}')">
                        Track Application
                    </button>
                    ${job.sourceLink ? `
                        <a href="${escapeHtml(job.sourceLink)}" target="_blank" rel="noopener noreferrer" class="btn-source-link">
                            View Original Post ↗
                        </a>
                    ` : ''}
                </div>
            `;
            wrap.appendChild(card);
        });

        return wrap;
    }

    function createConfirmationBox(pending) {
        const box = document.createElement('div');
        box.className = 'confirmation-box';
        box.innerHTML = `
            <div style="font-weight: 600; color: #92400e; margin-bottom: 4px;">Confirm Application Tracking</div>
            <div style="font-size: 0.85rem; color: var(--text-secondary);">
                Track application to <strong>${escapeHtml(pending.jobTitle)}</strong> at <strong>${escapeHtml(pending.company)}</strong>? (Note: External submission requires visiting the employer's direct job link).
            </div>
            <div class="confirmation-actions">
                <button class="btn-confirm" onclick="window.sendQuickPrompt('Yes, apply')">✓ Yes, Track Application</button>
                <button class="btn-cancel" onclick="window.sendQuickPrompt('Cancel')">✕ Cancel</button>
            </div>
        `;
        return box;
    }

    // Sidebar profile update
    function updateSidebarProfile(profile) {
        if (!profile) return;
        resumeStatusDot.className = 'status-dot dot-active';
        resumeStatusLabel.textContent = 'Resume Loaded';
        profileHintText.textContent = profile.fileName || 'Parsed successfully';
        profileDetailsPreview.classList.remove('hidden');

        previewCandidateName.textContent = profile.name || 'Candidate';
        previewTargetRole.textContent = profile.targetDesignation || 'Software Engineer';
        previewSkillsCount.textContent = `${(profile.skills || []).length} skills extracted`;
    }

    // Helper: Typing indicator
    let currentLoadingIndicator = null;
    function setLoading(loading) {
        isWaitingResponse = loading;
        sendBtn.disabled = loading;

        if (loading) {
            const row = document.createElement('div');
            row.className = 'message-row assistant typing-row';
            row.id = 'activeTypingRow';

            const avatar = document.createElement('div');
            avatar.className = 'avatar avatar-assistant';
            avatar.textContent = 'HM';

            const bubble = document.createElement('div');
            bubble.className = 'message-bubble';
            bubble.innerHTML = `
                <div class="typing-indicator">
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                </div>
            `;

            row.appendChild(avatar);
            row.appendChild(bubble);
            messagesContainer.appendChild(row);
            currentLoadingIndicator = row;
            scrollToBottom();
        } else {
            if (currentLoadingIndicator) {
                currentLoadingIndicator.remove();
                currentLoadingIndicator = null;
            }
        }
    }

    function scrollToBottom() {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatMarkdown(text) {
        if (!text) return '';
        let escaped = escapeHtml(text);

        // Bold
        escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
        // Italics
        escaped = escaped.replace(/\*(.*?)\*/g, '<em>$1</em>');
        // Inline code
        escaped = escaped.replace(/`([^`]+)`/g, '<code style="background: #f1f5f9; color: #4338ca; border: 1px solid #e2e8f0; padding: 2px 6px; border-radius: 4px; font-family: var(--font-mono); font-size: 0.85em;">$1</code>');

        // Bullet points
        const lines = escaped.split('\n');
        let inList = false;
        let htmlLines = [];

        for (let line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith('• ') || trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
                if (!inList) {
                    htmlLines.push('<ul>');
                    inList = true;
                }
                htmlLines.push(`<li>${trimmed.substring(2)}</li>`);
            } else {
                if (inList) {
                    htmlLines.push('</ul>');
                    inList = false;
                }
                htmlLines.push(trimmed ? `<p>${line}</p>` : '<br>');
            }
        }
        if (inList) htmlLines.push('</ul>');

        return htmlLines.join('');
    }

    // Global helper exposed for prompt chips and buttons
    window.sendQuickPrompt = (promptText) => {
        chatInput.value = promptText;
        chatForm.dispatchEvent(new Event('submit'));
    };

    window.triggerResumeUpload = () => {
        resumeFileInput.click();
    };
});
