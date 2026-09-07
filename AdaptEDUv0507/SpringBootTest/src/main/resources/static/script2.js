// AdaptEDU – Rebuilt Script
// Features: week/month/day views · click-to-open detail popover
//           archive (manual archive via popover)
//           distinct category colors · Apple Calendar UX

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function cleanStringValue(val, fallback = '') {
    if (val === null || val === undefined) return fallback;
    if (typeof val === 'object') {
        if (val.name) return cleanStringValue(val.name, fallback);
        if (val.title) return cleanStringValue(val.title, fallback);
        if (val.id) return cleanStringValue(val.id, fallback);
        return fallback;
    }
    let str = String(val).trim();
    let iterations = 0;
    while (str.startsWith('{') && iterations < 10) {
        iterations++;
        const match = str.match(/name=([^,{}]+)/);
        if (match && match[1]) {
            str = match[1].trim();
        } else {
            const idMatch = str.match(/id=([^,{}]+)/);
            if (idMatch && idMatch[1]) {
                str = idMatch[1].trim();
            } else {
                str = str.replace(/^[{}]+|[{}]+$/g, '').trim();
                break;
            }
        }
    }
    return str || fallback;
}

function cleanIdValue(val, prefix = 'item') {
    if (val === null || val === undefined) {
        return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2)}`;
    }
    if (typeof val === 'object') {
        if (val.id) return cleanIdValue(val.id, prefix);
        return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2)}`;
    }
    let str = String(val).trim();
    let iterations = 0;
    while (str.startsWith('{') && iterations < 10) {
        iterations++;
        const match = str.match(/id=([^,{}]+)/);
        if (match && match[1]) {
            str = match[1].trim();
        } else {
            break;
        }
    }
    if (!str || str.startsWith('{')) {
        return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2)}`;
    }
    return str;
}

class Task {
    constructor(name, category, dueDate, userPriority, estimatedTime, maxSessionLength = 120, description = '', completed = false) {
        this.id = cleanIdValue(null, 'task');
        this.type = 'task';
        this.name = cleanStringValue(name, 'Untitled Task');
        this.category = cleanStringValue(category, 'other').toLowerCase();
        this.dueDate = new Date(dueDate);
        this.userPriority = parseInt(userPriority) || 5;
        this.estimatedTime = parseInt(estimatedTime) || 60;
        this.maxSessionLength = parseInt(maxSessionLength, 10);
        if (isNaN(this.maxSessionLength)) this.maxSessionLength = 120;
        this.description = cleanStringValue(description, '');
        this.completed = !!completed;
        this.archived = false;
        this.minutesSpent = 0;
        this.archivedAt = null;
    }
    getHoursUntilDue() { return (this.dueDate - new Date()) / 3600000; }
    isOverdue() { return !this.completed && new Date() > this.dueDate; }
    getPriorityScore() {
        if (this.isOverdue()) return Infinity;
        if (this.completed) return -1;
        const tp = 10.0 / (this.getHoursUntilDue() + 1);
        return this.userPriority + tp;
    }
    getMinutesRemaining() { return Math.max(0, this.estimatedTime - this.minutesSpent); }
}

class CalEvent {
    constructor(name, startTime, endTime, location, status, category, reminderEnabled = false, reminderEveryDays = 1, recurrence = 'none', recurrenceId = null) {
        this.id = cleanIdValue(null, 'event');
        this.type = 'event';
        this.name = cleanStringValue(name, 'Untitled Event');
        this.startTime = new Date(startTime);
        this.endTime = new Date(endTime);
        this.location = cleanStringValue(location, '');
        this.status = cleanStringValue(status, 'FIXED');
        this.category = cleanStringValue(category, 'other').toLowerCase();
        this.reminderEnabled = !!reminderEnabled;
        this.reminderEveryDays = parseInt(reminderEveryDays, 10) || 1;
        this.recurrence = recurrence || 'none';
        this.recurrenceId = recurrenceId || null;
        this.archived = false;
        this.archivedAt = null;
    }
    getDurationMins() { return (this.endTime - this.startTime) / 60000; }
}

// ── State ──────────────────────────────────────────────────────────────────
let currentDate = new Date();          // anchor date for all views
let currentView = 'week';              // 'week' | 'month' | 'day'
let tasks  = [];
let events = [];
let scheduledBlocks = [];
let START_HOUR = 8;
let END_HOUR   = 22;
let globalTheme = 'black';
const STORAGE_KEY = 'calendar.state.v1';
let csvSyncTimer = null;
let lastAllClearMessageIndex = -1;
let sessionCheckInterval = null;

// ── Auth & User Session ────────────────────────────────────────────────────
let currentUser = null;
let pendingVerificationEmail = '';

function getStoredUser() {
    try {
        const raw = sessionStorage.getItem('adaptedu.auth_user');
        return raw ? JSON.parse(raw) : null;
    } catch (e) {
        return null;
    }
}

function getUserStorageKey(suffix) {
    if (currentUser && currentUser.userId) {
        return `adaptedu.user_${currentUser.userId}.${suffix}`;
    }
    return `adaptedu.${suffix}`;
}

// ── Pomodoro State ──
let pomoInterval = null;
let pomoTimeLeft = 25 * 60;
let pomoIsWorking = true;
let pomoIsRunning = false;
let pomoWorkDuration = 25;
let pomoBreakDuration = 5;
let currentPomoBlock = null;

// ── Category System ───────────────────────────────────────────────────────
const DEFAULT_CATEGORIES = [
    { id: 'school',          name: 'School',          color: '#82b1ff' },
    { id: 'work',            name: 'Work',            color: '#a5d6a7' },
    { id: 'personal',        name: 'Personal',        color: '#ce93d8' },
    { id: 'extracurricular', name: 'Extracurricular', color: '#ffb74d' },
    { id: 'other',           name: 'Other',           color: '#b0bec5' },
];

let categoriesList = [];
let activeCategoryFilter = null;

function loadCategories() {
    try {
        const saved = JSON.parse(localStorage.getItem(getUserStorageKey('categories')));
        if (Array.isArray(saved) && saved.length > 0) {
            categoriesList = saved;
            return;
        }
    } catch (e) {}
    categoriesList = JSON.parse(JSON.stringify(DEFAULT_CATEGORIES));
}

function saveCategories() {
    localStorage.setItem(getUserStorageKey('categories'), JSON.stringify(categoriesList));
}

function catColor(cat) {
    if (!cat) return '#b0bec5';
    const n = normCat(cat);
    const found = categoriesList.find(c => c.id === n || c.name.toLowerCase() === n);
    if (found) return found.color;
    if (n === 'extra' || n === 'extracurricular') return '#ffb74d';
    return '#b0bec5';
}

function getCategoryName(cat) {
    if (!cat) return 'Other';
    const n = normCat(cat);
    const found = categoriesList.find(c => c.id === n || c.name.toLowerCase() === n);
    if (found) return found.name;
    return capFirst(cat);
}

function hexToRgba(hex, alpha = 0.2) {
    if (!hex || typeof hex !== 'string') return `rgba(176, 190, 197, ${alpha})`;
    let c = hex.replace('#', '').trim();
    if (c.length === 3) c = c.split('').map(x => x + x).join('');
    if (c.length !== 6) return `rgba(176, 190, 197, ${alpha})`;
    const num = parseInt(c, 16);
    if (isNaN(num)) return `rgba(176, 190, 197, ${alpha})`;
    const r = (num >> 16) & 255;
    const g = (num >> 8) & 255;
    const b = num & 255;
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}


// ── Avatar & Profile Helpers ───────────────────────────────────────────────
function getOrGenerateAvatarColor(userId) {
    const key = `avatarColor_${userId || 'default'}`;
    let color = localStorage.getItem(getUserStorageKey(key));
    if (!color) {
        const palette = [
            '#3b82f6', '#10b981', '#8b5cf6', '#f59e0b', '#ec4899',
            '#06b6d4', '#6366f1', '#14b8a6', '#f97316', '#a855f7',
            '#0284c7', '#059669', '#7c3aed', '#d97706', '#db2777'
        ];
        color = palette[Math.floor(Math.random() * palette.length)];
        localStorage.setItem(getUserStorageKey(key), color);
    }
    return color;
}

function updateAvatar(username, userId) {
    const avatarEl = document.getElementById('user-avatar-circle');
    if (!avatarEl) return;
    const name = (username || 'User').trim();
    const initial = name ? name.charAt(0).toUpperCase() : 'U';
    avatarEl.textContent = initial;
    avatarEl.style.backgroundColor = getOrGenerateAvatarColor(userId);
}

async function checkOAuthCallback() {
    if (window.location.hash) {
        if (window.location.hash.includes('error=')) {
            const hashParams = new URLSearchParams(window.location.hash.substring(1));
            const errorDesc = hashParams.get('error_description') || hashParams.get('error') || 'OAuth authentication failed.';
            history.replaceState(null, '', window.location.pathname);
            const authAlert = document.getElementById('auth-alert');
            if (authAlert) {
                authAlert.textContent = decodeURIComponent(errorDesc.replace(/\+/g, ' '));
                authAlert.className = 'auth-alert';
            }
            return false;
        }
        if (window.location.hash.includes('access_token=')) {
            const hashParams = new URLSearchParams(window.location.hash.substring(1));
            const accessToken = hashParams.get('access_token');
            if (accessToken) {
                history.replaceState(null, '', window.location.pathname);
                try {
                    const res = await fetch(buildApiUrl('/api/auth/oauth-callback'), {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ accessToken })
                    });
                    const data = await res.json();
                    if (data.status === 'ok') {
                        const user = { userId: data.userId, email: data.email, username: data.username };
                        sessionStorage.setItem('adaptedu.auth_user', JSON.stringify(user));
                        await initializeUserSession(user);
                        return true;
                    } else {
                        const authAlert = document.getElementById('auth-alert');
                        if (authAlert) {
                            authAlert.textContent = data.message || 'Google sign-in failed.';
                            authAlert.className = 'auth-alert';
                        }
                    }
                } catch (err) {
                    console.warn('OAuth callback handling failed:', err);
                }
            }
        }
    }
    return false;
}

// ── Init & Session Handling ────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    setupListeners();
    setupAuthListeners();

    // Check if returning from Google OAuth redirect
    const handledOAuth = await checkOAuthCallback();
    if (handledOAuth) return;

    const storedUser = getStoredUser();
    if (storedUser && storedUser.userId) {
        initializeUserSession(storedUser);
    } else {
        // Show auth modal and enforce login
        const authOverlay = document.getElementById('auth-overlay');
        if (authOverlay) authOverlay.classList.remove('hidden');
    }

    // Start checking for active sessions
    if (sessionCheckInterval) clearInterval(sessionCheckInterval);
    sessionCheckInterval = setInterval(checkActiveSession, 10000);

    // Register Service Worker for PWA
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/service-worker.js')
            .then(reg => console.log('Service Worker registered', reg))
            .catch(err => console.warn('Service Worker registration failed', err));
    }
});

async function initializeUserSession(user) {
    currentUser = user;
    if (!currentUser.username) {
        currentUser.username = localStorage.getItem(getUserStorageKey('username')) || (currentUser.email ? currentUser.email.split('@')[0] : 'User');
    }
    localStorage.setItem(getUserStorageKey('username'), currentUser.username);

    // Hide auth modal
    const authOverlay = document.getElementById('auth-overlay');
    if (authOverlay) authOverlay.classList.add('hidden');

    // Update header profile badge & dropdown info
    const badge = document.getElementById('user-profile-badge');
    const displayName = document.getElementById('user-display-name');
    const ddName = document.getElementById('user-dropdown-name');
    const ddEmail = document.getElementById('user-dropdown-email');
    if (displayName) displayName.textContent = currentUser.username;
    if (ddName) ddName.textContent = currentUser.username;
    if (ddEmail) ddEmail.textContent = currentUser.email || '';
    updateAvatar(currentUser.username, currentUser.userId);
    if (badge) badge.classList.remove('hidden');

    // Load user settings (theme, sleep/wake hours)
    const savedSettings = JSON.parse(localStorage.getItem(getUserStorageKey('settings')) || '{}');
    globalTheme = savedSettings.theme || 'black';
    START_HOUR = (savedSettings.startHour !== undefined) ? parseInt(savedSettings.startHour, 10) : 8;
    END_HOUR = (savedSettings.endHour !== undefined) ? parseInt(savedSettings.endHour, 10) : 22;
    setGlobalTheme(globalTheme);


    const savedPomoTheme = localStorage.getItem(getUserStorageKey('pomoTheme')) || 'black';
    setPomodoroTheme(savedPomoTheme);

    // Load user categories
    loadCategories();
    populateCategoryDropdowns();
    renderSidebarCategories();

    // Load user tasks and events:
    // Try cloud first (Supabase), fallback to local user storage, fallback to seedDemoData if empty
    const cloudLoaded = await loadCloudState(user.userId);
    if (!cloudLoaded) {
        const localLoaded = loadState();
        if (!localLoaded) {
            seedDemoData();
            saveState();
        }

    }

    currentDate = new Date();
    currentDate.setHours(0, 0, 0, 0);

    if (window.innerWidth <= 768 && currentView !== 'day') {
        currentView = 'day';
        document.querySelectorAll('.view-btn').forEach(b => {
            b.classList.toggle('active', b.dataset.view === 'day');
        });
    }

    if (currentView === 'week') snapToMonday(currentDate);
    refreshAll(true);
    // Start checking for active sessions
    if (sessionCheckInterval) clearInterval(sessionCheckInterval);
    sessionCheckInterval = setInterval(checkActiveSession, 10000);

    // Register Service Worker for PWA
    //if ('serviceWorker' in navigator) {
        //navigator.serviceWorker.register('/service-worker.js')
            //.catch(err => console.warn('Service Worker registration failed', err));
   //}

    setTimeout(() => {
        checkEventOverlaps();
        checkDailyWorkloadAndSleepRisk();
    }, 600);

    fetchScheduledBlocks();
}


function snapToMonday(d) {
    const day = d.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diff);
    d.setHours(0, 0, 0, 0);
}

let tempTheme = 'black';

function openSettingsModal() {
    const settingsModal = document.getElementById('settings-modal');
    if (!settingsModal) return;
    document.getElementById('settings-wake').value = `${String(START_HOUR).padStart(2, '0')}:00`;
    document.getElementById('settings-sleep').value = `${String(END_HOUR).padStart(2, '0')}:00`;
    tempTheme = globalTheme;
    document.querySelectorAll('.global-color-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.color === globalTheme);
    });
    settingsModal.classList.remove('hidden');
}

// ── Event Listeners ────────────────────────────────────────────────────────
function setupListeners() {
    // Mobile Panel Toggles
    const mobileSidebarToggle = document.getElementById('mobile-sidebar-toggle');
    const sidebar = document.querySelector('.sidebar');
    if (mobileSidebarToggle && sidebar) {
        mobileSidebarToggle.addEventListener('click', () => {
            sidebar.classList.toggle('mobile-open');
            mobileSidebarToggle.textContent = sidebar.classList.contains('mobile-open') ? 'Hide Filters & Stats ▲' : 'Show Filters & Stats ▼';
        });
    }

    const mobileTasksToggle = document.getElementById('mobile-tasks-toggle');
    const taskPanel = document.querySelector('.task-list-panel');
    if (mobileTasksToggle && taskPanel) {
        mobileTasksToggle.addEventListener('click', () => {
            taskPanel.classList.toggle('mobile-open');
            mobileTasksToggle.textContent = taskPanel.classList.contains('mobile-open') ? 'Hide Tasks & Events ▲' : 'Show Tasks & Events ▼';
            if (taskPanel.classList.contains('mobile-open')) {
                setTimeout(() => taskPanel.scrollIntoView({ behavior: 'smooth' }), 50);
            }
        });
    }

    // Navigation
    document.getElementById('prev-btn').addEventListener('click', () => {
        if (currentView === 'week')  currentDate.setDate(currentDate.getDate() - 7);
        if (currentView === 'month') currentDate.setMonth(currentDate.getMonth() - 1);
        if (currentView === 'day')   currentDate.setDate(currentDate.getDate() - 1);
        refreshAll();
    });
    document.getElementById('next-btn').addEventListener('click', () => {
        if (currentView === 'week')  currentDate.setDate(currentDate.getDate() + 7);
        if (currentView === 'month') currentDate.setMonth(currentDate.getMonth() + 1);
        if (currentView === 'day')   currentDate.setDate(currentDate.getDate() + 1);
        refreshAll();
    });
    document.getElementById('today-btn').addEventListener('click', () => {
        currentDate = new Date();
        currentDate.setHours(0, 0, 0, 0);
        if (currentView === 'week') snapToMonday(currentDate);
        refreshAll();
    });

    // View toggle
    document.querySelectorAll('.view-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentView = btn.dataset.view;
            if (currentView === 'week' || currentView === 'day') {
                // make sure currentDate is a Monday for week, or actual today for day
                if (currentView === 'week') snapToMonday(currentDate);
            }
            refreshAll();
        });
    });

    // Pomodoro listeners
    document.getElementById('pomodoro-btn').addEventListener('click', openPomodoro);
    document.getElementById('pomo-popup-join').addEventListener('click', openPomodoro);
    document.getElementById('close-pomo-btn').addEventListener('click', closePomodoro);

    document.getElementById('pomo-start-pause-btn').addEventListener('click', () => {
        if (pomoIsRunning) pausePomodoro();
        else startPomodoro();
    });

    document.getElementById('pomo-skip-btn').addEventListener('click', () => {
        switchPomoPhase(!pomoIsWorking);
    });

    document.querySelectorAll('.pomo-mode-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            document.querySelectorAll('.pomo-mode-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            pomoWorkDuration = parseInt(btn.dataset.work, 10);
            pomoBreakDuration = parseInt(btn.dataset.break, 10);

            // If not running, reset timer to new duration
            if (!pomoIsRunning) {
                pomoTimeLeft = (pomoIsWorking ? pomoWorkDuration : pomoBreakDuration) * 60;
                updatePomoDisplay();
            }
        });
    });

    // Pomodoro theme selector
    document.querySelectorAll('.pomo-color-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const color = btn.dataset.color;
            setPomodoroTheme(color);
            localStorage.setItem(getUserStorageKey('pomoTheme'), color);
        });
    });
    const savedPomoTheme = localStorage.getItem(getUserStorageKey('pomoTheme')) || 'black';
    setPomodoroTheme(savedPomoTheme);

    // Settings Menu
    const settingsModal = document.getElementById('settings-modal');
    const settingsBtn = document.getElementById('settings-btn');
    if (settingsBtn) {
        settingsBtn.addEventListener('click', openSettingsModal);
    }

    ['close-settings-modal', 'cancel-settings-btn'].forEach(id => {
        const el = document.getElementById(id);
        if (el && settingsModal) {
            el.addEventListener('click', () => settingsModal.classList.add('hidden'));
        }
    });

    document.querySelectorAll('.global-color-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.global-color-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            tempTheme = btn.dataset.color;
        });
    });

    document.getElementById('settings-form').addEventListener('submit', (e) => {
        e.preventDefault();
        globalTheme = tempTheme;
        setGlobalTheme(globalTheme);

        const wake = document.getElementById('settings-wake').value;
        const sleep = document.getElementById('settings-sleep').value;
        START_HOUR = parseInt(wake.split(':')[0], 10);
        END_HOUR = parseInt(sleep.split(':')[0], 10);

        if (START_HOUR >= END_HOUR) { alert("Wake up time must be before sleep time!"); return; }

        localStorage.setItem(getUserStorageKey('settings'), JSON.stringify({ theme: globalTheme, startHour: START_HOUR, endHour: END_HOUR }));
        settingsModal.classList.add('hidden');
        refreshAll(true);
    });

    // Task/Archive tabs
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            renderTaskList(btn.dataset.tab);
        });
    });

    // Modals
    const taskModal  = document.getElementById('add-task-modal');
    const eventModal = document.getElementById('add-event-modal');
    const eventReminderSelect = document.getElementById('event-reminder-enabled');
    const eventReminderDaysGroup = document.getElementById('event-reminder-days-group');
    const eventRecurrenceSelect = document.getElementById('event-recurrence');
    const eventRecurrenceCountGroup = document.getElementById('event-recurrence-count-group');

    const updateEventReminderVisibility = () => {
        if (!eventReminderSelect || !eventReminderDaysGroup) return;
        const shouldShow = eventReminderSelect.value === 'yes';
        eventReminderDaysGroup.classList.toggle('hidden', !shouldShow);
    };

    const updateEventRecurrenceVisibility = () => {
        if (!eventRecurrenceSelect || !eventRecurrenceCountGroup) return;
        const shouldShow = eventRecurrenceSelect.value !== 'none';
        eventRecurrenceCountGroup.classList.toggle('hidden', !shouldShow);
    };

    document.getElementById('add-task-btn').addEventListener('click', () => {
        taskModal.classList.remove('hidden');
    });
    document.getElementById('add-event-btn').addEventListener('click', () => {
        updateEventReminderVisibility();
        updateEventRecurrenceVisibility();
        eventModal.classList.remove('hidden');
    });

    if (eventReminderSelect) {
        eventReminderSelect.addEventListener('change', updateEventReminderVisibility);
    }
    if (eventRecurrenceSelect) {
        eventRecurrenceSelect.addEventListener('change', updateEventRecurrenceVisibility);
    }

    [
        document.getElementById('close-task-modal'),
        document.getElementById('cancel-task-btn'),
    ].forEach(el => el.addEventListener('click', () => {
        taskModal.classList.add('hidden');
    }));

    [
        document.getElementById('close-event-modal'),
        document.getElementById('cancel-event-btn'),
    ].forEach(el => el.addEventListener('click', () => {
        eventModal.classList.add('hidden');
        updateEventReminderVisibility();
        updateEventRecurrenceVisibility();
    }));

    // Form: add task
    document.getElementById('task-form').addEventListener('submit', e => {
        e.preventDefault();
        const f = e.target;

        const rawEstimatedMinutes = parseInt(f['task-estimated-time'].value, 10) || 0;
        const maxSessionLength = parseInt(f['task-max-session-length'].value, 10) || 120;

        tasks.push(new Task(
            cleanStringValue(f['task-name'].value),
            cleanStringValue(f['task-category'].value),
            f['task-due-date'].value,
            f['task-priority'].value,
            rawEstimatedMinutes,
            maxSessionLength,
            cleanStringValue(f['task-description'].value)
        ));

        taskModal.classList.add('hidden');
        f.reset();
        refreshAll(true);
    });

    // Form: add event
    document.getElementById('event-form').addEventListener('submit', e => {
        e.preventDefault();
        const f = e.target;

        const eventDate = f['event-date'].value; // "YYYY-MM-DD"
        const startTimeStr = `${eventDate}T${f['event-start-time'].value}`; // "YYYY-MM-DDThh:mm"
        const endTimeStr = `${eventDate}T${f['event-end-time'].value}`;

        const baseStartDate = new Date(startTimeStr);
        const baseEndDate = new Date(endTimeStr);
        const durationMs = Math.max(0, baseEndDate.getTime() - baseStartDate.getTime());

        const eventName = cleanStringValue(f['event-name'].value, 'Untitled Event');
        const location = cleanStringValue(f['event-location'].value, '');
        const status = f['event-status'].value || 'FIXED';
        const category = cleanStringValue(f['event-category'].value, 'other');
        const reminderEnabled = f['event-reminder-enabled'].value === 'yes';
        const reminderEveryDays = f['event-reminder-every-days'].value;

        const recurrence = f['event-recurrence'] ? f['event-recurrence'].value : 'none';
        const repeatWeeks = f['event-recurrence-count'] ? (parseInt(f['event-recurrence-count'].value, 10) || 4) : 4;

        const occurrenceDates = [];
        if (recurrence === 'daily') {
            const totalDays = repeatWeeks * 7;
            for (let i = 0; i < totalDays; i++) {
                const cur = new Date(baseStartDate);
                cur.setDate(cur.getDate() + i);
                occurrenceDates.push(cur);
            }
        } else if (recurrence === 'weekdays') {
            const totalDays = repeatWeeks * 7;
            for (let i = 0; i < totalDays; i++) {
                const cur = new Date(baseStartDate);
                cur.setDate(cur.getDate() + i);
                const dayOfWeek = cur.getDay();
                if (dayOfWeek !== 0 && dayOfWeek !== 6) { // Mon-Fri
                    occurrenceDates.push(cur);
                }
            }
        } else if (recurrence === 'weekly') {
            for (let i = 0; i < repeatWeeks; i++) {
                const cur = new Date(baseStartDate);
                cur.setDate(cur.getDate() + i * 7);
                occurrenceDates.push(cur);
            }
        } else if (recurrence === 'biweekly') {
            const count = Math.max(1, Math.ceil(repeatWeeks / 2));
            for (let i = 0; i < count; i++) {
                const cur = new Date(baseStartDate);
                cur.setDate(cur.getDate() + i * 14);
                occurrenceDates.push(cur);
            }
        } else if (recurrence === 'monthly') {
            const count = Math.max(1, Math.round(repeatWeeks / 4));
            for (let i = 0; i < count; i++) {
                const cur = new Date(baseStartDate);
                cur.setMonth(cur.getMonth() + i);
                occurrenceDates.push(cur);
            }
        } else {
            occurrenceDates.push(baseStartDate);
        }

        const recurrenceId = occurrenceDates.length > 1 ? `rec_${Date.now()}_${Math.random().toString(36).slice(2)}` : null;

        occurrenceDates.forEach(occStart => {
            const occEnd = new Date(occStart.getTime() + durationMs);
            const ev = new CalEvent(
                eventName,
                occStart,
                occEnd,
                location,
                status,
                category,
                reminderEnabled,
                reminderEveryDays,
                recurrence,
                recurrenceId
            );
            events.push(ev);
        });


        eventModal.classList.add('hidden');
        f.reset();
        f['event-reminder-enabled'].value = 'no';
        f['event-reminder-every-days'].value = '1';
        if (f['event-recurrence']) f['event-recurrence'].value = 'none';
        if (f['event-recurrence-count']) f['event-recurrence-count'].value = '4';
        updateEventReminderVisibility();
        updateEventRecurrenceVisibility();
        refreshAll(true);

        if (occurrenceDates.length > 0) {
            checkEventOverlaps(events[events.length - 1]);
        }
        checkDailyWorkloadAndSleepRisk();

    });

    updateEventReminderVisibility();
    updateEventRecurrenceVisibility();

    // Close popover on outside click
    document.addEventListener('click', e => {
        const pop = document.getElementById('detail-popover');
        if (!pop.classList.contains('hidden') && !pop.contains(e.target) && !e.target.closest('.cal-block') && !e.target.closest('.task-card') && !e.target.closest('.event-card') && !e.target.closest('.month-event-pill')) {
            pop.classList.add('hidden');
        }
    });

    document.getElementById('close-popover').addEventListener('click', () => {
        document.getElementById('detail-popover').classList.add('hidden');
    });

    // Manage Categories Modal listeners
    const catModal = document.getElementById('categories-modal');
    const editCatBtn = document.getElementById('edit-categories-btn');
    if (editCatBtn && catModal) {
        editCatBtn.addEventListener('click', () => {
            renderManageCategoriesList();
            catModal.classList.remove('hidden');
        });
    }
    ['close-categories-modal', 'close-categories-btn'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn && catModal) {
            btn.addEventListener('click', () => catModal.classList.add('hidden'));
        }
    });
    if (catModal) {
        catModal.addEventListener('click', e => {
            if (e.target === catModal) catModal.classList.add('hidden');
        });
    }
    const addCatForm = document.getElementById('add-category-form');
    if (addCatForm) {
        addCatForm.addEventListener('submit', e => {
            e.preventDefault();
            const nameInput = document.getElementById('new-cat-name');
            const colorInput = document.getElementById('new-cat-color');
            if (!nameInput || !nameInput.value.trim()) return;
            addCategory(nameInput.value, colorInput ? colorInput.value : '#82b1ff');
            nameInput.value = '';
        });
    }
}

// ── Auth Modal & User Session Handlers ─────────────────────────────────────
function setupAuthListeners() {
    const authOverlay = document.getElementById('auth-overlay');
    const authAlert = document.getElementById('auth-alert');
    const tabLogin = document.getElementById('auth-tab-login');
    const tabSignup = document.getElementById('auth-tab-signup');
    const loginForm = document.getElementById('login-form');
    const signupForm = document.getElementById('signup-form');
    const verifyView = document.getElementById('auth-verify-view');
    const backToLoginBtn = document.getElementById('back-to-login-btn');
    const resendVerifyBtn = document.getElementById('resend-verify-btn');
    const logoutBtn = document.getElementById('logout-btn');
    const userProfileBadge = document.getElementById('user-profile-badge');
    const userDropdownMenu = document.getElementById('user-dropdown-menu');
    const openSettingsItem = document.getElementById('open-settings-item');

    // Profile Dropdown Toggle
    if (userProfileBadge && userDropdownMenu) {
        userProfileBadge.addEventListener('click', (e) => {
            e.stopPropagation();
            const isHidden = userDropdownMenu.classList.contains('hidden');
            userDropdownMenu.classList.toggle('hidden', !isHidden);
            const container = userProfileBadge.closest('.user-profile-container');
            if (container) container.classList.toggle('active', isHidden);
        });

        // Close dropdown when clicking anywhere outside
        document.addEventListener('click', (e) => {
            if (!userDropdownMenu.classList.contains('hidden')) {
                if (!userDropdownMenu.contains(e.target) && !userProfileBadge.contains(e.target)) {
                    userDropdownMenu.classList.add('hidden');
                    const container = userProfileBadge.closest('.user-profile-container');
                    if (container) container.classList.remove('active');
                }
            }
        });
    }

    if (openSettingsItem) {
        openSettingsItem.addEventListener('click', () => {
            if (userDropdownMenu) userDropdownMenu.classList.add('hidden');
            if (userProfileBadge) {
                const container = userProfileBadge.closest('.user-profile-container');
                if (container) container.classList.remove('active');
            }
            openSettingsModal();
        });
    }

    // Google OAuth Handler
    const handleGoogleAuth = async () => {
        try {
            const redirectUrl = window.location.origin + window.location.pathname;
            const res = await fetch(buildApiUrl(`/api/auth/oauth/google-url?redirectTo=${encodeURIComponent(redirectUrl)}`));
            const data = await res.json();
            if (data && data.url) {
                window.location.href = data.url;
            } else {
                showAuthAlert('Unable to start Google sign-in. Please try again.');
            }
        } catch (err) {
            console.error('Google auth error:', err);
            showAuthAlert('Failed to connect to authentication service.');
        }
    };

    const googleBtnLogin = document.getElementById('google-auth-btn-login');
    const googleBtnSignup = document.getElementById('google-auth-btn-signup');
    if (googleBtnLogin) googleBtnLogin.addEventListener('click', handleGoogleAuth);
    if (googleBtnSignup) googleBtnSignup.addEventListener('click', handleGoogleAuth);

    function showAuthAlert(msg, isSuccess = false) {
        if (!authAlert) return;
        authAlert.textContent = msg;
        authAlert.className = 'auth-alert' + (isSuccess ? ' success' : '');
    }

    function hideAuthAlert() {
        if (!authAlert) return;
        authAlert.className = 'auth-alert hidden';
        authAlert.textContent = '';
    }

    function switchToLoginTab() {
        hideAuthAlert();
        if (tabLogin) tabLogin.classList.add('active');
        if (tabSignup) tabSignup.classList.remove('active');
        if (loginForm) loginForm.classList.remove('hidden');
        if (signupForm) signupForm.classList.add('hidden');
        if (verifyView) verifyView.classList.add('hidden');
    }

    function switchToSignupTab() {
        hideAuthAlert();
        if (tabSignup) tabSignup.classList.add('active');
        if (tabLogin) tabLogin.classList.remove('active');
        if (signupForm) signupForm.classList.remove('hidden');
        if (loginForm) loginForm.classList.add('hidden');
        if (verifyView) verifyView.classList.add('hidden');
    }

    if (tabLogin) tabLogin.addEventListener('click', switchToLoginTab);
    if (tabSignup) tabSignup.addEventListener('click', switchToSignupTab);
    if (backToLoginBtn) backToLoginBtn.addEventListener('click', switchToLoginTab);

    // Login submit
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            hideAuthAlert();
            const email = document.getElementById('login-email').value.trim();
            const password = document.getElementById('login-password').value;
            const submitBtn = document.getElementById('login-btn');

            if (!email || !password) {
                showAuthAlert('Please enter both email and password.');
                return;
            }

            submitBtn.disabled = true;
            submitBtn.textContent = 'Signing In...';

            try {
                const res = await fetch(buildApiUrl('/api/auth/login'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email, password })
                });
                const data = await res.json();

                if (!res.ok || data.status !== 'ok') {
                    if (data.unconfirmed) {
                        pendingVerificationEmail = data.email || email;
                        showAuthAlert(data.message || 'Email not confirmed. Please check your inbox or click resend.');
                        if (signupForm) signupForm.classList.add('hidden');
                        if (loginForm) loginForm.classList.add('hidden');
                        if (verifyView) verifyView.classList.remove('hidden');
                        const vMsg = document.getElementById('verify-msg');
                        if (vMsg) {
                            vMsg.textContent = `Your email (${pendingVerificationEmail}) is not yet confirmed. Please check your inbox for the confirmation link, or click below to resend.`;
                        }
                    } else {
                        showAuthAlert(data.message || 'Login failed. Please check your credentials.');
                    }
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Sign In';
                    return;
                }

                // Successful login
                const user = { userId: data.userId, email: data.email, username: data.username };
                sessionStorage.setItem('adaptedu.auth_user', JSON.stringify(user));
                submitBtn.disabled = false;
                submitBtn.textContent = 'Sign In';
                document.getElementById('login-password').value = '';
                await initializeUserSession(user);
            } catch (err) {
                showAuthAlert('Network error while connecting to server. Please try again.');
                submitBtn.disabled = false;
                submitBtn.textContent = 'Sign In';
            }
        });
    }

    // Signup submit
    if (signupForm) {
        signupForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            hideAuthAlert();
            const usernameInput = document.getElementById('signup-username');
            const username = usernameInput ? usernameInput.value.trim() : '';
            const email = document.getElementById('signup-email').value.trim();
            const password = document.getElementById('signup-password').value;
            const confirm = document.getElementById('signup-password-confirm').value;
            const submitBtn = document.getElementById('signup-btn');

            if (!username || username.length < 2) {
                showAuthAlert('Username must be at least 2 characters long.');
                return;
            }
            if (password !== confirm) {
                showAuthAlert('Passwords do not match.');
                return;
            }
            if (password.length < 6) {
                showAuthAlert('Password must be at least 6 characters long.');
                return;
            }

            submitBtn.disabled = true;
            submitBtn.textContent = 'Creating Account...';

            try {
                const res = await fetch(buildApiUrl('/api/auth/signup'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email, password, username })
                });
                const data = await res.json();

                if (!res.ok || data.status !== 'ok') {
                    showAuthAlert(data.message || 'Registration failed. Please try again.');
                    submitBtn.disabled = false;
                    submitBtn.textContent = 'Create Account';
                    return;
                }

                pendingVerificationEmail = data.email || email;
                submitBtn.disabled = false;
                submitBtn.textContent = 'Create Account';
                if (usernameInput) usernameInput.value = '';
                document.getElementById('signup-password').value = '';
                document.getElementById('signup-password-confirm').value = '';

                // Show verify confirmation view
                if (signupForm) signupForm.classList.add('hidden');
                if (loginForm) loginForm.classList.add('hidden');
                if (verifyView) verifyView.classList.remove('hidden');
                const vMsg = document.getElementById('verify-msg');
                if (vMsg) {
                    vMsg.textContent = `A verification link has been sent to ${pendingVerificationEmail}. Please check your inbox and confirm your email, then return here to sign in.`;
                }
                const loginEmailInput = document.getElementById('login-email');
                if (loginEmailInput) loginEmailInput.value = pendingVerificationEmail;
            } catch (err) {
                showAuthAlert('Network error while connecting to server. Please try again.');
                submitBtn.disabled = false;
                submitBtn.textContent = 'Create Account';
            }
        });
    }

    // Resend verification email
    if (resendVerifyBtn) {
        resendVerifyBtn.addEventListener('click', async () => {
            const email = pendingVerificationEmail || (document.getElementById('login-email') ? document.getElementById('login-email').value.trim() : '') || (document.getElementById('signup-email') ? document.getElementById('signup-email').value.trim() : '');
            if (!email) {
                showAuthAlert('Please enter your email to resend verification.');
                return;
            }

            resendVerifyBtn.disabled = true;
            resendVerifyBtn.textContent = 'Sending...';

            try {
                const res = await fetch(buildApiUrl('/api/auth/resend'), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email })
                });
                const data = await res.json();
                if (data.status === 'ok') {
                    showAuthAlert('Verification email resent! Please check your inbox.', true);
                } else {
                    showAuthAlert(data.message || 'Could not resend email.');
                }
            } catch (err) {
                showAuthAlert('Failed to send request. Check your connection.');
            } finally {
                resendVerifyBtn.disabled = false;
                resendVerifyBtn.textContent = 'Resend Verification Email';
            }
        });
    }

    // Logout
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            handleLogout();
        });
    }
}

function handleLogout() {
    sessionStorage.removeItem('adaptedu.auth_user');
    currentUser = null;
    tasks = [];
    events = [];
    scheduledBlocks = [];

    const badge = document.getElementById('user-profile-badge');
    if (badge) {
        badge.classList.add('hidden');
        const container = badge.closest('.user-profile-container');
        if (container) container.classList.remove('active');
    }
    const dropdown = document.getElementById('user-dropdown-menu');
    if (dropdown) dropdown.classList.add('hidden');

    const authOverlay = document.getElementById('auth-overlay');
    if (authOverlay) authOverlay.classList.remove('hidden');

    // Reset forms and view
    const tabLogin = document.getElementById('auth-tab-login');
    const tabSignup = document.getElementById('auth-tab-signup');
    const loginForm = document.getElementById('login-form');
    const signupForm = document.getElementById('signup-form');
    const verifyView = document.getElementById('auth-verify-view');
    const authAlert = document.getElementById('auth-alert');

    if (tabLogin) tabLogin.classList.add('active');
    if (tabSignup) tabSignup.classList.remove('active');
    if (loginForm) loginForm.classList.remove('hidden');
    if (signupForm) signupForm.classList.add('hidden');
    if (verifyView) verifyView.classList.add('hidden');
    if (authAlert) authAlert.className = 'auth-alert hidden';

    renderCalendar();
    renderTasks();
    renderSidebarCategories();
}

// ── Schedule Conflict & Recommendation Alerts ─────────────────────────────
const alertQueue = [];
const acknowledgedOverloadDays = new Set();
const acknowledgedEventOverlapPairs = new Set();

function showScheduleAlert({ icon = '⚠️', title = 'Schedule Notice', message, recommendation }) {
    const modal = document.getElementById('schedule-alert-modal');
    if (!modal) return;

    // If modal is currently displayed, queue the alert so it appears sequentially
    if (!modal.classList.contains('hidden')) {
        alertQueue.push({ icon, title, message, recommendation });
        return;
    }

    const iconEl = document.getElementById('alert-modal-icon');
    const titleEl = document.getElementById('alert-modal-title');
    const msgEl = document.getElementById('alert-modal-message');
    const recEl = document.getElementById('alert-modal-recommendation-text');

    if (iconEl) iconEl.textContent = icon;
    if (titleEl) titleEl.textContent = title;
    if (msgEl) msgEl.textContent = message;
    if (recEl) recEl.textContent = recommendation;

    modal.classList.remove('hidden');
}

function closeScheduleAlert() {
    const modal = document.getElementById('schedule-alert-modal');
    if (modal) modal.classList.add('hidden');

    // Display next queued notification if one exists
    if (alertQueue.length > 0) {
        const nextAlert = alertQueue.shift();
        setTimeout(() => {
            showScheduleAlert(nextAlert);
        }, 220);
    }
}

function checkEventOverlaps(newEvent = null) {
    const activeEvents = events.filter(ev => !ev.archived);

    if (newEvent) {
        const conflicts = activeEvents.filter(ev =>
            ev.id !== newEvent.id &&
            newEvent.startTime < ev.endTime && ev.startTime < newEvent.endTime
        );
        if (conflicts.length > 0) {
            conflicts.forEach(ev => {
                const pairKey = [newEvent.id, ev.id].sort().join('--');
                acknowledgedEventOverlapPairs.add(pairKey);
            });
            const conflictNames = conflicts.map(ev => `"${ev.name}" (${fmtTime(ev.startTime)} – ${fmtTime(ev.endTime)})`).join(', ');
            showScheduleAlert({
                icon: '⚠️',
                title: 'Event Overlap Notice',
                message: `Your event "${newEvent.name}" (${fmtTime(newEvent.startTime)} – ${fmtTime(newEvent.endTime)}) overlaps with ${conflictNames} on ${newEvent.startTime.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}.`,
                recommendation: 'Because these events occur during the same time window, you may be double-booked. We recommend adjusting the event start or end times to optimize your schedule.'
            });
            return;
        }
    }

    // General check across all active events
    for (let i = 0; i < activeEvents.length; i++) {
        for (let j = i + 1; j < activeEvents.length; j++) {
            const a = activeEvents[i];
            const b = activeEvents[j];
            if (a.startTime < b.endTime && b.startTime < a.endTime) {
                const pairKey = [a.id, b.id].sort().join('--');
                if (!acknowledgedEventOverlapPairs.has(pairKey)) {
                    acknowledgedEventOverlapPairs.add(pairKey);
                    showScheduleAlert({
                        icon: '⚠️',
                        title: 'Event Overlap Notice',
                        message: `Event "${a.name}" (${fmtTime(a.startTime)} – ${fmtTime(a.endTime)}) and "${b.name}" (${fmtTime(b.startTime)} – ${fmtTime(b.endTime)}) overlap on ${a.startTime.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}.`,
                        recommendation: 'These events occur at the same time and may create a scheduling conflict. We recommend adjusting the event times to optimize your daily schedule.'
                    });
                    return;
                }
            }
        }
    }
}

function checkDailyWorkloadAndSleepRisk() {
    const sleepMinutes = (START_HOUR * 60 - END_HOUR * 60 + 1440) % 1440;
    const wakingMinutes = 1440 - sleepMinutes;
    if (wakingMinutes <= 0) return;

    const baseDate = new Date(currentDate);
    const numDays = currentView === 'month' ? 14 : 7;

    const isItemInSleep = (itemStart, itemEnd) => {
        const startFrac = itemStart.getHours() + itemStart.getMinutes() / 60;
        const endFrac   = itemEnd.getHours() + itemEnd.getMinutes() / 60;
        if (START_HOUR < END_HOUR) {
            return startFrac < START_HOUR || endFrac > END_HOUR;
        } else {
            return Math.max(startFrac, END_HOUR) < Math.min(endFrac, START_HOUR);
        }
    };

    for (let i = 0; i < numDays; i++) {
        const d = new Date(baseDate);
        d.setDate(baseDate.getDate() + i);
        const dayStart = new Date(d); dayStart.setHours(0, 0, 0, 0);
        const dayEnd   = new Date(d); dayEnd.setHours(23, 59, 59, 999);
        const dayKey   = `${dayStart.getFullYear()}-${String(dayStart.getMonth()+1).padStart(2,'0')}-${String(dayStart.getDate()).padStart(2,'0')}`;

        // Fixed events on this day
        const dayEvents = events.filter(ev => !ev.archived && ev.startTime >= dayStart && ev.startTime <= dayEnd);
        const eventMins = dayEvents.reduce((sum, ev) => sum + Math.max(0, ev.getDurationMins()), 0);

        // Scheduled task blocks on this day
        const dayBlocks = scheduledBlocks.filter(b => b.startTime >= dayStart && b.startTime <= dayEnd);
        const blockMins = dayBlocks.reduce((sum, b) => sum + Math.max(0, (b.endTime - b.startTime) / 60000), 0);

        // Active tasks due on this day
        const dueTasks = tasks.filter(t => !t.archived && !t.completed && t.dueDate >= dayStart && t.dueDate <= dayEnd);
        const dueTaskMins = dueTasks.reduce((sum, t) => sum + Math.max(0, t.getMinutesRemaining()), 0);

        const taskMins = Math.max(blockMins, dueTaskMins);
        const totalDemandMins = eventMins + taskMins;

        // Check if any scheduled block or event extends into sleep hours
        const sleepInvasion = dayBlocks.some(b => isItemInSleep(b.startTime, b.endTime)) ||
                              dayEvents.some(ev => isItemInSleep(ev.startTime, ev.endTime));

        const isOverloaded = totalDemandMins > wakingMinutes || sleepInvasion;

        if (isOverloaded) {
            if (!acknowledgedOverloadDays.has(dayKey)) {
                acknowledgedOverloadDays.add(dayKey);

                const dayName = dayStart.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric' });
                const totalHoursStr = (totalDemandMins / 60).toFixed(1).replace('.0', '');
                const wakingHoursStr = (wakingMinutes / 60).toFixed(1).replace('.0', '');
                const eventHoursStr = (eventMins / 60).toFixed(1).replace('.0', '');
                const taskHoursStr = (taskMins / 60).toFixed(1).replace('.0', '');

                const fmtHour = h => {
                    const p = h >= 12 ? 'PM' : 'AM';
                    const disp = h % 12 || 12;
                    return `${disp}:00 ${p}`;
                };

                let detail = `On ${dayName}, you have approximately ${totalHoursStr} hours of commitments (${eventHoursStr}h events + ${taskHoursStr}h task work), which exceeds your available waking time of ${wakingHoursStr} hours.`;
                if (sleepInvasion) {
                    detail += ` Scheduled blocks also extend into your sleep hours (${fmtHour(END_HOUR)} – ${fmtHour(START_HOUR)}).`;
                }
                detail += ` Without adjustments, you won't have enough time to finish your tasks today without cutting into your sleep constraints or having to drop a task.`;

                showScheduleAlert({
                    icon: '⏳',
                    title: 'Daily Schedule Overload',
                    message: detail,
                    recommendation: 'We recommend adjusting your task time estimates, rescheduling some tasks across upcoming days, or shortening non-essential events so you can optimize your time better without sacrificing your sleep.'
                });
                break;
            }
        } else {
            acknowledgedOverloadDays.delete(dayKey);
        }
    }

}

