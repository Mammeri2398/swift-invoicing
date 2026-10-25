// Client form: the NIF is required for professional clients only, and the other identifiers only concern them.
(function () {
    'use strict';
    var form = document.getElementById('client-form');
    var nif = document.getElementById('nif');
    var nifMark = form.querySelector('[data-nif-required]');
    var nameLabel = form.querySelector('[data-name-label]');

    function update() {
        var checked = form.querySelector('[data-client-type]:checked');
        var professional = !checked || checked.value === 'PROFESSIONNEL';
        nif.required = professional;
        nifMark.hidden = !professional;
        nameLabel.textContent = professional ? 'Raison sociale' : 'Nom complet';
        form.querySelectorAll('[data-professional]').forEach(function (element) {
            // A class, not the hidden attribute: .field sets its own display
            element.classList.toggle('hidden', !professional);
        });
    }

    form.querySelectorAll('[data-client-type]').forEach(function (radio) {
        radio.addEventListener('change', update);
    });
    update();
})();
