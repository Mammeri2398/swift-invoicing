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
