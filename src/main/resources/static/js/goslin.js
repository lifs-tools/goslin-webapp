/*
 * Goslin pages, plain JavaScript without libraries. Each part runs only when its elements exist.
 * - Input page: load the example names, clear, count the names, read a file into the text area
 *   (file chooser or drag and drop onto .goslin-names).
 * - Result page: filter, sort and page the rendered table rows, and open their details rows.
 *   Rows are rendered on the server; this only shows, hides and reorders them.
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

  // ---------- result page: filter, sort, page, details ----------
  var table = document.getElementById('results');
  if (table && table.tBodies.length) {
    var PAGE_SIZE = 25;
    var tbody = table.tBodies[0];
    var tableLang = document.documentElement.lang || undefined;
    var format = function (pattern) {
      var args = Array.prototype.slice.call(arguments, 1);
      return (pattern || '').replace(/\{(\d)\}/g, function (m, i) {
        return args[i];
      });
    };
    var number = function (n) {
      return n.toLocaleString(tableLang);
    };
    var rows = Array.prototype.map.call(tbody.querySelectorAll('tr.result-row'), function (tr, i) {
      var next = tr.nextElementSibling;
      return {
        tr: tr, index: i, expanded: false,
        detail: next && next.classList.contains('detail') ? next : null,
        search: (tr.dataset.search || '').toLowerCase()
      };
    });
    rows.forEach(function (row) {
      row.tr.goslinRow = row;
    });
    table.classList.add('results--js');

    // ▸ / ▾ opens the details row; its <details> is opened once and its summary hidden by CSS.
    rows.forEach(function (row) {
      var button = row.tr.querySelector('.xbtn');
      if (!button || !row.detail) {
        return;
      }
      var details = row.detail.querySelector('details');
      if (details) {
        details.open = true;
      }
      row.detail.hidden = true;
      button.hidden = false;
      button.addEventListener('click', function () {
        row.expanded = !row.expanded;
        button.setAttribute('aria-expanded', String(row.expanded));
        button.setAttribute('aria-label', row.expanded ? button.dataset.hide : button.dataset.show);
        button.textContent = row.expanded ? '\u25BE' : '\u25B8';
        row.detail.hidden = !row.expanded;
      });
    });

    var filterBox = document.getElementById('filters');
    var text = document.getElementById('filter-text');
    var level = document.getElementById('filter-level');
    var category = document.getElementById('filter-category');
    var pager = document.getElementById('pager');
    var status = document.getElementById('pager-status');
    var previous = document.getElementById('pager-previous');
    var next = document.getElementById('pager-next');
    if (filterBox && rows.length) {
      filterBox.hidden = false;
    }

    var page = 0;
    var sortHeader = table.querySelector('th[aria-sort]');
    var sortDir = 1;
    var value = function (row, col, numeric) {
      var cell = row.tr.cells[col];
      var v = cell ? (cell.hasAttribute('data-value') ? cell.getAttribute('data-value') : cell.textContent.trim()) : '';
      return numeric ? parseFloat(v) : v;
    };
    var sortRows = function () {
      if (!sortHeader) {
        return;
      }
      var col = sortHeader.cellIndex;
      var numeric = sortHeader.dataset.sort === 'num';
      var sorted = rows.slice().sort(function (a, b) {
        var x = value(a, col, numeric);
        var y = value(b, col, numeric);
        var c;
        if (numeric) {
          // rows without a value (e.g. no mass) go last in both directions
          c = isNaN(x) ? (isNaN(y) ? 0 : 1) : (isNaN(y) ? -1 : sortDir * (x - y));
        } else {
          c = sortDir * String(x).localeCompare(String(y), tableLang, {numeric: true, sensitivity: 'base'});
        }
        return c || a.index - b.index;
      });
      sorted.forEach(function (row) {
        tbody.appendChild(row.tr);
        if (row.detail) {
          tbody.appendChild(row.detail);
        }
      });
    };
    var sortName = function () {
      var label = sortHeader ? sortHeader.textContent.trim() : '';
      // German nouns keep their capital letter.
      return /^de/.test(tableLang || '') ? label : label.toLowerCase();
    };

    var render = function () {
      var q = text ? text.value.trim().toLowerCase() : '';
      var lvl = level ? level.value : '';
      var cat = category ? category.value : '';
      var shown = [];
      // in the current (sorted) order
      var ordered = Array.prototype.map.call(tbody.querySelectorAll('tr.result-row'), function (tr) {
        return tr.goslinRow;
      });
      ordered.forEach(function (row) {
        var match = (!q || row.search.indexOf(q) >= 0) && (!lvl || row.tr.dataset.level === lvl)
            && (!cat || row.tr.dataset.category === cat);
        row.match = match;
        if (match) {
          shown.push(row);
        }
      });
      var pages = Math.max(1, Math.ceil(shown.length / PAGE_SIZE));
      page = Math.min(page, pages - 1);
      var from = page * PAGE_SIZE;
      var to = Math.min(from + PAGE_SIZE, shown.length);
      ordered.forEach(function (row) {
        row.visible = false;
      });
      shown.slice(from, to).forEach(function (row) {
        row.visible = true;
      });
      ordered.forEach(function (row) {
        row.tr.hidden = !row.visible;
        if (row.detail) {
          row.detail.hidden = !(row.visible && row.expanded);
        }
      });
      if (status && pager) {
        if (!shown.length) {
          status.textContent = rows.length ? pager.dataset.none : status.textContent;
        } else {
          status.textContent = format(pager.dataset.range, number(from + 1), number(to), number(shown.length))
              + (shown.length < rows.length ? ' ' + format(pager.dataset.filtered, number(rows.length)) : '')
              + ' \u00B7 ' + format(pager.dataset.sorted, sortName());
        }
      }
      if (previous && next) {
        previous.hidden = next.hidden = shown.length <= PAGE_SIZE;
        previous.disabled = page === 0;
        next.disabled = page >= pages - 1;
      }
    };
    // Column headers sort; the line column sorts ascending by default.
    Array.prototype.forEach.call(table.tHead.querySelectorAll('th[data-sort]'), function (th) {
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'sort-btn';
      while (th.firstChild) {
        button.appendChild(th.firstChild);
      }
      th.appendChild(button);
      button.addEventListener('click', function () {
        if (sortHeader === th) {
          sortDir = -sortDir;
        } else {
          if (sortHeader) {
            sortHeader.removeAttribute('aria-sort');
          }
          sortHeader = th;
          sortDir = 1;
        }
        th.setAttribute('aria-sort', sortDir > 0 ? 'ascending' : 'descending');
        sortRows();
        page = 0;
        render();
      });
    });

    [text, level, category].forEach(function (control) {
      if (control) {
        control.addEventListener('input', function () {
          page = 0;
          render();
        });
      }
    });
    var turn = function (delta) {
      page += delta;
      render();
      if (table.getBoundingClientRect().top < 0) {
        table.scrollIntoView();
      }
    };
    if (previous && next) {
      previous.addEventListener('click', function () {
        turn(-1);
      });
      next.addEventListener('click', function () {
        turn(1);
      });
    }
    // The browser may restore filter values after "back"; apply them.
    render();
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
