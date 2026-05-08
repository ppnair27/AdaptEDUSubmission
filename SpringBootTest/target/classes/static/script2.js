// AdaptEDU – Rebuilt Script
// Features: week/month/day views · click-to-open detail popover
//           archive (manual archive via popover)
//           distinct category colors · Apple Calendar UX

class Task {
    constructor(name, category, dueDate, userPriority, estimatedTime, maxSessionLength = 120, description = '', completed = false) {
        this.id = `task_${Date.now()}_${Math.random().toString(36).slice(2)}`;
        this.type = 'task';
        this.name = name;
        this.category = (category || 'other').toLowerCase();
        this.dueDate = new Date(dueDate);
        this.userPriority = parseInt(userPriority) || 5;
        this.estimatedTime = parseInt(estimatedTime) || 60;
        this.maxSessionLength = parseInt(maxSessionLength, 10);
        if (isNaN(this.maxSessionLength)) this.maxSessionLength = 120;
        this.description = description;
        this.completed = completed;
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
    constructor(name, startTime, endTime, location, status, category, reminderEnabled = false, reminderEveryDays = 1) {
        this.id = `event_${Date.now()}_${Math.random().toString(36).slice(2)}`;
        this.type = 'event';
        this.name = name;
        this.startTime = new Date(startTime);
        this.endTime = new Date(endTime);
        this.location = location || '';
        this.status = status || 'FIXED';
        this.category = (category || 'other').toLowerCase();
        this.reminderEnabled = reminderEnabled;
        this.reminderEveryDays = parseInt(reminderEveryDays, 10) || 1;
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
const STORAGE_KEY = 'adaptedu.calendar.state.v1';
let csvSyncTimer = null;
let lastAllClearMessageIndex = -1;
let sessionCheckInterval = null;

// ── Pomodoro State ──
let pomoInterval = null;
let pomoTimeLeft = 25 * 60;
let pomoIsWorking = true;
let pomoIsRunning = false;
let pomoWorkDuration = 25;
let pomoBreakDuration = 5;
let currentPomoBlock = null;

// ── Category colors (must match CSS) ──────────────────────────────────────
const CAT_COLORS = {
    school:          '#82b1ff',
    work:            '#a5d6a7',
    personal:        '#ce93d8',
    extracurricular: '#ffb74d',
    extra:           '#ffb74d',
    other:           '#b0bec5',
};
function catColor(cat) { return CAT_COLORS[cat] || CAT_COLORS.other; }

// ── Init ───────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    const savedSettings = JSON.parse(localStorage.getItem('adaptedu.settings') || '{}');
    if (savedSettings.theme) globalTheme = savedSettings.theme;
    if (savedSettings.startHour !== undefined) START_HOUR = parseInt(savedSettings.startHour, 10);
    if (savedSettings.endHour !== undefined) END_HOUR = parseInt(savedSettings.endHour, 10);
    
    setGlobalTheme(globalTheme);

    setupListeners();
    const loaded = loadState();
    if (!loaded) {
        seedDemoData();
        saveState();
    }
    // Always force calendar to open to the current actual date on load
    currentDate = new Date();
    currentDate.setHours(0, 0, 0, 0);

    // Auto-switch to Day view on mobile for better UX
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
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/service-worker.js')
            .then(reg => console.log('Service Worker registered', reg))
            .catch(err => console.warn('Service Worker registration failed', err));
    }
});

