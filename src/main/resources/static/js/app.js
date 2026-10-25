// Behaviours shared by every page, attached through data attributes (no inline handlers).
(function () {
    'use strict';

    // <button data-dismiss> removes its alert
    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-dismiss]');
        if (button) {
            button.closest('.alert').remove();
        }
    });

    // <form data-confirm="Supprimer ce client ?"> asks before submitting
    document.addEventListener('submit', function (event) {
        var message = event.target.getAttribute('data-confirm');
        if (message && !window.confirm(message)) {
            event.preventDefault();
        }
    });
})();

// <button data-toggle-password="inputId"> shows or hides a password field
(function () {
    'use strict';
    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-toggle-password]');
        if (!button) {
            return;
        }
        var input = document.getElementById(button.getAttribute('data-toggle-password'));
        var visible = input.type === 'text';
        input.type = visible ? 'password' : 'text';
        button.querySelector('.icon').textContent = visible ? 'visibility' : 'visibility_off';
        button.setAttribute('aria-label', visible ? 'Afficher le mot de passe' : 'Masquer le mot de passe');
    });
})();

// <input data-table-filter="tableId"> hides the rows that do not contain the typed text
(function () {
    'use strict';
    document.addEventListener('input', function (event) {
        var tableId = event.target.getAttribute('data-table-filter');
        if (!tableId) {
            return;
        }
        var query = event.target.value.trim().toLowerCase();
        document.querySelectorAll('#' + tableId + ' tbody tr').forEach(function (row) {
            if (row.querySelector('.table-empty')) {
                return;
            }
            row.hidden = query !== '' && row.textContent.toLowerCase().indexOf(query) === -1;
        });
    });
})();

// <select data-autosubmit> submits its form as soon as the choice changes
(function () {
    'use strict';
    document.addEventListener('change', function (event) {
        if (event.target.matches('select[data-autosubmit]')) {
            event.target.form.submit();
        }
    });
})();

// <details data-dropdown> menus close on a click outside or on Escape
(function () {
    'use strict';
    function closeAll(except) {
        document.querySelectorAll('details[data-dropdown][open]').forEach(function (menu) {
            if (menu !== except) {
                menu.removeAttribute('open');
            }
        });
    }
    document.addEventListener('click', function (event) {
        closeAll(event.target.closest('details[data-dropdown]'));
    });
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape') {
            closeAll(null);
        }
    });
})();

// A link ending in #fieldId (e.g. the search icon of the top bar) puts the cursor in that field
(function () {
    'use strict';
    var target = location.hash && document.getElementById(location.hash.substring(1));
    if (target && target.matches('input, select, textarea')) {
        target.focus();
    }
})();
