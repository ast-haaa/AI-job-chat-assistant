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
    const resumeStatusDot = document.getElementById('resumeStatusDot');
    const resumeStatusLabel = document.getElementById('resumeStatusLabel');
    const profileHintText = document.getElementById('profileHintText');
    const profileDetailsPreview = document.getElementById('profileDetailsPreview');
    const previewCandidateName = document.getElementById('previewCandidateName');
    const previewTargetRole = document.getElementById('previewTargetRole');
    const previewSkillsCount = document.getElementById('previewSkillsCount');

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

    // 1. Initial health and job count verification
    initApp();

    async function initApp() {
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

        // Check if session already has resume
        try {
            const profileRes = await fetch('/api/resume/current');
            if (profileRes.ok) {
                const profile = await profileRes.json();
                updateSidebarProfile(profile);
            }
        } catch (ignored) {}
    }

    // 2. Mobile Sidebar Toggle
    if (openSidebarBtn) {
        openSidebarBtn.addEventListener('click', () => sidebar.classList.add('open'));
    }
    if (closeSidebarBtn) {
        closeSidebarBtn.addEventListener('click', () => sidebar.classList.remove('open'));
    }

    // 3. New Chat / Reset
    newChatBtn.addEventListener('click', async () => {
        if (confirm('Start a fresh conversation? Current session history will be reset.')) {
            try {
                await fetch('/api/chat/reset', { method: 'POST' });
            } catch (e) {}
            location.reload();
        }
    });

    // 4. Input Auto-resize & Keybindings
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

    // 5. File Attachment & Direct Resume Upload Handling
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

        // Show user upload bubble
        addMessage('user', `📄 Uploaded resume: **${escapeHtml(file.name)}** (${formatBytes(file.size)})`);

        // Show upload & parsing loading state
        setLoading(true);

        try {
            const formData = new FormData();
            formData.append('file', file);

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

        } catch (error) {
            console.error('Resume parsing error:', error);
            addMessage('assistant', `⚠️ **Resume Parsing Error**: ${error.message || 'Could not process document contents. The document may be scanned, encrypted, or corrupted.'}`);
        } finally {
            setLoading(false);
            resumeFileInput.value = ''; // Allow uploading another resume anytime
        }
    }

    function clearSelectedFile() {
        selectedFile = null;
        resumeFileInput.value = '';
        attachmentPreview.classList.add('hidden');
    }

    // 6. Drag and Drop Support
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

    // 7. Form Submission
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
                // Submit via multipart/form-data to /api/resume/parse or /api/chat
                const formData = new FormData();
                formData.append('file', fileToUpload);
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
                // Submit standard JSON to /api/chat
                const res = await fetch('/api/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: text })
                });

                if (!res.ok) {
                    const err = await res.json().catch(() => ({ error: 'Request failed' }));
                    throw new Error(err.error || err.message || 'Server error processing message');
                }
                responseData = await res.json();
            }

            // Update UI with Assistant's Response
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

    // 8. Render Assistant Response
    function renderAssistantResponse(data) {
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

    // Render Assistant Response specifically for direct resume upload
    function renderAssistantResumeResponse(profile) {
        const messageRow = document.createElement('div');
        messageRow.className = 'message-row assistant';

        const avatar = document.createElement('div');
        avatar.className = 'avatar avatar-assistant';
        avatar.textContent = 'HM';

        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';

        bubble.innerHTML = `
            <p>I have successfully parsed and activated your resume <strong>${escapeHtml(profile.fileName || 'document')}</strong> in this session.</p>
            <p>Below is the structured profile extracted without inventing missing data. You can now ask:
            <br>• <em>"Find jobs matching my resume"</em>
            <br>• <em>"Show remote Java internships"</em>
            <br>• <em>"Show jobs requiring React"</em></p>
        `;

        const profileCard = createProfileCard(profile);
        bubble.appendChild(profileCard);

        messageRow.appendChild(avatar);
        messageRow.appendChild(bubble);
        messagesContainer.appendChild(messageRow);
        scrollToBottom();
    }

    // 9. Component Builders
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
                <div style="font-size: 0.78rem; font-weight: 600; color: #cbd5e1; margin-top: 10px;">Work Experience & Internships:</div>
                <ul style="font-size: 0.8rem; color: var(--text-secondary); margin: 4px 0 6px 18px;">
                    ${profile.experience.slice(0, 3).map(e => `<li>${escapeHtml(e)}</li>`).join('')}
                </ul>
            `;
        }

        let educationHtml = '';
        if (profile.education && profile.education.length > 0) {
            educationHtml = `
                <div style="font-size: 0.78rem; font-weight: 600; color: #cbd5e1; margin-top: 6px;">Education:</div>
                <ul style="font-size: 0.8rem; color: var(--text-secondary); margin: 4px 0 6px 18px;">
                    ${profile.education.slice(0, 2).map(ed => `<li>${escapeHtml(ed)}</li>`).join('')}
                </ul>
            `;
        }

        let projectsHtml = '';
        if (profile.projects && profile.projects.length > 0) {
            projectsHtml = `
                <div style="font-size: 0.78rem; font-weight: 600; color: #cbd5e1; margin-top: 6px;">Projects:</div>
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
                        Apply via HireMate
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
            <div style="font-weight: 600; color: #ffffff; margin-bottom: 4px;">Confirm Job Application</div>
            <div style="font-size: 0.85rem; color: var(--text-secondary);">
                Track application to <strong>${escapeHtml(pending.jobTitle)}</strong> at <strong>${escapeHtml(pending.company)}</strong>?
            </div>
            <div class="confirmation-actions">
                <button class="btn-confirm" onclick="window.sendQuickPrompt('Yes, apply')">✓ Yes, Apply</button>
                <button class="btn-cancel" onclick="window.sendQuickPrompt('Cancel')">✕ Cancel</button>
            </div>
        `;
        return box;
    }

    // 10. Sidebar profile update
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

    // 11. Helper: Add basic message
    function addMessage(role, text) {
        const row = document.createElement('div');
        row.className = `message-row ${role}`;

        const avatar = document.createElement('div');
        avatar.className = `avatar avatar-${role}`;
        avatar.textContent = role === 'user' ? 'You' : 'HM';

        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        bubble.innerHTML = formatMarkdown(text);

        row.appendChild(avatar);
        row.appendChild(bubble);
        messagesContainer.appendChild(row);
        scrollToBottom();
    }

    // 12. Helper: Typing indicator
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
        escaped = escaped.replace(/`([^`]+)`/g, '<code style="background: rgba(255,255,255,0.1); padding: 2px 6px; border-radius: 4px; font-family: var(--font-mono); font-size: 0.85em;">$1</code>');

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

