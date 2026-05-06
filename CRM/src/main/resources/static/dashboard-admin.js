/* dashboard-admin.js – CRM Portal Admin Dashboard */

document.addEventListener('DOMContentLoaded', () => {

    // ── Sidebar mobile toggle ──────────────────────────────────────────────────
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    const hamburger = document.getElementById('hamburger');

    if (hamburger && sidebar && overlay) {
        hamburger.addEventListener('click', () => {
            sidebar.classList.toggle('open');
            overlay.classList.toggle('show');
        });
        overlay.addEventListener('click', () => {
            sidebar.classList.remove('open');
            overlay.classList.remove('show');
        });
    }

    // ── Mark active nav item based on current URL ──────────────────────────────
    // NOTE: Active state is handled server-side via Thymeleaf th:classappend in
    // the layout fragment. No JS needed here — removing avoids double-highlight.

    // ── Auto-dismiss toast notifications ──────────────────────────────────────
    document.querySelectorAll('.toast').forEach(t => {
        setTimeout(() => {
            t.style.transition = 'opacity .5s, transform .5s';
            t.style.opacity = '0';
            t.style.transform = 'translateY(-8px)';
            setTimeout(() => t.remove(), 500);
        }, 3500);
    });

    // ── KPI bar animation on load ──────────────────────────────────────────────
    const animateBars = (selector, delay = 0) => {
        document.querySelectorAll(selector).forEach(bar => {
            const target = bar.style.width;
            bar.style.width = '0';
            setTimeout(() => {
                requestAnimationFrame(() => {
                    bar.style.transition = 'width 1s cubic-bezier(.4,0,.2,1)';
                    bar.style.width = target;
                });
            }, delay);
        });
    };

    animateBars('.kpi-bar > div', 100);
    animateBars('.stage-bar > div', 300);

    // ── KPI value count-up animation ──────────────────────────────────────────
    document.querySelectorAll('.kpi-value').forEach(el => {
        const raw = el.textContent.trim().replace(/,/g, '');
        const num = parseFloat(raw);
        if (isNaN(num) || num === 0) return;

        const isDecimal = raw.includes('.');
        const duration = 800;
        const steps = 40;
        const increment = num / steps;
        let current = 0;
        let step = 0;

        el.textContent = isDecimal ? '0.00' : '0';

        const timer = setInterval(() => {
            step++;
            current += increment;
            if (step >= steps) {
                clearInterval(timer);
                current = num;
            }
            el.textContent = isDecimal
                ? current.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
                : Math.floor(current).toLocaleString('en-IN');
        }, duration / steps);
    });

    // ── Search box live filter for table rows ─────────────────────────────────
    const searchInput = document.querySelector('.search-box input');
    const tableRows = document.querySelectorAll('tbody tr');

    if (searchInput && tableRows.length > 0) {
        searchInput.addEventListener('input', () => {
            const query = searchInput.value.toLowerCase().trim();
            tableRows.forEach(row => {
                const text = row.textContent.toLowerCase();
                row.style.display = (!query || text.includes(query)) ? '' : 'none';
            });
        });
    }

    // ── Notification button pulse ──────────────────────────────────────────────
    const notifBtn = document.querySelector('.notif-btn');
    if (notifBtn && document.querySelector('.notif-dot')) {
        setInterval(() => {
            notifBtn.style.transform = 'scale(1.1)';
            setTimeout(() => { notifBtn.style.transform = 'scale(1)'; }, 150);
        }, 4000);
    }

    // ── Sidebar submenu collapse (accordion) ──────────────────────────────────
    // If you add collapsible submenus in the future, wire them here.
    // Currently each label group is flat. Placeholder for extensibility.

    // ── Table row hover highlight (accessible) ────────────────────────────────
    document.querySelectorAll('tbody tr').forEach(row => {
        row.setAttribute('tabindex', '0');
        row.addEventListener('keydown', e => {
            if (e.key === 'Enter') {
                const link = row.querySelector('a');
                if (link) link.click();
            }
        });
    });

    // ── Close sidebar on Escape key ───────────────────────────────────────────
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && sidebar && sidebar.classList.contains('open')) {
            sidebar.classList.remove('open');
            overlay.classList.remove('show');
        }
    });

});


// ── Add User page — form toggle & table search ────────────────────────────────
function toggleAddUserForm() {
    const form = document.getElementById('addUserForm');
    const btn  = document.getElementById('toggleFormBtn');
    if (!form || !btn) return;

    const open = form.style.display === 'block';
    form.style.display = open ? 'none' : 'block';

    if (open) {
        btn.innerHTML = '<svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" style="display:inline;vertical-align:middle;margin-right:4px"><path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd"/></svg> Add User';
    } else {
        btn.innerHTML = '<svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" style="display:inline;vertical-align:middle;margin-right:4px"><path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/></svg> Close';
    }
}

function filterUserTable(query) {
    const rows = document.querySelectorAll('#usersTable tbody tr:not(#emptyRow):not(#noResultsRow)');
    const q = query.toLowerCase().trim();
    let visible = 0;
    rows.forEach(row => {
        const match = row.textContent.toLowerCase().includes(q);
        row.style.display = match ? '' : 'none';
        if (match) visible++;
    });
    const noResults = document.getElementById('noResultsRow');
    if (noResults) noResults.style.display = (q && visible === 0) ? '' : 'none';
}