function buildApiUrl(path) {
    // Because the Spring Boot backend is serving both our frontend UI and our API,
    // we can simply return the relative path. The browser will automatically append
    // it to whatever domain the user is currently visiting (localhost, ngrok, or a real domain).
    return path;
}

function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// ── Pomodoro Logic ─────────────────────────────────────────────────────────
function checkActiveSession() {
    const now = new Date();
    // Find if the algorithm scheduled a task right now
    const activeBlock = scheduledBlocks.find(b => now >= b.startTime && now <= b.endTime);

    const popup = document.getElementById('pomodoro-popup');
    const pomoView = document.getElementById('pomodoro-view');

    if (activeBlock && pomoView.classList.contains('hidden')) {
        // New session detected, show popup!
        if (!currentPomoBlock || currentPomoBlock.id !== activeBlock.id) {
            document.getElementById('pomo-popup-task').textContent = activeBlock.name;
            popup.classList.remove('hidden');
            // Auto-hide popup after 15s so it's not annoying
            setTimeout(() => popup.classList.add('hidden'), 15000);
            currentPomoBlock = activeBlock;
        }
    } else if (!activeBlock) {
        popup.classList.add('hidden');

        // If they are in the full screen view and the scheduled time ran out, let them know
        if (!pomoView.classList.contains('hidden') && currentPomoBlock) {
            alert(`Your scheduled session for '${currentPomoBlock.name}' is over! Navigating back to calendar.`);
            closePomodoro();
        }
        currentPomoBlock = null;
    }
}

