/*
 * Goslin pages, plain JavaScript without libraries. Each part runs only when its elements exist.
 * - Input page: load the example names, clear, count the names, read a file into the text area
 *   (file chooser or drag and drop onto .goslin-names).
 * - Logo: a random blink offset per page load, and a honk on click.
 */
(function () {
  'use strict';

  // ---------- input page ----------
  var names = document.getElementById('lipids');
  if (names) {
    var count = document.getElementById('lipid-count');
    var lang = document.documentElement.lang || undefined;
    var updateCount = function () {
      if (!count) {
        return;
      }
      // Same rule as the server: non-blank lines, \r\n, \n and \r end a line.
      var n = names.value.split(/\r\n|\r|\n/).filter(function (line) {
        return line.trim() !== '';
      }).length;
      count.textContent = n.toLocaleString(lang) + ' ' + (n === 1 ? count.dataset.one : count.dataset.many)
          + ' · ' + count.dataset.max;
    };
    var setNames = function (text) {
      names.value = text;
      updateCount();
      names.focus();
    };
    names.addEventListener('input', updateCount);

    var example = document.getElementById('load-example');
    var exampleNames = document.getElementById('example-names');
    if (example && exampleNames) {
      example.addEventListener('click', function (event) {
        event.preventDefault();
        setNames(exampleNames.content.textContent);
      });
    }

    var clear = document.getElementById('clear');
    if (clear) {
      clear.hidden = false;
      clear.addEventListener('click', function () {
        setNames('');
      });
    }

    var file = document.getElementById('file');
    var fileError = document.getElementById('file-error');
    var maxBytes = file ? Number(file.dataset.maxBytes) || Infinity : Infinity;
    var showFileError = function (text) {
      if (fileError) {
        fileError.textContent = text;
        fileError.hidden = false;
      }
    };
    var isText = function (f) {
      return f.type === '' || f.type.indexOf('text/') === 0 || /\.(txt|csv|tsv)$/i.test(f.name);
    };
    var readFile = function (f) {
      if (!f) {
        return;
      }
      if (fileError) {
        fileError.hidden = true;
      }
      if (!isText(f)) {
        showFileError(fileError && fileError.dataset.notText);
        return;
      }
      if (f.size > maxBytes) {
        showFileError(fileError && fileError.dataset.tooLarge);
        return;
      }
      f.text().then(setNames).catch(function () {
        showFileError(fileError && fileError.dataset.unreadable);
      });
    };
    if (file) {
      file.addEventListener('change', function () {
        readFile(file.files[0]);
        file.value = '';
      });
    }

    // Only file drags are handled here; dragging selected text into the box keeps working.
    var hasFiles = function (event) {
      return !!event.dataTransfer && Array.prototype.indexOf.call(event.dataTransfer.types || [], 'Files') >= 0;
    };
    var box = document.querySelector('.goslin-names');
    if (box) {
      ['dragenter', 'dragover'].forEach(function (type) {
        box.addEventListener(type, function (event) {
          if (hasFiles(event)) {
            event.preventDefault();
            box.classList.add('goslin-names--drag');
          }
        });
      });
      ['dragleave', 'drop'].forEach(function (type) {
        box.addEventListener(type, function () {
          box.classList.remove('goslin-names--drag');
        });
      });
      box.addEventListener('drop', function (event) {
        if (hasFiles(event)) {
          event.preventDefault();
          readFile(event.dataTransfer.files[0]);
        }
      });
    }
    // A file dropped next to the box must not make the browser leave the page to open it.
    ['dragover', 'drop'].forEach(function (type) {
      window.addEventListener(type, function (event) {
        if (hasFiles(event)) {
          event.preventDefault();
        }
      });
    });

    // The browser may restore the text area after a reload or "back"; count what is there.
    updateCount();
  }

  // ---------- logo: blink offset and honk ----------
  var lines = ['Honk!', 'Honk!', 'Honk!', 'Honk!', 'PC 16:0/18:1(9Z), please.'];
  Array.prototype.forEach.call(document.querySelectorAll('.goslin-logo'), function (logo) {
    var eye = logo.querySelector('.g-eye');
    var bubble = logo.querySelector('.g-bubble');
    if (eye) {
      // Several logos, or reloads, do not blink in step.
      eye.style.animationDelay = (-Math.random() * 6).toFixed(2) + 's';
    }
    if (!bubble) {
      return;
    }
    var clicks = 0;
    var timer;
    logo.addEventListener('click', function () {
      bubble.textContent = lines[clicks++ % lines.length];
      logo.classList.remove('goslin-logo--honking');
      void logo.offsetWidth; // restart the peck
      logo.classList.add('goslin-logo--honking');
      clearTimeout(timer);
      timer = setTimeout(function () {
        logo.classList.remove('goslin-logo--honking');
      }, 1400);
    });
  });
})();
