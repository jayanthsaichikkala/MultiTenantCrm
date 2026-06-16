/**
 * Shared notification bell for all dashboards.
 * Uses WebSockets (SockJS + STOMP) with HTTP polling fallback.
 */
(function () {
    'use strict';

    var POLL_MS = 10000;
    var pollTimer = null;
    var isOpen = false;
    var notifications = [];
    var unreadCount = 0;

    var stompClient = null;
    var wsConnected = false;
    var wsRetryCount = 0;
    var maxWsRetries = 5;
    var reconnectTimeout = null;

    function isAppPage() {
        var path = window.location.pathname;
        return path !== '/login'
            && path !== '/forgot-password'
            && path.indexOf('/reset-password') !== 0;
    }

    function ensureUi() {
        var topbarRight = document.querySelector('.topbar-right');
        if (!topbarRight) return null;

        topbarRight.querySelectorAll('.notif-btn').forEach(function (el) {
            if (!el.closest('#crmNotifWrap')) {
                el.remove();
            }
        });

        if (document.getElementById('crmNotifWrap')) return topbarRight;

        var wrap = document.createElement('div');
        wrap.id = 'crmNotifWrap';
        wrap.className = 'notif-wrap';
        wrap.innerHTML =
            '<button type="button" class="notif-btn" id="crmNotifBtn" aria-label="Notifications" title="Notifications">' +
                '<i data-lucide="bell"></i>' +
                '<span class="notif-badge" id="crmNotifBadge" style="display:none;">0</span>' +
            '</button>' +
            '<div class="notif-dropdown" id="crmNotifDropdown" style="display:none;">' +
                '<div class="notif-dropdown-header">' +
                    '<span>Notifications</span>' +
                    '<div class="notif-header-actions">' +
                        '<button type="button" class="notif-mark-all" id="crmNotifMarkAll">Mark all read</button>' +
                        '<button type="button" class="notif-clear-all" id="crmNotifClearAll">Clear all</button>' +
                    '</div>' +
                '</div>' +
                '<div class="notif-list" id="crmNotifList"></div>' +
            '</div>';

        var userBadge = topbarRight.querySelector('.user-badge');
        if (userBadge) {
            topbarRight.insertBefore(wrap, userBadge);
        } else {
            topbarRight.appendChild(wrap);
        }

        if (window.lucide) lucide.createIcons();

        document.getElementById('crmNotifBtn').addEventListener('click', toggleDropdown);
        document.getElementById('crmNotifMarkAll').addEventListener('click', markAllRead);
        document.getElementById('crmNotifClearAll').addEventListener('click', deleteAll);
        document.addEventListener('click', function (e) {
            if (!wrap.contains(e.target)) closeDropdown();
        });

        return topbarRight;
    }

    function typeIcon(type) {
        switch ((type || '').toUpperCase()) {
            case 'TASK': return 'check-square';
            case 'LEAVE': return 'file-text';
            case 'MEETING': return 'calendar-clock';
            case 'TEAM': return 'users';
            case 'PERFORMANCE': return 'bar-chart-2';
            case 'REPORT': return 'file-bar-chart';
            case 'HOLIDAY': return 'calendar';
            default: return 'bell';
        }
    }

    function timeAgo(iso) {
        if (!iso) return '';
        var d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        var sec = Math.floor((Date.now() - d.getTime()) / 1000);
        if (sec < 60) return 'Just now';
        if (sec < 3600) return Math.floor(sec / 60) + 'm ago';
        if (sec < 86400) return Math.floor(sec / 3600) + 'h ago';
        return Math.floor(sec / 86400) + 'd ago';
    }

    function updateBadge() {
        var badge = document.getElementById('crmNotifBadge');
        if (!badge) return;
        if (unreadCount > 0) {
            badge.style.display = 'flex';
            badge.textContent = unreadCount > 9 ? '9+' : String(unreadCount);
        } else {
            badge.style.display = 'none';
        }
    }

    function renderList() {
        var list = document.getElementById('crmNotifList');
        if (!list) return;

        if (!notifications.length) {
            list.innerHTML = '<div class="notif-empty">No notifications yet</div>';
            return;
        }

        list.innerHTML = notifications.map(function (n) {
            var cls = 'notif-item' + (n.read ? '' : ' unread');
            return '<div class="' + cls + '" data-id="' + n.id + '">' +
                '<button type="button" class="notif-item-main" data-id="' + n.id + '" data-link="' + escapeHtml(n.link || '') + '">' +
                    '<span class="notif-item-icon"><i data-lucide="' + typeIcon(n.type) + '"></i></span>' +
                    '<span class="notif-item-body">' +
                        '<strong>' + escapeHtml(n.title || '') + '</strong>' +
                        '<span>' + escapeHtml(n.message || '') + '</span>' +
                        '<em>' + timeAgo(n.createdAt) + '</em>' +
                    '</span>' +
                '</button>' +
                '<button type="button" class="notif-delete" data-id="' + n.id + '" title="Delete notification" aria-label="Delete notification">' +
                    '<i data-lucide="trash-2"></i>' +
                '</button>' +
            '</div>';
        }).join('');

        list.querySelectorAll('.notif-item-main').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var id = btn.getAttribute('data-id');
                var link = btn.getAttribute('data-link');
                markRead(id, function () {
                    if (link) window.location.href = link;
                });
            });
        });

        list.querySelectorAll('.notif-delete').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                deleteNotification(btn.getAttribute('data-id'));
            });
        });

        if (window.lucide) lucide.createIcons();
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function fetchNotifications(cb) {
        if (typeof window.crmFetch !== 'function') return;
        window.crmFetch('/api/notifications', { method: 'GET', contentType: null })
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(function (data) {
                notifications = data.notifications || [];
                unreadCount = data.unreadCount || 0;
                updateBadge();
                renderList();
                renderDashboardFeed();
                if (typeof cb === 'function') cb();
            })
            .catch(function (err) {
                console.warn('Notifications fetch failed:', err.message);
                if (typeof cb === 'function') cb();
            });
    }

    function markRead(id, cb) {
        window.crmFetch('/api/notifications/' + id + '/read', { method: 'POST', body: '{}' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                if (data) unreadCount = data.unreadCount || 0;
                notifications = notifications.map(function (n) {
                    if (String(n.id) === String(id)) n.read = true;
                    return n;
                });
                updateBadge();
                renderList();
                renderDashboardFeed();
                if (typeof cb === 'function') cb();
            });
    }

    function markAllRead(e) {
        e.stopPropagation();
        window.crmFetch('/api/notifications/read-all', { method: 'POST', body: '{}' })
            .then(function () {
                unreadCount = 0;
                notifications = notifications.map(function (n) { n.read = true; return n; });
                updateBadge();
                renderList();
                renderDashboardFeed();
            });
    }

    function deleteNotification(id) {
        window.crmFetch('/api/notifications/' + id, { method: 'DELETE', contentType: null })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                if (!data) return;
                notifications = notifications.filter(function (n) {
                    return String(n.id) !== String(id);
                });
                unreadCount = data.unreadCount || 0;
                updateBadge();
                renderList();
                renderDashboardFeed();
            });
    }

    function deleteAll(e) {
        e.stopPropagation();
        if (!notifications.length) return;
        if (!window.confirm('Delete all notifications?')) return;
        window.crmFetch('/api/notifications/clear-all', { method: 'DELETE', contentType: null })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function () {
                notifications = [];
                unreadCount = 0;
                updateBadge();
                renderList();
                renderDashboardFeed();
            });
    }

    function toggleDropdown(e) {
        e.stopPropagation();
        var dd = document.getElementById('crmNotifDropdown');
        if (!dd) return;
        isOpen = !isOpen;
        dd.style.display = isOpen ? 'block' : 'none';
        if (isOpen) fetchNotifications();
    }

    function closeDropdown() {
        var dd = document.getElementById('crmNotifDropdown');
        if (dd) dd.style.display = 'none';
        isOpen = false;
    }

    function renderDashboardFeed() {
        var feed = document.getElementById('dashboardNotifFeed');
        if (!feed) return;
        if (!notifications.length) {
            feed.innerHTML = '<div class="notif-feed-empty"><i data-lucide="bell-off"></i><p>No notifications yet</p></div>';
        } else {
            feed.innerHTML = notifications.slice(0, 8).map(function (n) {
                return '<div class="notif-feed-item' + (n.read ? '' : ' unread') + '">' +
                    '<a class="notif-feed-link" href="' + escapeHtml(n.link || '#') + '">' +
                        '<span class="notif-feed-icon"><i data-lucide="' + typeIcon(n.type) + '"></i></span>' +
                        '<span><strong>' + escapeHtml(n.title || '') + '</strong>' +
                        '<span>' + escapeHtml(n.message || '') + '</span></span>' +
                        '<em>' + timeAgo(n.createdAt) + '</em>' +
                    '</a>' +
                    '<button type="button" class="notif-feed-delete" data-id="' + n.id + '" title="Delete" aria-label="Delete notification">' +
                        '<i data-lucide="trash-2"></i>' +
                    '</button>' +
                '</div>';
            }).join('');
            feed.querySelectorAll('.notif-feed-delete').forEach(function (btn) {
                btn.addEventListener('click', function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    deleteNotification(btn.getAttribute('data-id'));
                });
            });
        }
        if (window.lucide) lucide.createIcons();
    }

    function startPolling() {
        if (pollTimer) clearInterval(pollTimer);
        pollTimer = setInterval(fetchNotifications, POLL_MS);
    }

    /* WebSocket integration with Fallback */

    function loadScript(src, callback) {
        var s = document.createElement('script');
        s.src = src;
        s.onload = callback;
        s.onerror = function () {
            console.warn('Failed to load script:', src);
            if (typeof callback === 'function') callback(new Error('Load failed'));
        };
        document.head.appendChild(s);
    }

    function loadLibrariesAndConnect(userId) {
        var sockJsUrl = 'https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js';
        var stompUrl = 'https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js';

        function checkAndConnect() {
            if (window.SockJS && window.Stomp) {
                connectWebSocket(userId);
            } else {
                console.warn('WebSocket libraries loaded incorrectly. Falling back to HTTP polling.');
                startPolling();
            }
        }

        if (!window.SockJS) {
            loadScript(sockJsUrl, function (err) {
                if (err) {
                    startPolling();
                    return;
                }
                if (!window.Stomp) {
                    loadScript(stompUrl, function (err2) {
                        if (err2) {
                            startPolling();
                            return;
                        }
                        checkAndConnect();
                    });
                } else {
                    checkAndConnect();
                }
            });
        } else if (!window.Stomp) {
            loadScript(stompUrl, function (err) {
                if (err) {
                    startPolling();
                    return;
                }
                checkAndConnect();
            });
        } else {
            checkAndConnect();
        }
    }

    function connectWebSocket(userId) {
        if (wsConnected) return;

        try {
            var socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);
            stompClient.debug = null; // Suppress debug logging in console

            stompClient.connect({}, function () {
                wsConnected = true;
                wsRetryCount = 0;
                
                // If we were polling, clear the interval
                if (pollTimer) {
                    clearInterval(pollTimer);
                    pollTimer = null;
                }

                stompClient.subscribe('/topic/notifications/' + userId, function (msg) {
                    try {
                        var n = JSON.parse(msg.body);
                        
                        // Prevent duplicates
                        if (notifications.some(function(item) { return item.id === n.id; })) {
                            return;
                        }

                        notifications.unshift(n);
                        if (notifications.length > 50) {
                            notifications = notifications.slice(0, 50);
                        }
                        if (!n.read) {
                            unreadCount++;
                        }

                        updateBadge();
                        renderList();
                        renderDashboardFeed();
                        playNotificationSound();
                        showToast(n);
                        refreshPageContentIfRelevant(n.type);
                    } catch (e) {
                        console.error('Error handling WebSocket message:', e);
                    }
                });
            }, function (err) {
                console.warn('WebSocket STOMP error, attempting reconnect:', err);
                handleDisconnect(userId);
            });
        } catch (e) {
            console.warn('Failed to build SockJS/STOMP client:', e);
            handleDisconnect(userId);
        }
    }

    function handleDisconnect(userId) {
        wsConnected = false;
        if (stompClient) {
            try { stompClient.disconnect(); } catch (e) {}
            stompClient = null;
        }

        if (wsRetryCount < maxWsRetries) {
            var delay = Math.min(30000, Math.pow(2, wsRetryCount) * 2000);
            wsRetryCount++;
            console.log('Reconnecting WebSocket in ' + (delay / 1000) + 's (attempt ' + wsRetryCount + '/' + maxWsRetries + ')...');
            
            if (reconnectTimeout) clearTimeout(reconnectTimeout);
            reconnectTimeout = setTimeout(function () {
                connectWebSocket(userId);
            }, delay);
        } else {
            console.warn('WebSocket connection retries exhausted. Falling back to HTTP polling.');
            startPolling();
        }
    }

    /* Browser dynamic synthesized sound chime using Web Audio API */
    function playNotificationSound() {
        try {
            var AudioContext = window.AudioContext || window.webkitAudioContext;
            if (!AudioContext) return;
            var ctx = new AudioContext();

            var osc1 = ctx.createOscillator();
            var osc2 = ctx.createOscillator();
            var gain = ctx.createGain();

            osc1.type = 'sine';
            osc1.frequency.setValueAtTime(523.25, ctx.currentTime); // C5
            osc1.frequency.exponentialRampToValueAtTime(880.00, ctx.currentTime + 0.08); // A5

            osc2.type = 'sine';
            osc2.frequency.setValueAtTime(659.25, ctx.currentTime); // E5
            osc2.frequency.exponentialRampToValueAtTime(1046.50, ctx.currentTime + 0.12); // C6

            gain.gain.setValueAtTime(0.1, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);

            osc1.connect(gain);
            osc2.connect(gain);
            gain.connect(ctx.destination);

            osc1.start();
            osc2.start();

            osc1.stop(ctx.currentTime + 0.4);
            osc2.stop(ctx.currentTime + 0.4);
        } catch (e) {
            console.warn('Could not play synthesized notification sound:', e);
        }
    }

    function shouldReloadPage(type) {
        var path = window.location.pathname.toLowerCase();
        var t = (type || '').toUpperCase();

        if (path.indexOf('/dashboard') !== -1) {
            return true;
        }

        if (t === 'TASK' && path.indexOf('/tasks') !== -1) {
            return true;
        }
        if (t === 'MEETING' && (path.indexOf('/meetings') !== -1 || path.indexOf('/schedule-meeting') !== -1 || path.indexOf('/calendar') !== -1)) {
            return true;
        }
        if (t === 'LEAVE' && (path.indexOf('/leaves') !== -1 || path.indexOf('/leave') !== -1)) {
            return true;
        }
        if (t === 'TEAM' && (path.indexOf('/team') !== -1 || path.indexOf('/teams') !== -1)) {
            return true;
        }
        if (t === 'HOLIDAY' && path.indexOf('/calendar') !== -1) {
            return true;
        }
        if (t === 'REPORT' && path.indexOf('/reports') !== -1) {
            return true;
        }
        if (t === 'PERFORMANCE' && path.indexOf('/performance') !== -1) {
            return true;
        }

        return false;
    }

    function refreshPageContentIfRelevant(type) {
        if (!shouldReloadPage(type)) return;

        var pageContent = document.querySelector('.page-content');
        if (!pageContent) {
            window.location.reload();
            return;
        }

        var activeEl = document.activeElement;
        if (activeEl) {
            var isInsideReplacedContainer = false;
            var selectorsToCheck = [
                '#lhList',
                '#lhEmpty',
                '#taskTbody',
                '#taskTable',
                '#meetingList',
                '#employeeTbody',
                '#attendanceTbody',
                '#teamList',
                '#dashboardNotifFeed',
                '.stats-row',
                '.tasks-scroll'
            ];
            selectorsToCheck.forEach(function (sel) {
                var container = document.querySelector(sel);
                if (container && container.contains(activeEl)) {
                    isInsideReplacedContainer = true;
                }
            });
            if (isInsideReplacedContainer) {
                console.log('User is interacting with elements inside the replaced container, skipping AJAX refresh.');
                return;
            }
        }

        var modalOpen = false;
        document.querySelectorAll('[id*="modal"], [class*="modal"], [id*="overlay"], [class*="overlay"]').forEach(function(el) {
            var id = (el.id || '').toLowerCase();
            var cls = (el.className || '').toLowerCase();
            
            // Ignore sidebar, notification, hamburger, or menu elements
            if (id.indexOf('sidebar') !== -1 || cls.indexOf('sidebar') !== -1 ||
                id.indexOf('notif') !== -1 || cls.indexOf('notif') !== -1 ||
                id.indexOf('hamburger') !== -1 || cls.indexOf('hamburger') !== -1 ||
                id.indexOf('menu') !== -1 || cls.indexOf('menu') !== -1) {
                return;
            }

            var style = window.getComputedStyle(el);
            if (style.display !== 'none' && style.visibility !== 'hidden' && el.offsetWidth > 0 && el.offsetHeight > 0) {
                if (style.opacity !== '0') {
                    modalOpen = true;
                }
            }
        });
        if (modalOpen) {
            console.log('Modal/Overlay is currently open, skipping AJAX refresh.');
            return;
        }

        console.log('Refreshing page content via AJAX for type ' + type + '...');
        fetch(window.location.href)
            .then(function (response) {
                if (!response.ok) throw new Error('HTTP ' + response.status);
                return response.text();
            })
            .then(function (htmlText) {
                var parser = new DOMParser();
                var doc = parser.parseFromString(htmlText, 'text/html');
                
                var selectors = [
                    '#lhList',
                    '#lhEmpty',
                    '#taskTbody',
                    '#taskTable',
                    '#meetingList',
                    '#employeeTbody',
                    '#attendanceTbody',
                    '#teamList',
                    '#dashboardNotifFeed',
                    '.stats-row',
                    '.tasks-scroll'
                ];

                var updatedAny = false;
                selectors.forEach(function (selector) {
                    var currentEl = document.querySelector(selector);
                    var newEl = doc.querySelector(selector);
                    if (currentEl && newEl) {
                        currentEl.innerHTML = newEl.innerHTML;
                        updatedAny = true;
                    }
                });

                if (updatedAny) {
                    if (window.lucide) {
                        lucide.createIcons();
                    }

                    // Run page-specific callbacks/post-refresh adjustments
                    var path = window.location.pathname.toLowerCase();
                    if (path.indexOf('/leaves') !== -1) {
                        if (typeof updateStats === 'function') updateStats();
                        if (typeof applyFilters === 'function') applyFilters();
                        document.querySelectorAll('#lhList .leave-item').forEach(function (el) {
                            var s = el.dataset.status;
                            if (s === 'Approved' || s === 'Rejected') {
                                el.querySelectorAll('.btn-approve, .btn-reject').forEach(function (b) {
                                    b.style.display = 'none';
                                });
                            }
                        });
                    }
                    if (path.indexOf('/tasks') !== -1) {
                        if (typeof filterMyTasks === 'function') filterMyTasks();
                    }

                    console.log('Page content elements updated dynamically.');
                } else {
                    console.log('No specific container selectors found, falling back to full reload...');
                    window.location.reload();
                }
            })
            .catch(function (error) {
                console.warn('AJAX page refresh failed, falling back to full reload:', error);
                window.location.reload();
            });
    }

    /* Dynamic toast popup widget */
    function getToastContainer() {
        var container = document.getElementById('crmToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'crmToastContainer';
            container.className = 'notif-toast-container';
            document.body.appendChild(container);
        }
        return container;
    }

    function showToast(n) {
        var container = getToastContainer();
        var toast = document.createElement('div');
        toast.className = 'notif-toast';
        toast.innerHTML =
            '<div class="notif-toast-icon">' +
                '<i data-lucide="' + typeIcon(n.type) + '"></i>' +
            '</div>' +
            '<div class="notif-toast-content">' +
                '<span class="notif-toast-title">' + escapeHtml(n.title || '') + '</span>' +
                '<span class="notif-toast-message">' + escapeHtml(n.message || '') + '</span>' +
            '</div>' +
            '<button type="button" class="notif-toast-close" aria-label="Close">' +
                '<i data-lucide="x"></i>' +
            '</button>';

        container.appendChild(toast);
        if (window.lucide) lucide.createIcons();

        // Trigger animation
        setTimeout(function () {
            toast.classList.add('show');
        }, 10);

        // Click redirects to the link if present
        toast.addEventListener('click', function (e) {
            if (e.target.closest('.notif-toast-close')) return;
            if (n.link) {
                window.location.href = n.link;
            }
        });

        // Close button functionality
        toast.querySelector('.notif-toast-close').addEventListener('click', function (e) {
            e.stopPropagation();
            hideToast(toast);
        });

        // Auto dismiss after 5s
        setTimeout(function () {
            hideToast(toast);
        }, 5000);
    }

    function hideToast(toast) {
        toast.classList.remove('show');
        setTimeout(function () {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 400);
    }

    function init(attempt) {
        if (!isAppPage()) return;

        if (typeof window.crmFetch !== 'function' || !localStorage.getItem('jwt_token')) {
            if (attempt < 30) {
                setTimeout(function () { init(attempt + 1); }, 100);
            }
            return;
        }

        ensureUi();

        // Fetch initial list of notifications
        fetchNotifications(function () {
            // Once initial data loads, try connecting to WebSocket
            window.crmFetch('/api/notifications/me', { method: 'GET', contentType: null })
                .then(function (r) { return r.ok ? r.json() : null; })
                .then(function (user) {
                    if (user && user.id) {
                        loadLibrariesAndConnect(user.id);
                    } else {
                        console.warn('Could not read user info. Falling back to HTTP polling.');
                        startPolling();
                    }
                })
                .catch(function (err) {
                    console.warn('User details fetch failed. Falling back to HTTP polling:', err.message);
                    startPolling();
                });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () { init(0); });
    } else {
        init(0);
    }
})();