function openPomodoro() {
    document.getElementById('pomodoro-popup').classList.add('hidden');
    document.getElementById('pomodoro-view').classList.remove('hidden');

    // Force a fresh check in case they opened it manually
    const now = new Date();
    currentPomoBlock = scheduledBlocks.find(b => now >= b.startTime && now <= b.endTime);

    const title = document.getElementById('pomo-current-task');
    const times = document.getElementById('pomo-session-times');

    if (currentPomoBlock) {
        title.textContent = currentPomoBlock.name;
        times.textContent = `Scheduled: ${fmtTime(currentPomoBlock.startTime)} – ${fmtTime(currentPomoBlock.endTime)}`;
    } else {
        title.textContent = 'Pomodoro Timer';
        times.textContent = '';
    }

    if (!pomoIsRunning && pomoTimeLeft === pomoWorkDuration * 60) {
        updatePomoDisplay();
    }
}

function closePomodoro() {
    document.getElementById('pomodoro-view').classList.add('hidden');
    pausePomodoro();
}

function startPomodoro() {
    if (pomoIsRunning) return;
    pomoIsRunning = true;
    const btn = document.getElementById('pomo-start-pause-btn');
    btn.textContent = 'Pause';
    btn.classList.add('running');

    pomoInterval = setInterval(() => {
        pomoTimeLeft--;
        if (pomoTimeLeft <= 0) {
            switchPomoPhase(!pomoIsWorking);
        } else {
            updatePomoDisplay();
        }
    }, 1000);
}

