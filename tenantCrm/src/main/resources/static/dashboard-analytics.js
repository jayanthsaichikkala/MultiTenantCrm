const CrmAnalytics = {
    getNumber(value) {
        const number = Number(value);
        return Number.isFinite(number) ? number : 0;
    },

    ensureArray(value) {
        return Array.isArray(value) ? value : [];
    },

    pctLabel(ctx) {
        const total = ctx.dataset.data.reduce((sum, value) => {
            return sum + CrmAnalytics.getNumber(value);
        }, 0);
        const pct = total > 0 ? Math.round(ctx.parsed / total * 100) : 0;
        return ' ' + ctx.label + ': ' + ctx.parsed + ' (' + pct + '%)';
    },

    setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    },

    updateChips(next) {
        CrmAnalytics.setText('chip-done', CrmAnalytics.getNumber(next.statusDone) + ' Done');
        CrmAnalytics.setText('chip-inprog', CrmAnalytics.getNumber(next.statusInProgress) + ' In Progress');
        CrmAnalytics.setText('chip-pending', CrmAnalytics.getNumber(next.statusPending) + ' Pending');
        CrmAnalytics.setText('chip-review', CrmAnalytics.getNumber(next.statusReview) + ' Review');
        CrmAnalytics.setText('chip-high', CrmAnalytics.getNumber(next.priorityHigh) + ' High');
        CrmAnalytics.setText('chip-medium', CrmAnalytics.getNumber(next.priorityMedium) + ' Medium');
        CrmAnalytics.setText('chip-low', CrmAnalytics.getNumber(next.priorityLow) + ' Low');
        CrmAnalytics.setText('chip-active-team', CrmAnalytics.getNumber(next.activeTeam) + ' Active');
        CrmAnalytics.setText('chip-inactive-team', CrmAnalytics.getNumber(next.inactiveTeam) + ' Inactive');
        CrmAnalytics.setText('chip-approved', CrmAnalytics.getNumber(next.verified) + ' Approved');
        CrmAnalytics.setText('chip-rejected', CrmAnalytics.getNumber(next.rejected) + ' Rejected');
        CrmAnalytics.setText('chip-waiting', CrmAnalytics.getNumber(next.waiting) + ' Waiting');
        CrmAnalytics.setText('chip-unverified', CrmAnalytics.getNumber(next.unverified) + ' Open');
    },

    updateStats(next, cfg) {
        const bindings = cfg.statBindings || {};
        Object.keys(bindings).forEach((key) => {
            const el = document.querySelector('[data-analytics-stat="' + key + '"]');
            if (el && Object.prototype.hasOwnProperty.call(next, bindings[key])) {
                el.textContent = next[bindings[key]];
            }
        });
    },

    replaceDataset(chart, labels, values) {
        if (!chart) return;
        
        if (!chart.$originalColors && chart.data.datasets && chart.data.datasets[0]) {
            chart.$originalColors = chart.data.datasets[0].backgroundColor;
        }
        
        const sum = values.reduce((sumVal, val) => {
            return sumVal + CrmAnalytics.getNumber(val);
        }, 0);
        
        if (sum === 0 && (chart.config.type === 'doughnut' || chart.config.type === 'pie')) {
            chart.data.labels = ['No Data'];
            chart.data.datasets[0].data = [1];
            chart.data.datasets[0].backgroundColor = ['#edf3f6'];
        } else {
            chart.data.labels = labels;
            chart.data.datasets[0].data = values;
            if (chart.$originalColors && chart.data.datasets && chart.data.datasets[0]) {
                chart.data.datasets[0].backgroundColor = chart.$originalColors;
            }
        }
        
        chart.update('none');
    },

    createChart(id, options) {
        const el = document.getElementById(id);
        if (!el) return null;
        return new Chart(el, options);
    }
};

