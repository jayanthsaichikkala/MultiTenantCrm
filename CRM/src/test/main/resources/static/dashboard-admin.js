/* dashboard-admin.js – CRM Portal */

document.addEventListener('DOMContentLoaded', () => {

    // ── Sidebar mobile toggle ────────────────────────────────────────────────
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

    // ── Auto-dismiss toast notifications ─────────────────────────────────────
    document.querySelectorAll('.toast').forEach(t => {
        setTimeout(() => {
            t.style.transition = 'opacity .5s';
            t.style.opacity = '0';
            setTimeout(() => t.remove(), 500);
        }, 3500);
    });

    // ── KPI bar animation on load ─────────────────────────────────────────────
    document.querySelectorAll('.kpi-bar > div').forEach(bar => {
        const target = bar.style.width;
        bar.style.width = '0';
        requestAnimationFrame(() => {
            bar.style.transition = 'width 1s cubic-bezier(.4,0,.2,1)';
            bar.style.width = target;
        });
    });

    // ── Pipeline bar animation ────────────────────────────────────────────────
    document.querySelectorAll('.stage-bar > div').forEach(bar => {
        const target = bar.style.width;
        bar.style.width = '0';
        setTimeout(() => {
            bar.style.transition = 'width .8s cubic-bezier(.4,0,.2,1)';
            bar.style.width = target;
        }, 300);
    });

});