function pausePomodoro() {
    pomoIsRunning = false;
    clearInterval(pomoInterval);
    const btn = document.getElementById('pomo-start-pause-btn');
    btn.textContent = 'Resume';
    btn.classList.remove('running');
}

function switchPomoPhase(toWork) {
    pomoIsWorking = toWork;
    pomoTimeLeft = (pomoIsWorking ? pomoWorkDuration : pomoBreakDuration) * 60;

    const label = document.getElementById('pomo-phase-label');
    label.textContent = pomoIsWorking ? 'Work Time' : 'Break Time';
    label.classList.toggle('break-mode', !pomoIsWorking);
    updatePomoDisplay();
}

function updatePomoDisplay() {
    const m = Math.floor(pomoTimeLeft / 60).toString().padStart(2, '0');
    const s = (pomoTimeLeft % 60).toString().padStart(2, '0');
    document.getElementById('pomo-timer-display').textContent = `${m}:${s}`;
}

function setPomodoroTheme(color) {
    const view = document.getElementById('pomodoro-view');
    Array.from(view.classList).forEach(c => {
        if (c.startsWith('theme-')) view.classList.remove(c);
    });
    view.classList.add(`theme-${color}`);

    document.querySelectorAll('.pomo-color-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.color === color);
    });
}

function setGlobalTheme(color) {
    Array.from(document.body.classList).forEach(c => {
        if (c.startsWith('theme-')) document.body.classList.remove(c);
    });
    if (color !== 'black') { document.body.classList.add(`theme-${color}`); }
}

