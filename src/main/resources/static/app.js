'use strict';

// The token lives only in memory: closing or reloading the tab ends the session (no storage an XSS could read).
const state = { token: null, email: null, report: 'trial-balance' };

const REPORTS = {
    'trial-balance': {
        title: 'Balance de comprobación',
        fields: ['year', 'fromMonth', 'toMonth'],
        params: () => ({ year: val('year'), fromMonth: val('fromMonth'), toMonth: val('toMonth') }),
        render: renderTrialBalance
    },
    'balance-sheet': {
        title: 'Estado de situación financiera',
        fields: ['year', 'month'],
        params: () => ({ year: val('year'), month: val('month') }),
        render: renderBalanceSheet
    },
    'income-statement': {
        title: 'Estado de resultados',
        fields: ['year', 'fromMonth', 'toMonth', 'costCenter'],
        params: () => ({ year: val('year'), fromMonth: val('fromMonth'), toMonth: val('toMonth'), costCenter: val('costCenter') }),
        render: renderIncomeStatement
    },
    journal: {
        title: 'Libro diario',
        fields: ['from', 'to'],
        params: () => ({ from: val('from'), to: val('to') }),
        render: renderJournal
    },
    ledger: {
        title: 'Libro mayor',
        fields: ['account', 'from', 'to'],
        params: () => ({ account: val('account'), from: val('from'), to: val('to') }),
        render: renderLedger
    }
};

const money = new Intl.NumberFormat('es-EC', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

document.addEventListener('DOMContentLoaded', () => {
    const today = new Date();
    const year = today.getFullYear();
    document.getElementById('year').value = year;
    document.getElementById('month').value = today.getMonth() + 1;
    document.getElementById('from').value = `${year}-01-01`;
    document.getElementById('to').value = `${year}-12-31`;

    document.getElementById('login-form').addEventListener('submit', login);
    document.getElementById('logout').addEventListener('click', logout);
    document.getElementById('filters').addEventListener('submit', event => {
        event.preventDefault();
        loadReport();
    });
    document.getElementById('download-xlsx').addEventListener('click', () => download('XLSX'));
    document.getElementById('download-pdf').addEventListener('click', () => download('PDF'));
    document.querySelectorAll('.tabs button').forEach(tab => tab.addEventListener('click', () => selectReport(tab.dataset.report)));
});

function val(id) {
    return document.getElementById(id).value.trim();
}

async function login(event) {
    event.preventDefault();
    const error = document.getElementById('login-error');
    error.textContent = '';
    try {
        const response = await fetch('api/v1/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: val('email'), password: document.getElementById('password').value })
        });
        const body = await response.json();
        if (!response.ok) {
            error.textContent = body.detail || 'No se pudo iniciar sesión';
            return;
        }
        state.token = body.accessToken;
        state.email = val('email');
        document.getElementById('password').value = '';
        document.getElementById('session-user').textContent = `${state.email} · ${body.role}`;
        document.getElementById('session').hidden = false;
        document.getElementById('login-view').hidden = true;
        document.getElementById('app-view').hidden = false;
        selectReport(state.report);
    } catch (e) {
        error.textContent = 'No se pudo conectar con la API';
    }
}

function logout() {
    state.token = null;
    state.email = null;
    document.getElementById('session').hidden = true;
    document.getElementById('app-view').hidden = true;
    document.getElementById('login-view').hidden = false;
}

function selectReport(report) {
    state.report = report;
    document.querySelectorAll('.tabs button').forEach(tab =>
        tab.setAttribute('aria-selected', String(tab.dataset.report === report)));
    const fields = REPORTS[report].fields;
    document.querySelectorAll('#filters label[data-for]').forEach(label => {
        label.hidden = !fields.includes(label.dataset.for);
    });
    document.getElementById('report-title').textContent = REPORTS[report].title;
    loadReport();
}

