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
            element.classList.toggle('hidden', element.getAttribute('data-show') !== type);
        });
    }
    radios.forEach(function (radio) {
        radio.addEventListener('change', apply);
    });
})();