// ── Schedule Integration ───────────────────────────────────────────────────
async function fetchScheduledBlocks() {
    try {
        const windowEnd = new Date(currentDate);
        windowEnd.setDate(windowEnd.getDate() + (currentView === 'day' ? 1 : currentView === 'month' ? 30 : 7));

        // Format local state data into a payload the backend expects
        const payload = {
            tasks: tasks.map(t => ({
                id: t.id, // 👈 ADD THIS LINE
                name: t.name,
                category: t.category,
                dueDate: formatLocalISO(t.dueDate),
                userPriority: t.userPriority,
                estimatedTime: t.estimatedTime,
                maxSessionLength: t.maxSessionLength,
                description: t.description,
                completed: t.completed
            })),
            events: events.map(ev => ({
                id: ev.id, // 👈 ADD THIS LINE
                name: ev.name,
                startTime: formatLocalISO(ev.startTime),
                endTime: formatLocalISO(ev.endTime),
                location: ev.location,
                status: ev.status,
                category: ev.category
            })),
            scheduleStart: formatLocalISO(currentDate), // 👈 ADDED THIS
            scheduleEnd: formatLocalISO(windowEnd)     // 👈 ADDED THIS
        };

        const response = await fetch('/api/schedule', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload), // <-- THIS FIXES THE 400 ERROR

            cache: 'no-store'
        });

        if (!response.ok) return;

        const scheduleData = await response.json();
        console.log("Raw Backend Schedule Data:", scheduleData);

        // Filter out the algorithm's split blocks and format them for the UI
        scheduledBlocks = scheduleData
            .filter(item => item.status === 'SCHEDULED_TASK')
            .map(item => {
                const rawName = cleanStringValue(item.name);
                const sessionMatch = rawName.match(/\s*\(Session \d+\)$/);
                const baseTaskName = rawName.replace(/\s*\(Session \d+\)$/, '');
                const cleanBase = cleanStringValue(baseTaskName);
                const matchedTask = tasks.find(t => cleanStringValue(t.name).toLowerCase() === cleanBase.toLowerCase());
                const cleanBlockName = cleanBase + (sessionMatch ? sessionMatch[0] : '');


                const parsedStart = parseBackendDate(item.startTime);
                const parsedEnd = parseBackendDate(item.endTime);

                console.log(`Task Block '${cleanBlockName}' was placed on the calendar for:`, parsedStart.toLocaleString());

                return {
                    type: 'scheduledBlock',
                    name: cleanBlockName,

                    startTime: parsedStart,
                    endTime: parsedEnd,
                    category: item.category || (matchedTask ? matchedTask.category : 'other'),
                    completed: matchedTask ? matchedTask.completed : false,
                    matchedTask: matchedTask
                };
            });

        renderCalendar();
        checkActiveSession();
    } catch (err) {
        console.warn('Failed to fetch schedule blocks:', err);
    }
}

// ── Refresh ────────────────────────────────────────────────────────────────
function refreshAll(fetchSchedule = false) {
    updateLabel();
    renderCalendar();
    const activeTab = document.querySelector('.tab-btn.active')?.dataset.tab || 'tasks';
    renderTaskList(activeTab);
    updateStats();
    saveState(fetchSchedule);
}

function updateLabel() {
    const el = document.getElementById('week-label');
    if (!el) return;
    if (currentView === 'week') {
        const end = new Date(currentDate);
        end.setDate(currentDate.getDate() + 6);
        const fmt = { month: 'short', day: 'numeric' };
        el.textContent = `${currentDate.toLocaleDateString('en-US', fmt)} – ${end.toLocaleDateString('en-US', fmt)}, ${currentDate.getFullYear()}`;
    } else if (currentView === 'month') {
        el.textContent = currentDate.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
    } else {
        el.textContent = currentDate.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
    }
}

function updateStats() {
    const active = tasks.filter(t => !t.archived);
    const pendingEl = document.getElementById('stat-pending');
    const overdueEl = document.getElementById('stat-overdue');
    const eventsEl = document.getElementById('stat-events');

    if (pendingEl) pendingEl.textContent = active.filter(t => !t.completed).length;
    if (overdueEl) overdueEl.textContent = active.filter(t => t.isOverdue()).length;
    if (eventsEl) eventsEl.textContent  = events.filter(e => !e.archived).length;

    const categories = ['school', 'work', 'personal', 'extracurricular', 'other'];
    const counts = Object.fromEntries(categories.map(c => [c, 0]));

    tasks.filter(t => !t.archived).forEach(t => {
        const category = normCat(t.category);
        counts[category] = (counts[category] || 0) + 1;
    });
    events.filter(e => !e.archived).forEach(e => {
        const category = normCat(e.category);
        counts[category] = (counts[category] || 0) + 1;
    });

    const maxCount = Math.max(1, ...Object.values(counts));
    categories.forEach(category => {
        const countEl = document.getElementById(`bar-count-${category}`);
        const fillEl = document.getElementById(`cat-bar-${category}`);
        if (!countEl || !fillEl) return;
        const count = counts[category] || 0;
        countEl.textContent = String(count);
        const pct = Math.max(6, Math.round((count / maxCount) * 100));
        fillEl.style.width = count === 0 ? '0%' : `${pct}%`;
    });
}