(function () {
    'use strict';

    const cfg = window.dashboardAnalytics || {};
    const charts = window.dashboardCharts || {};
    window.dashboardCharts = charts;
    const pollMs = cfg.pollMs || 10000;
    const PRIMARY = '#17455e';
    let data = cfg.initialData || {};

    if (!window.Chart) return;

    Chart.defaults.font.family = "'Poppins', sans-serif";
    Chart.defaults.font.size = 12;
    Chart.defaults.color = '#6b7280';
    Chart.defaults.plugins.legend.labels.usePointStyle = true;
    Chart.defaults.plugins.legend.labels.pointStyleWidth = 10;
    Chart.defaults.plugins.legend.labels.padding = 18;
    Chart.defaults.plugins.tooltip.padding = 10;
    Chart.defaults.plugins.tooltip.cornerRadius = 10;
    Chart.defaults.plugins.tooltip.titleFont = { weight: '700' };

    function render(next) {
        data = Object.assign({}, data, next || {});
        CrmAnalytics.updateChips(data);
        CrmAnalytics.updateStats(data, cfg);

        CrmAnalytics.replaceDataset(charts.status,
            ['Done', 'In Progress', 'Pending', 'Waiting Review'],
            [CrmAnalytics.getNumber(data.statusDone), CrmAnalytics.getNumber(data.statusInProgress), CrmAnalytics.getNumber(data.statusPending), CrmAnalytics.getNumber(data.statusReview)]);

        CrmAnalytics.replaceDataset(charts.priority,
            ['High', 'Medium', 'Low'],
            [CrmAnalytics.getNumber(data.priorityHigh), CrmAnalytics.getNumber(data.priorityMedium), CrmAnalytics.getNumber(data.priorityLow)]);

        const memberLabels = CrmAnalytics.ensureArray(data.memberLabels);
        const memberCounts = CrmAnalytics.ensureArray(data.memberTaskCounts);
        CrmAnalytics.replaceDataset(charts.member,
            memberLabels.length ? memberLabels : ['No Data'],
            memberCounts.length ? memberCounts : [0]);

        CrmAnalytics.replaceDataset(charts.team,
            cfg.peopleChartLabels || ['Active', 'Inactive'],
            [CrmAnalytics.getNumber(data.activeTeam), CrmAnalytics.getNumber(data.inactiveTeam)]);

        CrmAnalytics.replaceDataset(charts.verification,
            ['Approved', 'Rejected', 'Waiting Review', 'Open'],
            [CrmAnalytics.getNumber(data.verified), CrmAnalytics.getNumber(data.rejected), CrmAnalytics.getNumber(data.waiting), CrmAnalytics.getNumber(data.unverified)]);
    }

    charts.status = CrmAnalytics.createChart('taskStatusChart', {
        type: 'doughnut',
        data: {
            labels: ['Done', 'In Progress', 'Pending', 'Waiting Review'],
            datasets: [{
                data: [0, 0, 0, 0],
                backgroundColor: ['rgba(27,122,70,.85)', 'rgba(37,99,235,.85)', 'rgba(192,86,33,.85)', 'rgba(107,70,193,.85)'],
                borderColor: '#fff',
                borderWidth: 3,
                hoverOffset: 10
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '60%',
            layout: { padding: { top: 20, bottom: 10, left: 10, right: 10 } },
            plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: CrmAnalytics.pctLabel } } }
        }
    });

    charts.priority = CrmAnalytics.createChart('taskPriorityChart', {
        type: 'pie',
        data: {
            labels: ['High', 'Medium', 'Low'],
            datasets: [{
                data: [0, 0, 0],
                backgroundColor: ['rgba(209,26,42,.85)', 'rgba(192,86,33,.80)', 'rgba(27,122,70,.80)'],
                borderColor: '#fff',
                borderWidth: 3,
                hoverOffset: 10
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            layout: { padding: { top: 20, bottom: 10, left: 10, right: 10 } },
            plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: CrmAnalytics.pctLabel } } }
        }
    });

    charts.member = CrmAnalytics.createChart('memberTaskChart', {
        type: 'bar',
        data: {
            labels: ['No Data'],
            datasets: [{
                label: cfg.memberDatasetLabel || 'Tasks',
                data: [0],
                backgroundColor: 'rgba(23,69,94,.18)',
                borderColor: PRIMARY,
                borderWidth: 2,
                borderRadius: 8,
                borderSkipped: false
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true, ticks: { stepSize: 1, precision: 0 }, grid: { color: '#f0f6f9' } },
                x: { grid: { display: false } }
            }
        }
    });

    charts.team = CrmAnalytics.createChart('teamStatusChart', {
        type: 'pie',
        data: {
            labels: cfg.peopleChartLabels || ['Active', 'Inactive'],
            datasets: [{
                data: [0, 0],
                backgroundColor: ['rgba(27,122,70,.85)', 'rgba(209,26,42,.80)'],
                borderColor: '#fff',
                borderWidth: 3,
                hoverOffset: 10
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            layout: { padding: { top: 20, bottom: 10, left: 10, right: 10 } },
            plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: CrmAnalytics.pctLabel } } }
        }
    });

    charts.verification = CrmAnalytics.createChart('verificationChart', {
        type: 'doughnut',
        data: {
            labels: ['Approved', 'Rejected', 'Waiting Review', 'Open'],
            datasets: [{
                data: [0, 0, 0, 0],
                backgroundColor: ['rgba(27,122,70,.85)', 'rgba(209,26,42,.80)', 'rgba(192,86,33,.80)', 'rgba(37,99,235,.75)'],
                borderColor: '#fff',
                borderWidth: 3,
                hoverOffset: 10
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '60%',
            layout: { padding: { top: 20, bottom: 10, left: 10, right: 10 } },
            plugins: { legend: { position: 'bottom' }, tooltip: { callbacks: { label: CrmAnalytics.pctLabel } } }
        }
    });

    render(data);

    function fetchLatest() {
        if (!cfg.endpoint) return;
        fetch(cfg.endpoint, { headers: { Accept: 'application/json' }, cache: 'no-store' })
            .then((response) => {
                if (!response.ok) throw new Error('Analytics refresh failed');
                return response.json();
            })
            .then(render)
            .catch(() => {});
    }

    fetchLatest();
    window.setInterval(fetchLatest, pollMs);
    window.refreshDashboardAnalytics = render;
})();