function query(extra = {}) {
    const params = new URLSearchParams();
    Object.entries({ ...REPORTS[state.report].params(), ...extra }).forEach(([key, value]) => {
        if (value !== '' && value !== undefined) {
            params.set(key, value);
        }
    });
    return `api/v1/reports/${state.report}?${params}`;
}

async function authorizedFetch(url) {
    const response = await fetch(url, { headers: { Authorization: `Bearer ${state.token}` } });
    if (response.status === 401) {
        logout();
        throw new Error('La sesión expiró; vuelve a iniciar sesión');
    }
    return response;
}

async function loadReport() {
    const error = document.getElementById('report-error');
    error.textContent = '';
    clearTable();
    try {
        const response = await authorizedFetch(query());
        const body = await response.json();
        if (!response.ok) {
            error.textContent = problemText(body);
            setStatus(null);
            return;
        }
        REPORTS[state.report].render(body);
    } catch (e) {
        error.textContent = e.message;
    }
}

async function download(format) {
    const error = document.getElementById('report-error');
    error.textContent = '';
    try {
        const response = await authorizedFetch(query({ format }));
        if (!response.ok) {
            error.textContent = problemText(await response.json());
            return;
        }
        const disposition = response.headers.get('Content-Disposition') || '';
        const match = /filename="?([^";]+)"?/.exec(disposition);
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement('a');
        link.href = url;
        link.download = match ? match[1] : `reporte.${format.toLowerCase()}`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    } catch (e) {
        error.textContent = e.message;
    }
}

function problemText(body) {
    const details = body.errors ? ` (${Object.entries(body.errors).map(([k, v]) => `${k}: ${v}`).join('; ')})` : '';
    return `${body.detail || body.title || 'Error'}${details}`;
}

/* ---------- rendering (always textContent, never innerHTML) ---------- */

function clearTable() {
    document.querySelector('#report-table thead').replaceChildren();
    document.querySelector('#report-table tbody').replaceChildren();
    document.getElementById('summary').replaceChildren();
    document.getElementById('report-subtitle').textContent = '';
}

function setStatus(ok, okText = 'Cuadrado', badText = 'Descuadrado') {
    const badge = document.getElementById('report-status');
    if (ok === null) {
        badge.hidden = true;
        return;
    }
    badge.hidden = false;
    badge.className = `badge ${ok ? 'ok' : 'bad'}`;
    badge.textContent = ok ? okText : badText;
}

function setHeader(columns) {
    const row = document.createElement('tr');
    columns.forEach(([label, numeric]) => {
        const th = document.createElement('th');
        th.textContent = label;
        if (numeric) th.className = 'num';
        row.appendChild(th);
    });
    document.querySelector('#report-table thead').appendChild(row);
}

function addRow(cells, className = '') {
    const row = document.createElement('tr');
    if (className) row.className = className;
    cells.forEach(cell => {
        const td = document.createElement('td');
        if (typeof cell === 'number' || (cell !== null && typeof cell === 'object' && 'amount' in cell)) {
            const amount = typeof cell === 'number' ? cell : cell.amount;
            td.className = amount < 0 ? 'num negative' : 'num';
            td.textContent = amount === null ? '' : money.format(amount);
        } else {
            td.textContent = cell ?? '';
        }
        row.appendChild(td);
    });
    document.querySelector('#report-table tbody').appendChild(row);
}

function summary(items) {
    const container = document.getElementById('summary');
    items.forEach(([label, amount]) => {
        const box = document.createElement('div');
        const caption = document.createElement('span');
        caption.textContent = label;
        const value = document.createElement('strong');
        value.textContent = money.format(amount);
        box.append(caption, value);
        container.appendChild(box);
    });
}

function indent(text, level) {
    return `${'   '.repeat(Math.max(0, level))}${text}`;
}