// ══════════════════════════════════════════
// CALENDAR RENDERING
// ══════════════════════════════════════════
function renderCalendar() {
    if (currentView === 'month') renderMonthView();
    else renderTimeGrid(currentView === 'day' ? 1 : 7);
}

// ── Time Grid (Week & Day) ─────────────────────────────────────────────────
function renderTimeGrid(numDays) {
    const cal = document.getElementById('calendar');
    const prevScroll = cal.scrollTop;
    cal.innerHTML = '';
    const wrapper = document.createElement('div');
    wrapper.className = 'calendar-wrapper';

    // Header row
    const header = document.createElement('div');
    header.className = `cal-header${numDays === 1 ? ' day-view' : ''}`;
    header.innerHTML = '<div class="cal-header-spacer"></div>';

    const today = new Date();
    const DAYS  = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];

    for (let i = 0; i < numDays; i++) {
        const d = new Date(currentDate);
        d.setDate(currentDate.getDate() + i);
        const isToday = d.toDateString() === today.toDateString();
        const dIdx = d.getDay() === 0 ? 6 : d.getDay() - 1; // 0=Mon, 6=Sun
        header.innerHTML += `
            <div class="cal-day-header ${isToday ? 'today' : ''}">
                <div class="cal-day-name">${DAYS[dIdx]}</div>
                <div class="cal-day-date">${d.getDate()}</div>
            </div>`;
    }
    wrapper.appendChild(header);

    // Body
    const body = document.createElement('div');
    body.className = `cal-body${numDays === 1 ? ' day-view' : ''}`;

    // Time column
    let timeHTML = '<div class="cal-time-col">';
    for (let h = 0; h < 24; h++) {
        const label = `<span class="cal-time-label">${h === 0 ? '12AM' : (h % 12 || 12) + (h < 12 ? 'AM' : 'PM')}</span>`;
        timeHTML += `<div class="cal-time-slot">${label}</div>`;
    }
    timeHTML += '</div>';
    body.innerHTML = timeHTML;

    for (let i = 0; i < numDays; i++) {
        const d = new Date(currentDate);
        d.setDate(currentDate.getDate() + i);
        const isToday = d.toDateString() === today.toDateString();
        const col = document.createElement('div');
        col.className = `cal-day-col${isToday ? ' today-col' : ''}`;
        col.id = `day-col-${i}`;

        // Shade off-hours visually without restricting the grid space
        const offHoursStart = document.createElement('div');
        offHoursStart.className = 'off-hours-shade';
        offHoursStart.style.cssText = `top: 0; height: ${START_HOUR * 60}px`;
        col.appendChild(offHoursStart);

        const offHoursEnd = document.createElement('div');
        offHoursEnd.className = 'off-hours-shade';
        offHoursEnd.style.cssText = `top: ${END_HOUR * 60}px; height: ${(24 - END_HOUR) * 60}px`;
        col.appendChild(offHoursEnd);

        body.appendChild(col);
    }
    wrapper.appendChild(body);
    cal.appendChild(wrapper);

    // Restore scroll position, or initial-scroll down slightly above the set START_HOUR
    if (prevScroll === 0) {
        cal.scrollTop = Math.max(0, (START_HOUR - 1) * 60);
    } else {
        cal.scrollTop = prevScroll;
    }

    // Current time line
    const endOfRange = new Date(currentDate);
    endOfRange.setDate(currentDate.getDate() + numDays);
    if (today >= currentDate && today < endOfRange) {
        const h = today.getHours(), m = today.getMinutes();
        const dayIndex = numDays === 1 ? 0 : (today.getDay() === 0 ? 6 : today.getDay() - 1);
        const col = document.getElementById(`day-col-${dayIndex}`);
        if (col) {
            const top = (h + m / 60) * 60;
            const line = document.createElement('div');
            line.className = 'current-time-line';
            line.style.top = `${top}px`;
            line.innerHTML = '<div class="current-time-dot"></div>';
            col.appendChild(line);
        }
    }

    // Place blocks
    const gridStart = new Date(currentDate);
    gridStart.setHours(0, 0, 0, 0);

    const gridEnd = new Date(gridStart);
    gridEnd.setDate(gridStart.getDate() + numDays);

    const placeBlock = (item, colIdx, startFrac, endFrac, isTask) => {
        const col = document.getElementById(`day-col-${colIdx}`);
        if (!col || endFrac <= 0 || startFrac >= 24) return;
        const top    = Math.max(0, startFrac) * 60;
        const height = Math.max(18, (Math.min(24, endFrac) - Math.max(0, startFrac)) * 60 - 1);
        const block  = document.createElement('div');
        const catCls = normCat(item.category);
        block.className = `cal-block ${catCls}${isTask ? ' task-block' : ''}`;
        if (isTask && item.completed) block.classList.add('completed-task-block');
        block.style.cssText = `top:${top}px;height:${height}px`;
        const timeStr = isTask && item.type === 'scheduledBlock'
            ? `${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`  // real slot
            : isTask
                ? `${item.completed ? 'Completed · was due' : 'Due'} ${fmtTime(item.dueDate)}`
                : `${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;

        // Clean up title for break blocks to just show 'Break' clearly
        let blockName = cleanStringValue(item.name);
        if (catCls === 'break') {
            blockName = blockName.replace(/\s*\(Session \d+\)$/, '');
        }

        block.innerHTML = `<div class="block-title">${escapeHtml(blockName)}</div><div class="block-time">${timeStr}</div>`;

        let tooltip = cleanStringValue(item.name);
        if (item.type === 'scheduledBlock') {
            tooltip += `\nSession: ${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
            if (item.matchedTask && catCls !== 'break') {
                tooltip += `\nDue: ${item.matchedTask.dueDate.toLocaleDateString()} at ${fmtTime(item.matchedTask.dueDate)}`;
                if (item.matchedTask.description) tooltip += `\nNotes: ${cleanStringValue(item.matchedTask.description)}`;
            }
        } else if (item.type === 'task') {
            tooltip += `\nDue: ${item.dueDate.toLocaleDateString()} at ${fmtTime(item.dueDate)}`;
            tooltip += `\nEst. Time: ${item.estimatedTime}m`;
            if (item.description) tooltip += `\nNotes: ${cleanStringValue(item.description)}`;
        } else {
            tooltip += `\nTime: ${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
            if (item.location) tooltip += `\nLocation: ${cleanStringValue(item.location)}`;
        }
        block.title = tooltip;

        block.addEventListener('click', e => { e.stopPropagation(); showPopover(item, e); });
        col.appendChild(block);
    };

    events
        .filter(ev => !ev.archived && ev.startTime >= gridStart && ev.startTime < gridEnd && (!activeCategoryFilter || normCat(ev.category) === activeCategoryFilter))
        .forEach(ev => {
            const colIdx   = numDays === 1 ? 0 : Math.min(numDays - 1, Math.max(0, Math.floor((ev.startTime - gridStart) / 86400000)));
            const startFrac = timeFrac(ev.startTime);
            const endFrac   = timeFrac(ev.endTime);
            placeBlock(ev, colIdx, startFrac, endFrac, false);
        });


    scheduledBlocks
        .filter(b => b.startTime >= gridStart && b.startTime < gridEnd)
        .forEach(b => {
            const colIdx    = numDays === 1 ? 0 : Math.min(numDays - 1, Math.max(0, Math.floor((b.startTime - gridStart) / 86400000)));
            const startFrac = timeFrac(b.startTime);
            const endFrac   = timeFrac(b.endTime);
            placeBlock(b, colIdx, startFrac, endFrac, true);
        });
}

function timeFrac(d) { return d.getHours() + d.getMinutes() / 60; }
function dayIndex(d) { return d.getDay() === 0 ? 6 : d.getDay() - 1; }

// ── Month View ─────────────────────────────────────────────────────────────
function renderMonthView() {
    const cal = document.getElementById('calendar');
    cal.innerHTML = '';
    const wrap = document.createElement('div');
    wrap.className = 'month-wrapper';

    // Day name header
    const hrow = document.createElement('div');
    hrow.className = 'month-header-row';
    ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].forEach(d => {
        hrow.innerHTML += `<div class="month-day-name">${d}</div>`;
    });
    wrap.appendChild(hrow);

    // Grid
    const grid = document.createElement('div');
    grid.className = 'month-grid';

    const year  = currentDate.getFullYear();
    const month = currentDate.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay  = new Date(year, month + 1, 0);
    const today    = new Date();

    // Start on Monday
    let startOffset = firstDay.getDay() === 0 ? 6 : firstDay.getDay() - 1;
    const gridStart = new Date(firstDay);
    gridStart.setDate(1 - startOffset);

    const totalCells = Math.ceil((startOffset + lastDay.getDate()) / 7) * 7;

    for (let i = 0; i < totalCells; i++) {
        const cellDate = new Date(gridStart);
        cellDate.setDate(gridStart.getDate() + i);
        const isToday      = cellDate.toDateString() === today.toDateString();
        const isOtherMonth = cellDate.getMonth() !== month;

        const cell = document.createElement('div');
        cell.className = `month-cell${isOtherMonth ? ' other-month' : ''}${isToday ? ' today-cell' : ''}`;

        const dateEl = document.createElement('div');
        dateEl.className = 'month-cell-date';
        dateEl.textContent = cellDate.getDate();
        dateEl.addEventListener('click', () => {
            currentDate = new Date(cellDate);
            currentView = 'day';
            document.querySelectorAll('.view-btn').forEach(b => b.classList.toggle('active', b.dataset.view === 'day'));
            refreshAll();
        });
        cell.appendChild(dateEl);

        // Items on this day
        const cellStart = new Date(cellDate); cellStart.setHours(0,0,0,0);
        const cellEnd   = new Date(cellDate); cellEnd.setHours(23,59,59,999);

        const dayEvents = events.filter(ev => !ev.archived && ev.startTime >= cellStart && ev.startTime <= cellEnd);
        const dayTasks  = scheduledBlocks.filter(b => b.startTime >= cellStart && b.startTime <= cellEnd);

        const allItems = [...dayEvents, ...dayTasks];
        const MAX_SHOW = 3;
        allItems.slice(0, MAX_SHOW).forEach(item => {
            const pill = document.createElement('div');
            const catCls = normCat(item.category);
            pill.className = `month-event-pill ${catCls}${item.type === 'task' ? ' task-pill' : ''}`;
            if (item.type === 'task' && item.completed) pill.classList.add('completed-task-pill');
            pill.textContent = item.name;

            let tooltip = item.name;
            if (item.type === 'scheduledBlock') {
                tooltip += `\nSession: ${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
                if (item.matchedTask) {
                    tooltip += `\nDue: ${item.matchedTask.dueDate.toLocaleDateString()} at ${fmtTime(item.matchedTask.dueDate)}`;
                    if (item.matchedTask.description) tooltip += `\nNotes: ${item.matchedTask.description}`;
                }
            } else if (item.type === 'task') {
                tooltip += `\nDue: ${item.dueDate.toLocaleDateString()} at ${fmtTime(item.dueDate)}`;
                if (item.description) tooltip += `\nNotes: ${item.description}`;
            } else {
                tooltip += `\nTime: ${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
                if (item.location) tooltip += `\nLocation: ${item.location}`;
            }
            pill.title = tooltip;

            pill.addEventListener('click', e => { e.stopPropagation(); showPopover(item, e); });
            cell.appendChild(pill);
        });
        if (allItems.length > MAX_SHOW) {
            const more = document.createElement('div');
            more.className = 'month-more';
            more.textContent = `+${allItems.length - MAX_SHOW} more`;
            cell.appendChild(more);
        }
        grid.appendChild(cell);
    }
    wrap.appendChild(grid);
    cal.appendChild(wrap);
}

// ══════════════════════════════════════════
// TASK / ARCHIVE PANEL
// ══════════════════════════════════════════
function renderTaskList(tab = 'tasks') {
    const el      = document.getElementById('task-list');
    const subhead = document.getElementById('task-list-subheader');
    el.innerHTML  = '';

    if (tab === 'archive') {
        subhead.textContent = 'Completed & Archived ↓';
        const archivedTasks  = tasks.filter(t  => t.archived);
        const archivedEvents = events.filter(ev => ev.archived);
        const all = [
            ...archivedTasks.map(t  => ({ item: t,  archivedAt: t.archivedAt })),
            ...archivedEvents.map(ev => ({ item: ev, archivedAt: ev.archivedAt }))
        ].sort((a, b) => (b.archivedAt || 0) - (a.archivedAt || 0));

        if (all.length === 0) {
            el.innerHTML = '<div class="empty-state">Nothing archived yet.<br>Complete or archive tasks to see them here.</div>';
            return;
        }
        all.forEach(({ item }) => renderItem(item, el, true));
        return;
    }

    if (tab === 'events') {
        subhead.textContent = 'Upcoming Events ↓';
        const activeEvents = events.filter(ev => !ev.archived).sort((a, b) => a.startTime - b.startTime);

        if (activeEvents.length === 0) {
            el.innerHTML = '<div class="empty-state">No upcoming events.<br>Add an event to see it here.</div>';
            return;
        }

        activeEvents.forEach(ev => renderItem(ev, el, false));
        return;
    }

    // Tasks tab
    subhead.textContent = 'Priority Score ↓';
    const activeTasks  = tasks.filter(t  => !t.archived).sort((a, b) => b.getPriorityScore() - a.getPriorityScore());

    if (activeTasks.length === 0) {
        el.innerHTML = `<div class="empty-state">${getAllClearMessage()}</div>`;
        return;
    }

    if (activeTasks.length > 0) {
        activeTasks.forEach(t => renderItem(t, el, false));
    }
}

function getAllClearMessage() {

    const messages = [
        'All clear!<br>Nothing left on the task list.',
        'Nice work!<br>You have zero tasks left right now.',
        'Yay!<br>The task list is empty.',
        'You are all done!<br>Everything is checked off for now.',
        'You did it!<br>Nothing pending at the moment.'
    ];

    let nextIndex = Math.floor(Math.random() * messages.length);
    if (messages.length > 1 && nextIndex === lastAllClearMessageIndex) {
        nextIndex = (nextIndex + 1) % messages.length;
    }

    lastAllClearMessageIndex = nextIndex;
    return messages[nextIndex];
}

function renderItem(item, container, isArchive) {
    const card = document.createElement('div');
    const cleanItemName = cleanStringValue(item.name, 'Untitled');
    let tooltip = cleanItemName;

    if (item.type === 'task') {
        const t = item;

        tooltip += `\nDue: ${t.dueDate.toLocaleDateString()} at ${fmtTime(t.dueDate)}`;
        tooltip += `\nEst. Time: ${t.estimatedTime}m`;
        tooltip += `\nMax Session: ${t.maxSessionLength === -1 ? 'No Breaks' : t.maxSessionLength + 'm'}`;
        if (t.description) tooltip += `\nNotes: ${cleanStringValue(t.description)}`;
        card.title = tooltip;

        const score = t.getPriorityScore();
        let scoreColor = 'var(--accent)';
        if (score === Infinity) scoreColor = 'var(--accent-red)';
        else if (score > 15)   scoreColor = 'var(--accent-orange)';

        card.className = `task-card ${t.isOverdue() ? 'overdue' : ''} ${t.completed ? 'completed' : ''}`;
        card.dataset.category = cleanStringValue(t.category, 'other');
        if (!t.isOverdue()) {
            card.style.borderLeft = `3px solid ${catColor(t.category)}`;
        }


        const chk = document.createElement('div');
        chk.className = `card-checkbox${t.completed ? ' checked' : ''}`;
        chk.addEventListener('click', e => {
            e.stopPropagation();
            t.completed = !t.completed;
            t.archived = t.completed;
            if (!t.completed) t.archivedAt = null;
            if (t.completed) t.archivedAt = Date.now();
            refreshAll();
        });

        card.innerHTML = `
            <div class="card-info">
                <div class="card-name">${escapeHtml(cleanItemName)}</div>
                <div class="card-meta">${getCategoryName(t.category)} · Due ${t.dueDate.toLocaleDateString('en-US', {weekday:'short',month:'short',day:'numeric'})}</div>

                <div class="card-detail">Priority ${t.userPriority} · Est ${t.estimatedTime}m · Max Session: ${t.maxSessionLength === -1 ? 'None' : t.maxSessionLength + 'm'} · ${t.getMinutesRemaining()}m left</div>
            </div>
            <div class="card-score" style="color:${scoreColor}">${score === Infinity ? '∞' : score === -1 ? '✓' : score.toFixed(1)}</div>
        `;
        card.insertBefore(chk, card.firstChild);
        card.addEventListener('click', e => { if (!e.target.classList.contains('card-checkbox')) showPopover(t, e); });

    } else {
        const ev = item;

        tooltip += `\nTime: ${fmtTime(ev.startTime)} – ${fmtTime(ev.endTime)}`;
        if (ev.location) tooltip += `\nLocation: ${cleanStringValue(ev.location)}`;
        card.title = tooltip;

        card.className = 'event-card';
        card.style.position = 'relative';
        card.style.paddingLeft = '14px';
        const bar = document.createElement('div');
        bar.className = 'event-color-bar';
        bar.style.background = catColor(ev.category);
        card.appendChild(bar);
        card.innerHTML += `
            <div class="card-info">
                <div class="card-name">${escapeHtml(cleanItemName)}</div>
                <div class="card-meta">${ev.startTime.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric'})} · ${fmtTime(ev.startTime)}–${fmtTime(ev.endTime)}</div>
                <div class="card-detail">${getCategoryName(ev.category)} · ${ev.status}${ev.reminderEnabled ? ` · Remind every ${ev.reminderEveryDays} day(s)` : ''}${ev.recurrence && ev.recurrence !== 'none' ? ` · Repeats ${ev.recurrence}` : ''}</div>

            </div>
        `;
        card.addEventListener('click', e => showPopover(ev, e));
    }
    container.appendChild(card);
}

// ══════════════════════════════════════════
// DETAIL POPOVER
// ══════════════════════════════════════════
function showPopover(item, e) {
    const pop     = document.getElementById('detail-popover');
    const dot     = document.getElementById('popover-dot');
    const title   = document.getElementById('popover-title');
    const body    = document.getElementById('popover-body');
    const footer  = document.getElementById('popover-footer');

    // Extract the parent task if this is a scheduled session block
    const isSession = item.type === 'scheduledBlock' && item.matchedTask;
    const targetItem = isSession ? item.matchedTask : item;

    const now = new Date();
    const isHappeningNow = isSession && (now >= item.startTime && now <= item.endTime);

    dot.style.background = catColor(targetItem.category);
    title.textContent    = cleanStringValue(item.name); // Keep the specific block name (e.g., Session 1)
    body.innerHTML       = '';
    footer.innerHTML     = '';

    const row = (icon, label, val) => {
        if (!val) return;
        body.innerHTML += `<div class="popover-row"><span class="popover-icon">${icon}</span><span>${label}: <span class="popover-val">${val}</span></span></div>`;
    };

    if (isSession) {
        row('🕐', 'Session Time', `${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`);

        if (isHappeningNow) {
            const btnReschedule = btn('btn-reschedule', 'Reschedule', () => {
                const pushEvent = new CalEvent("Busy / Rescheduled", new Date(), item.endTime, "", "FIXED", "other");
                events.push(pushEvent);
                pop.classList.add('hidden');
                refreshAll(true);
            });
            btnReschedule.style.backgroundColor = 'var(--accent-orange)';
            btnReschedule.style.color = '#fff';
            footer.appendChild(btnReschedule);
        }
    }

    if (targetItem.type === 'task') {
        const t = targetItem;
        row('📅', 'Due',        t.dueDate.toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric',year:'numeric'}));
        row('⏰', 'Due time',   fmtTime(t.dueDate));
        row('🏷', 'Category',   capFirst(t.category));
        row('⭐', 'Priority',   `${t.userPriority}/10`);
        row('⏱', 'Est. time',  `${t.estimatedTime} min`);
        row('⏳', 'Max Session', t.maxSessionLength === -1 ? 'No Breaks' : `${t.maxSessionLength} min`);
        row('📝', 'Notes',      cleanStringValue(t.description));
        row('📊', 'Score',      t.isOverdue() ? 'OVERDUE' : t.getPriorityScore().toFixed(2));

        if (!t.archived) {
            const btnComplete = btn('btn-complete', t.completed ? 'Mark Incomplete' : 'Mark Complete', () => {
                t.completed  = !t.completed;
                t.archived   = t.completed;
                t.archivedAt = t.completed ? Date.now() : null;
                pop.classList.add('hidden');
                refreshAll();
            });
            const btnArchive = btn('btn-archive', 'Archive', () => {
                t.archived   = true;
                t.archivedAt = Date.now();
                pop.classList.add('hidden');
                refreshAll();
            });
            const btnDel = btn('btn-delete', 'Delete', () => {
                tasks = tasks.filter(x => x.id !== t.id);
                pop.classList.add('hidden');
                refreshAll();
            });
            footer.appendChild(btnComplete);
            footer.appendChild(btnArchive);
            footer.appendChild(btnDel);
        } else {
            const btnRestore = btn('btn-archive', 'Restore', () => {
                t.archived   = false;
                t.completed  = false;
                t.archivedAt = null;
                pop.classList.add('hidden');
                refreshAll();
            });
            const btnDel = btn('btn-delete', 'Delete', () => {
                tasks = tasks.filter(x => x.id !== t.id);
                pop.classList.add('hidden');
                refreshAll();
            });
            footer.appendChild(btnRestore);
            footer.appendChild(btnDel);
        }
    } else {
        const ev = targetItem;
        row('📅', 'Date',     ev.startTime.toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric',year:'numeric'}));
        row('🕐', 'Time',     `${fmtTime(ev.startTime)} – ${fmtTime(ev.endTime)}`);
        row('⏱', 'Duration', `${ev.getDurationMins()} min`);
        row('📍', 'Location', cleanStringValue(ev.location));
        row('🏷', 'Category', getCategoryName(ev.category));

        row('📌', 'Status',   ev.status);
        row('🔔', 'Reminder', ev.reminderEnabled ? `Every ${ev.reminderEveryDays} day(s) before event` : 'Off');
        if (ev.recurrence && ev.recurrence !== 'none') {
            row('🔁', 'Repeat',   `${ev.recurrence.charAt(0).toUpperCase() + ev.recurrence.slice(1)}`);
        }

        if (!ev.archived) {
            const btnArc = btn('btn-archive', 'Archive', () => {
                ev.archived   = true;
                ev.archivedAt = Date.now();
                pop.classList.add('hidden');
                refreshAll();
            });
            const btnDel = btn('btn-delete', 'Delete', () => {
                if (ev.recurrenceId) {
                    const confirmAll = confirm('This is a recurring event.\n\nClick OK to delete ALL events in this recurring series.\nClick Cancel to delete ONLY this single event.');
                    if (confirmAll) {
                        events = events.filter(x => x.recurrenceId !== ev.recurrenceId);
                    } else {
                        events = events.filter(x => x.id !== ev.id);
                    }
                } else {
                    events = events.filter(x => x.id !== ev.id);
                }
                pop.classList.add('hidden');
                refreshAll(true);
            });
            footer.appendChild(btnArc);
            footer.appendChild(btnDel);
        } else {
            const btnRestore = btn('btn-archive', 'Restore', () => {
                ev.archived   = false;
                ev.archivedAt = null;
                pop.classList.add('hidden');
                refreshAll();
            });
            const btnDel = btn('btn-delete', 'Delete', () => {
                if (ev.recurrenceId) {
                    const confirmAll = confirm('This is a recurring event.\n\nClick OK to delete ALL events in this recurring series.\nClick Cancel to delete ONLY this single event.');
                    if (confirmAll) {
                        events = events.filter(x => x.recurrenceId !== ev.recurrenceId);
                    } else {
                        events = events.filter(x => x.id !== ev.id);
                    }
                } else {
                    events = events.filter(x => x.id !== ev.id);
                }
                pop.classList.add('hidden');
                refreshAll(true);
            });
            footer.appendChild(btnRestore);
            footer.appendChild(btnDel);
        }
    }

    // Position popover near click
    pop.classList.remove('hidden');

    const rect = pop.getBoundingClientRect();
    const popW = rect.width, popH = rect.height;
    let left = e.clientX + 12, top = e.clientY - 20;
    if (left + popW > window.innerWidth - 10)  left = e.clientX - popW - 12;
    if (top  + popH > window.innerHeight - 10) top  = window.innerHeight - popH - 10;
    if (left < 10) left = 10;
    if (top < 10) top = 10;
    pop.style.left = `${left}px`;
    pop.style.top  = `${top}px`;
}

function btn(cls, label, handler) {
    const b = document.createElement('button');
    b.className = `popover-action-btn ${cls}`;
    b.textContent = label;
    b.addEventListener('click', handler);
    return b;
}

// ── Demo Data ──────────────────────────────────────────────────────────────
function seedDemoData() {
    const mon = new Date();
    snapToMonday(mon);

    const d = (offset, h, m = 0) => {
        const x = new Date(mon);
        x.setDate(mon.getDate() + offset);
        x.setHours(h, m, 0, 0);
        return x;
    };

    tasks.push(new Task("Math Homework",       "School",          d(0, 17),  8, 90,  120, "Chapter 5 exercises"));
    tasks.push(new Task("Physics Lab Report",  "School",          d(2, 12),  6, 60,  120, "Include all graphs"));
    tasks.push(new Task("Team Presentation",   "Work",            d(3, 15),  9, 120, 120, "Slides + script"));
    tasks.push(new Task("Journal Entry",       "Personal",        d(1, 20),  4, 20,  120, ""));
    tasks.push(new Task("Overdue Assignment",  "School",          d(-1, 12), 8, 45,  120, "Submit on portal"));

    events.push(new CalEvent("School",          d(0,  8), d(0, 15), "Main Building",    "FIXED",    "School"));
    events.push(new CalEvent("School",          d(1,  8), d(1, 15), "Main Building",    "FIXED",    "School"));
    events.push(new CalEvent("School",          d(2,  8), d(2, 15), "Main Building",    "FIXED",    "School"));
    events.push(new CalEvent("School",          d(3,  8), d(3, 15), "Main Building",    "FIXED",    "School"));
    events.push(new CalEvent("School",          d(4,  8), d(4, 15), "Main Building",    "FIXED",    "School"));
    events.push(new CalEvent("Soccer Practice", d(1, 16, 30), d(1, 18), "Sports Field", "FIXED",    "Extracurricular"));
    events.push(new CalEvent("Club Meeting",    d(3, 15), d(3, 16),  "Room 204",        "OPTIONAL", "Extracurricular"));
    events.push(new CalEvent("Work Shift",      d(2, 16), d(2, 20),  "Office",          "FIXED",    "Work"));
}

function formatLocalISO(d) {
    if (!d) return null;
    const dateObj = (d instanceof Date) ? d : new Date(d);
    if (isNaN(dateObj.getTime())) return null;
    const pad = n => String(n).padStart(2, '0');
    return `${dateObj.getFullYear()}-${pad(dateObj.getMonth()+1)}-${pad(dateObj.getDate())}T${pad(dateObj.getHours())}:${pad(dateObj.getMinutes())}:00`;
}

function saveState(fetchSchedule = false) {
    try {
        const payload = {
            currentDate: currentDate.toISOString(),
            currentView,
            tasks: tasks.map(t => ({
                id: cleanIdValue(t.id, 'task'),
                name: cleanStringValue(t.name, 'Untitled Task'),
                category: cleanStringValue(t.category, 'other'),
                dueDate: formatLocalISO(t.dueDate),
                userPriority: t.userPriority,
                estimatedTime: t.estimatedTime,
                description: cleanStringValue(t.description, ''),
                completed: t.completed,
                archived: t.archived,
                minutesSpent: t.minutesSpent,
                archivedAt: t.archivedAt,
                maxSessionLength: t.maxSessionLength
            })),
            events: events.map(ev => ({
                id: cleanIdValue(ev.id, 'event'),
                name: cleanStringValue(ev.name, 'Untitled Event'),
                startTime: formatLocalISO(ev.startTime),
                endTime: formatLocalISO(ev.endTime),
                location: cleanStringValue(ev.location, ''),
                status: ev.status || 'FIXED',
                category: cleanStringValue(ev.category, 'other'),
                reminderEnabled: ev.reminderEnabled,
                reminderEveryDays: ev.reminderEveryDays,
                recurrence: ev.recurrence || 'none',
                recurrenceId: ev.recurrenceId || null,
                archived: ev.archived,
                archivedAt: ev.archivedAt
            }))
        };
        localStorage.setItem(getUserStorageKey(STORAGE_KEY), JSON.stringify(payload));
        queueCsvSync({
            userId: currentUser ? currentUser.userId : null,
            tasks: payload.tasks,
            events: payload.events
        }, fetchSchedule);
    } catch (err) {
        console.warn('Failed to save local state:', err);
    }
}

function shouldShowTaskOnCalendar(task) {
    return !task.archived || task.completed;
}

let pendingFetchSchedule = false;

function queueCsvSync(payload, fetchSchedule) {
    if (fetchSchedule) pendingFetchSchedule = true;
    if (csvSyncTimer) clearTimeout(csvSyncTimer);
    csvSyncTimer = setTimeout(() => {
        const doFetch = pendingFetchSchedule;
        pendingFetchSchedule = false;
        syncStateToCsv(payload, doFetch);
    }, 300);
}

async function syncStateToCsv(payload, fetchSchedule) {
    try {
        const response = await fetch(buildApiUrl('/api/state/save-csv'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            console.warn(`CSV sync failed (${response.status}). Local storage is still saved.`);
        } else {
            const data = await response.json();
            if (data && data.status === 'ok') {
                if (fetchSchedule && typeof fetchScheduledBlocks === 'function') {
                    fetchScheduledBlocks();
                }
            } else {
                console.error("Save state error from server:", data ? data.message : "Unknown error");
            }
        }
    } catch (error) {
        console.warn('CSV sync unavailable. Local storage is still saved.', error);
    }
}

function loadState() {
    try {
        const raw = localStorage.getItem(getUserStorageKey(STORAGE_KEY));
        if (!raw) return false;

        const parsed = JSON.parse(raw);
        if (!parsed || !Array.isArray(parsed.tasks) || !Array.isArray(parsed.events)) {
            return false;
        }

        tasks = parsed.tasks.map(t => {
            const task = new Task(
                cleanStringValue(t.name),
                cleanStringValue(t.category),
                t.dueDate,
                t.userPriority,
                t.estimatedTime,
                t.maxSessionLength,
                cleanStringValue(t.description),
                !!t.completed
            );
            task.id = cleanIdValue(t.id || task.id, 'task');
            task.archived = !!t.archived;
            task.minutesSpent = Number.isFinite(t.minutesSpent) ? t.minutesSpent : 0;
            task.archivedAt = t.archivedAt || null;
            return task;
        });

        events = parsed.events.map(ev => {
            const reminderEveryDays = Number.isFinite(ev.reminderEveryDays)
                ? ev.reminderEveryDays
                : (Number.isFinite(ev.reminderEveryMinutes)
                    ? Math.max(1, Math.round(ev.reminderEveryMinutes / (60 * 24)))
                    : 1);

            const event = new CalEvent(
                cleanStringValue(ev.name),
                ev.startTime,
                ev.endTime,
                cleanStringValue(ev.location),
                ev.status,
                cleanStringValue(ev.category),
                !!ev.reminderEnabled,
                reminderEveryDays,
                ev.recurrence || 'none',
                ev.recurrenceId || null
            );
            event.id = cleanIdValue(ev.id || event.id, 'event');
            event.archived = !!ev.archived;
            event.archivedAt = ev.archivedAt || null;
            return event;
        });

        if (['week', 'month', 'day'].includes(parsed.currentView)) {
            currentView = parsed.currentView;
            document.querySelectorAll('.view-btn').forEach(btn => {
                btn.classList.toggle('active', btn.dataset.view === currentView);
            });
        }

        return true;
    } catch (err) {
        console.warn('Failed to load local state; clearing invalid storage:', err);
        localStorage.removeItem(getUserStorageKey(STORAGE_KEY));
        return false;
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────
function fmtTime(d) {
    return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
}
function capFirst(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1) : ''; }
function normCat(cat) {
    if (!cat) return 'other';
    const c = cat.toLowerCase();
    if (c === 'extra') return 'extracurricular';
    return c;
}

function parseBackendDate(val) {
    if (!val) return new Date();
    if (Array.isArray(val)) {
        const [y, m, d, h = 0, min = 0, s = 0] = val;
        return new Date(y, m - 1, d, h, min, s);
    }
    return new Date(val);
}