function snapToMonday(d) {
    const day = d.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diff);
    d.setHours(0, 0, 0, 0);
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
            localStorage.setItem('adaptedu.pomoTheme', color);
        });
    });
    const savedPomoTheme = localStorage.getItem('adaptedu.pomoTheme') || 'black';
    setPomodoroTheme(savedPomoTheme);

    // Settings Menu
    const settingsModal = document.getElementById('settings-modal');
    document.getElementById('settings-btn').addEventListener('click', () => {
        document.getElementById('settings-wake').value = `${String(START_HOUR).padStart(2, '0')}:00`;
        document.getElementById('settings-sleep').value = `${String(END_HOUR).padStart(2, '0')}:00`;
        
        document.querySelectorAll('.global-color-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.color === globalTheme);
        });
        settingsModal.classList.remove('hidden');
    });
    
    ['close-settings-modal', 'cancel-settings-btn'].forEach(id => {
        document.getElementById(id).addEventListener('click', () => settingsModal.classList.add('hidden'));
    });
    
    let tempTheme = globalTheme;
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
        
        localStorage.setItem('adaptedu.settings', JSON.stringify({ theme: globalTheme, startHour: START_HOUR, endHour: END_HOUR }));
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

    const updateEventReminderVisibility = () => {
        if (!eventReminderSelect || !eventReminderDaysGroup) return;
        const shouldShow = eventReminderSelect.value === 'yes';
        eventReminderDaysGroup.classList.toggle('hidden', !shouldShow);
    };

    document.getElementById('add-task-btn').addEventListener('click', () => {
        taskModal.classList.remove('hidden');
    });
    document.getElementById('add-event-btn').addEventListener('click', () => {
        updateEventReminderVisibility();
        eventModal.classList.remove('hidden');
    });

    if (eventReminderSelect) {
        eventReminderSelect.addEventListener('change', updateEventReminderVisibility);
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
    }));

    // Form: add task
    document.getElementById('task-form').addEventListener('submit', e => {
        e.preventDefault();
        const f = e.target;

        const rawEstimatedMinutes = parseInt(f['task-estimated-time'].value, 10) || 0;
        const maxSessionLength = parseInt(f['task-max-session-length'].value, 10) || 120;

        tasks.push(new Task(
            f['task-name'].value,
            f['task-category'].value,
            f['task-due-date'].value,
            f['task-priority'].value,
            rawEstimatedMinutes,
            maxSessionLength,
            f['task-description'].value
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

        events.push(new CalEvent(
            f['event-name'].value,
            startTimeStr,
            endTimeStr,
            f['event-location'].value,
            f['event-status'].value,
            f['event-category'].value,
            f['event-reminder-enabled'].value === 'yes',
            f['event-reminder-every-days'].value
        ));
        
        eventModal.classList.add('hidden');
        f.reset();
        f['event-reminder-enabled'].value = 'no';
        f['event-reminder-every-days'].value = '1';
        updateEventReminderVisibility();
        refreshAll(true);
    });

    updateEventReminderVisibility();

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
        const response = await fetch(buildApiUrl(`/api/schedule?startHour=${START_HOUR}&endHour=${END_HOUR}&t=${Date.now()}`), {
            cache: 'no-store'
        });
        if (!response.ok) return;
        
        const scheduleData = await response.json();
        console.log("Raw Backend Schedule Data:", scheduleData);
        
        // Filter out the algorithm's split blocks and format them for the UI
        scheduledBlocks = scheduleData
            .filter(item => item.status === 'SCHEDULED_TASK')
            .map(item => {
                // Match with original task to inherit its completion status and category
                const baseTaskName = item.name.replace(/\s*\(Session \d+\)$/, '');
                const matchedTask = tasks.find(t => t.name === baseTaskName);
                
                const parsedStart = parseBackendDate(item.startTime);
                const parsedEnd = parseBackendDate(item.endTime);
                
                console.log(`Task Block '${item.name}' was placed on the calendar for:`, parsedStart.toLocaleString());

                return {
                    type: 'scheduledBlock',
                    name: item.name, // The backend already formats this as "[Task Name] (Session X)"
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
    document.getElementById('stat-pending').textContent = active.filter(t => !t.completed).length;
    document.getElementById('stat-overdue').textContent = active.filter(t => t.isOverdue()).length;
    document.getElementById('stat-events').textContent  = events.filter(e => !e.archived).length;

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
        let blockName = item.name;
        if (catCls === 'break') {
            blockName = blockName.replace(/\s*\(Session \d+\)$/, '');
        }
        
        block.innerHTML = `<div class="block-title">${blockName}</div><div class="block-time">${timeStr}</div>`;
        
        let tooltip = item.name;
        if (item.type === 'scheduledBlock') {
            tooltip += `\nSession: ${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
            if (item.matchedTask && catCls !== 'break') {
                tooltip += `\nDue: ${item.matchedTask.dueDate.toLocaleDateString()} at ${fmtTime(item.matchedTask.dueDate)}`;
                if (item.matchedTask.description) tooltip += `\nNotes: ${item.matchedTask.description}`;
            }
        } else if (item.type === 'task') {
            tooltip += `\nDue: ${item.dueDate.toLocaleDateString()} at ${fmtTime(item.dueDate)}`;
            tooltip += `\nEst. Time: ${item.estimatedTime}m`;
            if (item.description) tooltip += `\nNotes: ${item.description}`;
        } else {
            tooltip += `\nTime: ${fmtTime(item.startTime)} – ${fmtTime(item.endTime)}`;
            if (item.location) tooltip += `\nLocation: ${item.location}`;
        }
        block.title = tooltip;
        
        block.addEventListener('click', e => { e.stopPropagation(); showPopover(item, e); });
        col.appendChild(block);
    };

    events.filter(ev => !ev.archived && ev.startTime >= gridStart && ev.startTime < gridEnd).forEach(ev => {
        const colIdx   = numDays === 1 ? 0 : dayIndex(ev.startTime);
        const startFrac = timeFrac(ev.startTime);
        const endFrac   = timeFrac(ev.endTime);
        placeBlock(ev, colIdx, startFrac, endFrac, false);
    });

    scheduledBlocks
        .filter(b => b.startTime >= gridStart && b.startTime < gridEnd)
        .forEach(b => {
            const colIdx    = numDays === 1 ? 0 : dayIndex(b.startTime);
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
    let tooltip = item.name;
    
    if (item.type === 'task') {
        const t = item;
        
        tooltip += `\nDue: ${t.dueDate.toLocaleDateString()} at ${fmtTime(t.dueDate)}`;
        tooltip += `\nEst. Time: ${t.estimatedTime}m`;
        tooltip += `\nMax Session: ${t.maxSessionLength === -1 ? 'No Breaks' : t.maxSessionLength + 'm'}`;
        if (t.description) tooltip += `\nNotes: ${t.description}`;
        card.title = tooltip;
        
        const score = t.getPriorityScore();
        let scoreColor = 'var(--accent)';
        if (score === Infinity) scoreColor = 'var(--accent-red)';
        else if (score > 15)   scoreColor = 'var(--accent-orange)';

        card.className = `task-card ${t.isOverdue() ? 'overdue' : ''} ${t.completed ? 'completed' : ''}`;
        card.dataset.category = t.category;

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
                <div class="card-name">${t.name}</div>
                <div class="card-meta">${capFirst(t.category)} · Due ${t.dueDate.toLocaleDateString('en-US', {weekday:'short',month:'short',day:'numeric'})}</div>
                <div class="card-detail">Priority ${t.userPriority} · Est ${t.estimatedTime}m · Max Session: ${t.maxSessionLength === -1 ? 'None' : t.maxSessionLength + 'm'} · ${t.getMinutesRemaining()}m left</div>
            </div>
            <div class="card-score" style="color:${scoreColor}">${score === Infinity ? '∞' : score === -1 ? '✓' : score.toFixed(1)}</div>
        `;
        card.insertBefore(chk, card.firstChild);
        card.addEventListener('click', e => { if (!e.target.classList.contains('card-checkbox')) showPopover(t, e); });

    } else {
        const ev = item;
        
        tooltip += `\nTime: ${fmtTime(ev.startTime)} – ${fmtTime(ev.endTime)}`;
        if (ev.location) tooltip += `\nLocation: ${ev.location}`;
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
                <div class="card-name">${ev.name}</div>
                <div class="card-meta">${ev.startTime.toLocaleDateString('en-US',{weekday:'short',month:'short',day:'numeric'})} · ${fmtTime(ev.startTime)}–${fmtTime(ev.endTime)}</div>
                <div class="card-detail">${capFirst(ev.category)} · ${ev.status}${ev.reminderEnabled ? ` · Remind every ${ev.reminderEveryDays} day(s)` : ''}</div>
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
    title.textContent    = item.name; // Keep the specific block name (e.g., Session 1)
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
        row('📝', 'Notes',      t.description);
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
        row('📍', 'Location', ev.location);
        row('🏷', 'Category', capFirst(ev.category));
        row('📌', 'Status',   ev.status);
        row('🔔', 'Reminder', ev.reminderEnabled ? `Every ${ev.reminderEveryDays} day(s) before event` : 'Off');

        if (!ev.archived) {
            const btnArc = btn('btn-archive', 'Archive', () => {
                ev.archived   = true;
                ev.archivedAt = Date.now();
                pop.classList.add('hidden');
                refreshAll();
            });
            const btnDel = btn('btn-delete', 'Delete', () => {
                events = events.filter(x => x.id !== ev.id);
                pop.classList.add('hidden');
                refreshAll();
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
                events = events.filter(x => x.id !== ev.id);
                pop.classList.add('hidden');
                refreshAll();
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
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
}

function saveState(fetchSchedule = false) {
    try {
        const payload = {
            currentDate: currentDate.toISOString(),
            currentView,
            tasks: tasks.map(t => ({
                id: t.id,
                name: t.name,
                category: t.category,
                dueDate: formatLocalISO(t.dueDate),
                userPriority: t.userPriority,
                estimatedTime: t.estimatedTime,
                description: t.description,
                completed: t.completed,
                archived: t.archived,
                minutesSpent: t.minutesSpent,
                archivedAt: t.archivedAt,
                maxSessionLength: t.maxSessionLength
            })),
            events: events.map(ev => ({
                id: ev.id,
                name: ev.name,
                startTime: formatLocalISO(ev.startTime),
                endTime: formatLocalISO(ev.endTime),
                location: ev.location,
                status: ev.status,
                category: ev.category,
                reminderEnabled: ev.reminderEnabled,
                reminderEveryDays: ev.reminderEveryDays,
                archived: ev.archived,
                archivedAt: ev.archivedAt
            }))
        };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
        queueCsvSync({ tasks: payload.tasks, events: payload.events }, fetchSchedule);
    } catch (err) {
        console.warn('Failed to save local state:', err);
    }
}

function shouldShowTaskOnCalendar(task) {
    return !task.archived || task.completed;
}

function queueCsvSync(payload, fetchSchedule) {
    if (csvSyncTimer) clearTimeout(csvSyncTimer);
    csvSyncTimer = setTimeout(() => {
        syncStateToCsv(payload, fetchSchedule);
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
            if (fetchSchedule && typeof fetchScheduledBlocks === 'function') {
                fetchScheduledBlocks();
            }
        }
    } catch (error) {
        console.warn('CSV sync unavailable. Local storage is still saved.', error);
    }
}

function loadState() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) return false;

        const parsed = JSON.parse(raw);
        if (!parsed || !Array.isArray(parsed.tasks) || !Array.isArray(parsed.events)) {
            return false;
        }

        tasks = parsed.tasks.map(t => {
            const task = new Task(
                t.name,
                t.category,
                t.dueDate,
                t.userPriority,
                t.estimatedTime,
                t.maxSessionLength,
                t.description,
                !!t.completed
            );
            task.id = t.id || task.id;
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
                ev.name,
                ev.startTime,
                ev.endTime,
                ev.location,
                ev.status,
                ev.category,
                !!ev.reminderEnabled,
                reminderEveryDays
            );
            event.id = ev.id || event.id;
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
        localStorage.removeItem(STORAGE_KEY);
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
