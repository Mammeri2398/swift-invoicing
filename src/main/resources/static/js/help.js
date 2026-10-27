// Aide: the search box hides the topics that do not contain every typed word (and their table of contents entry).
(function () {
    'use strict';
    var input = document.querySelector('[data-help-search]');
    if (!input) {
        return;
    }
    var sections = document.querySelectorAll('[data-help-section]');
    var empty = document.querySelector('[data-help-empty]');

    function normalize(text) {
        // Accents ignored: "echeance" finds "échéance"
        return text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    }

    input.addEventListener('input', function () {
        var words = normalize(input.value).split(/\s+/).filter(Boolean);
        var shown = 0;
        sections.forEach(function (section) {
            var text = normalize(section.textContent);
            var match = words.every(function (word) { return text.indexOf(word) >= 0; });
            section.classList.toggle('hidden', !match);
            var link = document.querySelector('[data-help-toc] a[href="#' + section.id + '"]');
            if (link) {
                link.classList.toggle('hidden', !match);
            }
            shown += match ? 1 : 0;
        });
        empty.classList.toggle('hidden', shown > 0);
    });
})();
