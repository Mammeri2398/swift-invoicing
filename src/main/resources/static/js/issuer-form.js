// Issuer form: the invoice header preview follows the fields while typing.
(function () {
    'use strict';
    document.querySelectorAll('#issuer-form [data-preview]').forEach(function (target) {
        var input = document.getElementById(target.getAttribute('data-preview'));
        if (input) {
            input.addEventListener('input', function () {
                target.textContent = input.value;
            });
        }
    });
})();

// Issuer form: show the fields of the chosen legal status (auto-entrepreneur or company)
(function () {
    'use strict';
    var radios = document.querySelectorAll('#issuer-form [data-company-type]');
    function apply() {
        var checked = document.querySelector('#issuer-form [data-company-type]:checked');
        var type = checked ? checked.value : 'AUTO_ENTREPRENEUR';
        document.querySelectorAll('[data-show]').forEach(function (element) {
            var hidden = element.getAttribute('data-show') !== type;
            element.classList.toggle('hidden', hidden);
            // A hidden required field would block the form without any visible message (the browser cannot show
            // its bubble): it is required only while shown, the server checks the fields of the chosen status
            element.querySelectorAll('[required], [data-required]').forEach(function (input) {
                if (hidden && input.required) {
                    input.required = false;
                    input.setAttribute('data-required', '');
                } else if (!hidden && input.hasAttribute('data-required')) {
                    input.required = true;
                    input.removeAttribute('data-required');
                }
            });
        });
    }
    radios.forEach(function (radio) {
        radio.addEventListener('change', apply);
    });
    if (radios.length > 0) {
        apply();
    }
})();
