// Invoice form: add/remove lines and show totals while typing.
// The server recomputes every amount on save; these totals are only a preview.
(function () {
    'use strict';
    var form = document.getElementById('invoice-form');
    var tbody = form.querySelector('#lines-table tbody');
    var template = document.getElementById('line-template');
    var amountFormat = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    function formatAmount(value) {
        // Same output as AmountUtils.format: "1 234,50 DA" with a regular space
        return amountFormat.format(value).replace(/\s/g, ' ') + ' DA';
    }

    function roundCents(value) {
        return Math.round(value * 100) / 100;
    }

    function refresh() {
        var total = 0;
        // Company: TVA per rate on the total of its lines, as InvoiceService computes it
        var basesByRate = {};
        var rows = tbody.querySelectorAll('[data-line]');
        rows.forEach(function (row, index) {
            row.querySelector('[data-line-number]').textContent = index + 1;
            // Keep indexes contiguous so Spring binds lines[0..n]
            row.querySelectorAll('[name^="lines["]').forEach(function (input) {
                input.name = input.name.replace(/^lines\[\d+\]/, 'lines[' + index + ']');
                input.removeAttribute('id');
            });
            var quantity = parseFloat(row.querySelector('[data-quantity]').value) || 0;
            var unitPrice = parseFloat(row.querySelector('[data-unit-price]').value) || 0;
            var lineTotal = roundCents(quantity * unitPrice);
            row.querySelector('[data-line-total]').textContent = formatAmount(lineTotal);
            total += lineTotal;
            var rateSelect = row.querySelector('[data-vat-rate]');
            var rate = rateSelect ? parseFloat(rateSelect.value) || 0 : 0;
            basesByRate[rate] = (basesByRate[rate] || 0) + lineTotal;
        });
        var subtotal = roundCents(total);
        var vat = 0;
        Object.keys(basesByRate).forEach(function (rate) {
            vat += roundCents(basesByRate[rate] * parseFloat(rate) / 100);
        });
        vat = roundCents(vat);
        form.querySelectorAll('[data-subtotal]').forEach(function (element) {
            element.textContent = formatAmount(subtotal);
        });
        form.querySelectorAll('[data-vat]').forEach(function (element) {
            element.textContent = formatAmount(vat);
        });
        form.querySelectorAll('[data-grand-total]').forEach(function (element) {
            element.textContent = formatAmount(roundCents(subtotal + vat));
        });
        // The last remaining line cannot be removed
        tbody.querySelectorAll('[data-remove-line]').forEach(function (button) {
            button.disabled = rows.length === 1;
        });
    }

    function addLine() {
        var html = template.innerHTML.replace(/INDEX/g, String(tbody.querySelectorAll('[data-line]').length));
        tbody.insertAdjacentHTML('beforeend', html);
        refresh();
        tbody.lastElementChild.querySelector('input').focus();
    }

    form.querySelector('[data-add-line]').addEventListener('click', addLine);
    tbody.addEventListener('click', function (event) {
        var button = event.target.closest('[data-remove-line]');
        if (button) {
            button.closest('[data-line]').remove();
            refresh();
        }
    });
    tbody.addEventListener('input', refresh);
    tbody.addEventListener('change', refresh);

    if (!tbody.querySelector('[data-line]')) {
        addLine();
    }
    refresh();
})();
