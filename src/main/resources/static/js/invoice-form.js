// Invoice form: add/remove lines and show totals while typing.
// The server recomputes every amount on save; these totals are only a preview.
(function () {
    'use strict';
    var form = document.getElementById('invoice-form');
    var tbody = form.querySelector('#lines-table tbody');
    var template = document.getElementById('line-template');
    // Amounts are written the French way in every language, as AmountUtils.format does
    var amountFormat = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    // Droit de timbre rules (InvoiceProperties.StampDuty), absent on a credit note
    var stampRules = form.getAttribute('data-stamp-duty') ? JSON.parse(form.getAttribute('data-stamp-duty')) : null;
    var paymentMethod = document.getElementById('paymentMethod');

    // Same computation as StampDuty#compute: rate of the bracket containing the amount, rounded up to the dinar
    function stampDuty(amount) {
        if (!stampRules || !paymentMethod || paymentMethod.value !== 'CASH' || amount <= 0) {
            return 0;
        }
        var rate = stampRules.brackets[stampRules.brackets.length - 1].rate;
        for (var i = 0; i < stampRules.brackets.length; i++) {
            var bracket = stampRules.brackets[i];
            if (bracket.upTo === null || amount <= bracket.upTo) {
                rate = bracket.rate;
                break;
            }
        }
        var duty = Math.max(Math.ceil(roundCents(amount * rate / 100)), stampRules.minimum);
        return stampRules.maximum !== null ? Math.min(duty, stampRules.maximum) : duty;
    }

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
        var totalWithTax = roundCents(subtotal + vat);
        var stamp = stampDuty(totalWithTax);
        form.querySelectorAll('[data-total]').forEach(function (element) {
            element.textContent = formatAmount(totalWithTax);
        });
        form.querySelectorAll('[data-stamp]').forEach(function (element) {
            element.textContent = formatAmount(stamp);
        });
        form.querySelectorAll('[data-stamp-row]').forEach(function (element) {
            element.classList.toggle('hidden', stamp === 0);
            element.classList.toggle('flex', stamp > 0 && element.tagName === 'DIV');
        });
        form.querySelectorAll('[data-grand-label]').forEach(function (element) {
            // Labels in the page's language (invoice/form.html)
            element.textContent = element.getAttribute(stamp > 0 ? 'data-net-label' : 'data-label');
        });
        form.querySelectorAll('[data-grand-total]').forEach(function (element) {
            element.textContent = formatAmount(roundCents(totalWithTax + stamp));
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
    if (paymentMethod) {
        paymentMethod.addEventListener('change', refresh);
    }

    if (!tbody.querySelector('[data-line]')) {
        addLine();
    }
    refresh();
})();