function renderTrialBalance(report) {
    document.getElementById('report-subtitle').textContent = `${report.year} · meses ${report.fromMonth} a ${report.toMonth} · USD`;
    setStatus(report.balanced);
    summary([['Total debe', report.totalDebit], ['Total haber', report.totalCredit],
        ['Saldos deudores', report.totalDebitBalance], ['Saldos acreedores', report.totalCreditBalance]]);
    setHeader([['Código'], ['Cuenta'], ['Saldo inicial D', true], ['Saldo inicial A', true], ['Debe', true],
        ['Haber', true], ['Saldo deudor', true], ['Saldo acreedor', true]]);
    report.rows.forEach(row => addRow([row.code, indent(row.name, row.level - 1), row.openingDebit, row.openingCredit,
        row.debit, row.credit, row.debitBalance, row.creditBalance], row.detail ? '' : 'strong'));
}

function renderStatement(lines) {
    setHeader([['Código'], ['Concepto'], ['Valor', true]]);
    lines.forEach(line => addRow([line.code ?? '', indent(line.label, line.indent), { amount: line.amount ?? null }],
        line.kind === 'HEADER' ? 'header' : line.kind === 'DETAIL' ? '' : 'strong'));
}

function renderBalanceSheet(report) {
    document.getElementById('report-subtitle').textContent = `Al ${report.asOf} · USD`;
    setStatus(report.balanced, 'Activo = Pasivo + Patrimonio', 'Ecuación descuadrada');
    summary([['Activo', report.totalAssets], ['Pasivo', report.totalLiabilities], ['Patrimonio', report.totalEquity],
        ['Resultado no cerrado', report.currentResult]]);
    renderStatement(report.lines);
}

function renderIncomeStatement(report) {
    const center = report.costCenterCode ? ` · centro de costo ${report.costCenterCode}` : '';
    document.getElementById('report-subtitle').textContent = `${report.year} · meses ${report.fromMonth} a ${report.toMonth}${center} · USD`;
    setStatus(null);
    summary([['Ingresos ordinarios', report.revenue], ['Ganancia bruta', report.grossProfit],
        ['Antes de 15% e IR', report.profitBeforeParticipationAndTax], ['Ganancia neta', report.netProfit]]);
    renderStatement(report.lines);
}

function renderJournal(report) {
    document.getElementById('report-subtitle').textContent = `Del ${report.from} al ${report.to} · ${report.entries.length} asientos · USD`;
    setStatus(report.totalDebit === report.totalCredit);
    summary([['Total debe', report.totalDebit], ['Total haber', report.totalCredit]]);
    setHeader([['Fecha'], ['Asiento'], ['Cuenta'], ['Detalle'], ['Debe', true], ['Haber', true]]);
    report.entries.forEach(entry => {
        addRow([entry.date, entry.number, '', `${entry.description} (${entry.type})`, '', ''], 'header');
        entry.lines.forEach(line => addRow(['', '', line.accountCode, line.accountName, line.debit || { amount: null },
            line.credit || { amount: null }]));
    });
}

function renderLedger(report) {
    document.getElementById('report-subtitle').textContent =
        `${report.accountCode} ${report.accountName} · naturaleza ${report.nature === 'DEBIT' ? 'deudora' : 'acreedora'} · del ${report.from} al ${report.to}`;
    setStatus(null);
    summary([['Saldo inicial', report.openingBalance], ['Debe', report.totalDebit], ['Haber', report.totalCredit],
        ['Saldo final', report.closingBalance]]);
    setHeader([['Fecha'], ['Asiento'], ['Cuenta'], ['Detalle'], ['Debe', true], ['Haber', true], ['Saldo', true]]);
    addRow(['', '', '', 'Saldo inicial', { amount: null }, { amount: null }, report.openingBalance], 'strong');
    report.lines.forEach(line => addRow([line.date, line.entryNumber, line.accountCode, line.description,
        line.debit || { amount: null }, line.credit || { amount: null }, line.balance]));
}
