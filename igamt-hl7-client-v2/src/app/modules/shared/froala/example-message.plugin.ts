const WIDGET_CLASS = 'fr-exmsg';
const BODY_CLASS = 'fr-exmsg-body';
const LINK_CLASS = 'fr-exmsg-link';
const REMOVE_CLASS = 'fr-exmsg-remove';
const SELECTED_CLASS = 'fr-exmsg-selected';
const VIEW_BAR_CLASS = 'fr-exmsg-views';
const VIEW_BTN_CLASS = 'fr-exmsg-view';

function getFe(): any {
  const jq = (window as any).$ || (window as any).jQuery;
  return jq && (jq.FE || jq.FroalaEditor);
}

function getJq(): any {
  return (window as any).$ || (window as any).jQuery;
}

export function registerFroalaExampleMessagePlugin() {
  const FE = getFe();
  if (!FE) {
    return;
  }
  const alreadyRegistered = !!FE.PLUGINS.exampleMessage;

  FE.POPUP_TEMPLATES['exampleMessage.edit'] = '[_BUTTONS_]';

  FE.PLUGINS.exampleMessage = function(editor) {
    let $current = null;

    function pick() {
      if (typeof editor.opts.igamtExampleMessagePick === 'function') {
        editor.opts.igamtExampleMessagePick(editor);
      }
    }

    function renderAll() {
      if (typeof editor.opts.igamtExampleMessageRender === 'function') {
        editor.opts.igamtExampleMessageRender(editor);
      }
      attachRemoveControls();
    }

    function insertWidget(html: string) {
      editor.html.insert(html + '<p><br></p>', true);
      if (typeof editor.opts.igamtExampleMessageRender === 'function') {
        editor.opts.igamtExampleMessageRender(editor, true);
      }
      attachRemoveControls();
      ensureEditableAroundExampleMessages(editor.$el);
      placeCaretAfter(editor.$el.find('.' + WIDGET_CLASS).last());
    }

    function attachRemoveControls() {
      const jq = getJq();
      if (!jq || !editor.$el) {
        return;
      }
      jq(editor.$el).find('.' + WIDGET_CLASS).each(function() {
        ensureRemoveButton(this);
      });
    }

    function ensureEditableAfterWidgets() {
      ensureEditableAroundExampleMessages(editor.$el);
    }

    function placeCaretIn($node, atEnd?: boolean) {
      if (!$node || !$node.length) {
        return;
      }
      const node = $node.get(0);
      try {
        if (atEnd && editor.selection && editor.selection.setAtEnd) {
          editor.selection.setAtEnd(node);
        } else if (editor.selection && editor.selection.setAtStart) {
          editor.selection.setAtStart(node);
        }
        if (editor.selection && editor.selection.restore) {
          editor.selection.restore();
        }
      } catch (e) { }
      try {
        const sel = window.getSelection();
        const range = document.createRange();
        range.selectNodeContents(node);
        range.collapse(!atEnd);
        sel.removeAllRanges();
        sel.addRange(range);
      } catch (e2) { }
    }

    function placeCaretBefore($widget) {
      if (!$widget || !$widget.length) {
        return;
      }
      let $prev = $widget.prev();
      if (!$prev.length || $prev.hasClass(WIDGET_CLASS)) {
        $widget.before('<p><br></p>');
        $prev = $widget.prev();
      }
      placeCaretIn($prev, true);
    }

    function placeCaretAfter($widget) {
      if (!$widget || !$widget.length) {
        return;
      }
      let $next = $widget.next();
      if (!$next.length || $next.hasClass(WIDGET_CLASS)) {
        $widget.after('<p><br></p>');
        $next = $widget.next();
      }
      placeCaretIn($next, false);
    }

    function select($widget) {
      const jq = getJq();
      jq(editor.$el).find('.' + WIDGET_CLASS).removeClass(SELECTED_CLASS);
      $current = $widget && $widget.length ? $widget : null;
      if ($current) {
        $current.addClass(SELECTED_CLASS);
      }
    }

    function hidePopup() {
      if (editor.popups && editor.popups.hide) {
        editor.popups.hide('exampleMessage.edit');
      }
    }

    function initPopup() {
      const template = {
        buttons: '<div class="fr-buttons">' + editor.button.buildList(['removeExampleMessage']) + '</div>',
      };
      return editor.popups.create('exampleMessage.edit', template);
    }

    function showEditPopup($widget) {
      if (!editor.popups) {
        return;
      }
      select($widget);
      let $popup = editor.popups.get('exampleMessage.edit');
      if (!$popup) {
        $popup = initPopup();
      }
      editor.popups.setContainer('exampleMessage.edit', editor.$sc || editor.$box);
      const left = $widget.offset().left + $widget.outerWidth() / 2;
      const top = $widget.offset().top;
      editor.popups.show('exampleMessage.edit', left, top, $widget.outerHeight());
    }

    function remove($widget?) {
      const $target = $widget && $widget.length ? $widget : $current;
      if (!$target || !$target.length) {
        return;
      }
      const $after = $target.next();
      hidePopup();
      select(null);
      editor.undo.saveStep();
      $target.remove();
      if (!$after.length) {
        editor.html.insert('<p><br></p>', true);
      } else if (editor.selection && editor.selection.setAtStart) {
        try {
          editor.selection.setAtStart($after.get(0));
          if (editor.selection.restore) {
            editor.selection.restore();
          }
        } catch (e) { }
      }
      editor.undo.saveStep();
      if (editor.events && editor.events.trigger) {
        editor.events.trigger('contentChanged');
      }
    }

    function _init() {
      const jq = getJq();
      editor.events.on('html.set', function() {
        renderAll();
        ensureEditableAfterWidgets();
      });
      editor.events.on('html.processGet', function(html) {
        if (!html) {
          return html;
        }
        return String(html)
          .replace(/<button[^>]*fr-exmsg-remove[^>]*>[\s\S]*?<\/button>/gi, '')
          .replace(/<div[^>]*fr-exmsg-views[^>]*>[\s\S]*?<\/div>/gi, '');
      });
      editor.events.$on(editor.$el, 'click', 'a.' + LINK_CLASS, function(e) {
        if (!e.ctrlKey && !e.metaKey) {
          e.preventDefault();
        }
      });
      editor.events.$on(editor.$el, 'click', '.' + VIEW_BTN_CLASS, function(e) {
        e.preventDefault();
        e.stopPropagation();
        const jq = getJq();
        const $btn = jq(this);
        const view = $btn.attr('data-view') || 'reduced';
        const $widget = $btn.closest('.' + WIDGET_CLASS);
        $widget.attr('data-view', view);
        if (typeof editor.opts.igamtExampleMessageRender === 'function') {
          editor.opts.igamtExampleMessageRender(editor, true);
        }
      });
      editor.events.$on(editor.$el, 'click', '.' + REMOVE_CLASS, function(e) {
        e.preventDefault();
        e.stopPropagation();
        remove(jq(this).closest('.' + WIDGET_CLASS));
      });
      editor.events.$on(editor.$el, 'click', '.' + WIDGET_CLASS, function(e) {
        e.preventDefault();
        e.stopPropagation();
        showEditPopup(jq(this));
      });
      editor.events.on('click', function(e) {
        const jq = getJq();
        if (e && jq(e.target).closest('.' + WIDGET_CLASS).length) {
          return;
        }
        if ($current) {
          select(null);
          hidePopup();
        }
      });
      editor.events.on('keydown', function(e) {
        if ($current && $current.length && (e.which === 8 || e.which === 46)) {
          e.preventDefault();
          remove($current);
          return false;
        }
      });
      editor.events.$on(editor.$el, 'mousedown', function(e) {
        const jq = getJq();
        const $target = jq(e.target);
        if ($target.closest('.' + WIDGET_CLASS).length) {
          return;
        }
        ensureEditableAfterWidgets();
        const $p = $target.closest('p');
        if ($p.length && editor.$el.has($p).length) {
          const hasText = ($p.text() || '').replace(/\u00a0/g, '').trim().length > 0;
          if (hasText) {
            return;
          }
          if ($p.next().hasClass(WIDGET_CLASS)) {
            e.preventDefault();
            select(null);
            hidePopup();
            placeCaretIn($p, true);
            return false;
          }
          if ($p.prev().hasClass(WIDGET_CLASS)) {
            e.preventDefault();
            select(null);
            hidePopup();
            placeCaretIn($p, false);
            return false;
          }
          return;
        }
        const y = e.clientY;
        const widgets = editor.$el.find('.' + WIDGET_CLASS);
        for (let i = 0; i < widgets.length; i++) {
          const $widget = jq(widgets[i]);
          const rect = widgets[i].getBoundingClientRect();
          const nextTop = (i < widgets.length - 1)
            ? widgets[i + 1].getBoundingClientRect().top
            : Number.POSITIVE_INFINITY;
          if (y < rect.top) {
            e.preventDefault();
            select(null);
            hidePopup();
            placeCaretBefore($widget);
            return false;
          }
          if (y > rect.bottom && y < nextTop) {
            e.preventDefault();
            select(null);
            hidePopup();
            placeCaretAfter($widget);
            return false;
          }
        }
      });
      editor.events.on('contentChanged', function() {
        ensureEditableAfterWidgets();
      });
      setTimeout(function() {
        renderAll();
        ensureEditableAfterWidgets();
      }, 0);
    }

    return {
      _init: _init,
      pick: pick,
      insertWidget: insertWidget,
      renderAll: renderAll,
      remove: remove,
    };
  };

  FE.DefineIconTemplate('igamt_em_label', '<span class="badge badge-primary igamt-em-toolbar-btn">[NAME]</span>');
  FE.DefineIcon('insertExampleMessage', { NAME: 'M', template: 'igamt_em_label' });

  if (alreadyRegistered) {
    return;
  }

  FE.RegisterCommand('insertExampleMessage', {
    title: 'Insert Example Messages',
    icon: 'insertExampleMessage',
    undo: true,
    focus: false,
    plugin: 'exampleMessage',
    callback: function() {
      this.exampleMessage.pick();
    },
  });

  FE.DefineIcon('removeExampleMessage', { NAME: 'trash' });
  FE.RegisterCommand('removeExampleMessage', {
    title: 'Remove Example Message',
    icon: 'removeExampleMessage',
    undo: true,
    focus: false,
    plugin: 'exampleMessage',
    callback: function() {
      this.exampleMessage.remove();
    },
  });
}

export function withExampleMessageToolbarButton(buttons: any): any {
  if (!buttons || !Array.isArray(buttons)) {
    return buttons;
  }
  if (buttons.indexOf('insertExampleMessage') !== -1) {
    return buttons;
  }
  const next = buttons.slice();
  const linkIndex = next.indexOf('insertLink');
  if (linkIndex >= 0) {
    next.splice(linkIndex + 1, 0, 'insertExampleMessage');
  } else {
    next.push('insertExampleMessage');
  }
  return next;
}

export function froalaToolbarButtons(fe: any, size?: 'MD' | 'SM' | 'XS'): any[] {
  const defaults = fe && fe.DEFAULTS ? fe.DEFAULTS : {};
  if (!size) {
    return copyToolbar(defaults.toolbarButtons || (fe && fe.TOOLBAR_BUTTONS));
  }
  const key = 'toolbarButtons' + size;
  const feKey = 'TOOLBAR_BUTTONS_' + size;
  return copyToolbar(defaults[key] || (fe && fe[feKey]));
}

function copyToolbar(buttons: any): any[] {
  return Array.isArray(buttons) ? buttons.slice() : [];
}

export function ensureEditableAroundExampleMessages(root: any) {
  const jq = getJq();
  if (!jq || !root) {
    return;
  }
  jq(root).find('.' + WIDGET_CLASS).each(function() {
    const $widget = jq(this);
    const $prev = $widget.prev();
    if (!$prev.length || $prev.hasClass(WIDGET_CLASS)) {
      $widget.before('<p><br></p>');
    }
    const $next = $widget.next();
    if (!$next.length || $next.hasClass(WIDGET_CLASS)) {
      $widget.after('<p><br></p>');
    }
  });
}

export function ensureEditableAfterExampleMessages(root: any) {
  ensureEditableAroundExampleMessages(root);
}

export function buildExampleMessageWidgetHtml(
  igId: string,
  messageId: string,
  snippetId: string,
  label: string,
): string {
  const href = buildExampleMessageHref(igId, messageId, snippetId);
  const caption = escapeHtml(label ? ('Example: ' + label) : 'Example message');
  return (
    '<div class="' + WIDGET_CLASS + ' fr-deletable" contenteditable="false"' +
    ' data-message-id="' + escapeAttr(messageId) + '"' +
    ' data-snippet-id="' + escapeAttr(snippetId || '') + '"' +
    ' data-view="' + (snippetId ? 'reduced' : 'full') + '"' +
    ' data-label="' + escapeAttr(label || '') + '">' +
    '<a class="' + LINK_CLASS + '" href="' + escapeAttr(href) + '">' + caption + '</a>' +
    '<pre class="' + BODY_CLASS + '">Loading…</pre>' +
    '</div>'
  );
}

export function buildExampleMessageHref(igId: string, messageId: string, snippetId?: string): string {
  let href = '/example-messages/' + igId + '/message/' + messageId;
  if (snippetId) {
    href += '?snippetId=' + encodeURIComponent(snippetId);
  }
  return href;
}

export function findExampleMessageWidgets(root: any): any[] {
  const jq = getJq();
  if (!jq || !root) {
    return [];
  }
  return jq(root).find('.' + WIDGET_CLASS).toArray();
}

export interface IExampleMessageHighlightRange {
  startLine: number;
  startCol: number;
  endLine: number;
  endCol: number;
}

export function setExampleMessageWidgetBody(
  widget: HTMLElement,
  body: string,
  missing?: boolean,
  highlights?: IExampleMessageHighlightRange[],
) {
  const jq = getJq();
  if (!jq || !widget) {
    return;
  }
  let pre = jq(widget).children('.' + BODY_CLASS);
  if (!pre.length) {
    pre = jq('<pre class="' + BODY_CLASS + '"></pre>');
    jq(widget).append(pre);
  }
  pre.html(styleEr7Html(body || '', highlights || []));
  jq(widget).toggleClass('fr-exmsg-missing', !!missing);
  ensureRemoveButton(widget);
  ensureViewToggle(widget);
}

export function widgetViewMode(widget: HTMLElement): 'full' | 'reduced' {
  return widget && widget.getAttribute('data-view') === 'full' ? 'full' : 'reduced';
}

export function ensureViewToggle(widget: HTMLElement) {
  const jq = getJq();
  if (!jq || !widget) {
    return;
  }
  const $widget = jq(widget);
  const snippetId = $widget.attr('data-snippet-id');
  $widget.children('.' + VIEW_BAR_CLASS).remove();
  if (!snippetId) {
    return;
  }
  const view = widgetViewMode(widget);
  $widget.children('.' + LINK_CLASS).after(
    '<div class="' + VIEW_BAR_CLASS + '" contenteditable="false">' +
      '<span class="fr-exmsg-view-label">View:</span>' +
      '<button type="button" class="' + VIEW_BTN_CLASS + (view === 'reduced' ? ' active' : '') + '" data-view="reduced">Snippet Only</button>' +
      '<button type="button" class="' + VIEW_BTN_CLASS + (view === 'full' ? ' active' : '') + '" data-view="full">Full Message</button>' +
    '</div>'
  );
}

const ER7_TOKEN_STYLE: { [token: string]: string } = {
  'segment-name': 'font-weight:bold;color:black',
  'field-separator': 'color:#0707f1',
  'component-separator': 'color:#ac2200',
  'subcomponent-separator': 'color:rgb(0, 146, 0)',
  'continuation-separator': 'color:#FF00FF',
};

export function styleEr7Html(body: string, highlights?: IExampleMessageHighlightRange[]): string {
  if (!body) {
    return '';
  }
  const lines = String(body).split(/\r\n|\n|\r/);
  const marked = lines.map((line) => {
    const flags = [];
    for (let i = 0; i < line.length; i++) {
      flags.push(false);
    }
    return flags;
  });
  const ranges = highlights || [];
  for (let r = 0; r < ranges.length; r++) {
    markHighlightRange(lines, marked, ranges[r]);
  }
  return lines.map((line, lineIdx) => styleEr7Line(line, marked[lineIdx])).join('\n');
}

function markHighlightRange(
  lines: string[],
  marked: boolean[][],
  range: IExampleMessageHighlightRange,
) {
  if (!range || !lines.length) {
    return;
  }
  const startLine = clamp(range.startLine, 0, lines.length - 1);
  const endLine = clamp(range.endLine, 0, lines.length - 1);
  const fromLine = Math.min(startLine, endLine);
  const toLine = Math.max(startLine, endLine);
  for (let lineIdx = fromLine; lineIdx <= toLine; lineIdx++) {
    const startCol = lineIdx === fromLine ? Math.max(0, range.startCol) : 0;
    const endCol = lineIdx === toLine ? range.endCol : lines[lineIdx].length;
    const fromCol = Math.max(0, Math.min(startCol, lines[lineIdx].length));
    const toCol = Math.max(fromCol, Math.min(endCol, lines[lineIdx].length));
    for (let col = fromCol; col < toCol; col++) {
      marked[lineIdx][col] = true;
    }
  }
}

function styleEr7Line(line: string, flags: boolean[]): string {
  if (!line) {
    return '';
  }
  let html = '';
  let i = 0;
  while (i < line.length) {
    const highlighted = !!flags[i];
    let j = i + 1;
    while (j < line.length && !!flags[j] === highlighted) {
      j += 1;
    }
    const inner = styleEr7Tokens(line, i, j);
    html += highlighted
      ? '<span class="cm-highlight" style="background-color:#f3c60b;padding-top:5px;padding-bottom:5px;box-shadow:0px 5px 2px -2px gray">' + inner + '</span>'
      : inner;
    i = j;
  }
  return html;
}

function styleEr7Tokens(line: string, from: number, to: number): string {
  let html = '';
  let i = from;
  while (i < to) {
    const token = er7TokenClass(line, i);
    let j = i + 1;
    while (j < to && er7TokenClass(line, j) === token) {
      j += 1;
    }
    const text = escapeHtml(line.substring(i, j));
    if (token) {
      html += '<span class="cm-' + token + '" style="' + ER7_TOKEN_STYLE[token] + '">' + text + '</span>';
    } else {
      html += text;
    }
    i = j;
  }
  return html;
}

function er7TokenClass(line: string, column: number): string {
  if (column <= 2) {
    return 'segment-name';
  }
  const ch = line.charAt(column);
  if (ch === '|') {
    return 'field-separator';
  }
  if (ch === '^') {
    return 'component-separator';
  }
  if (ch === '&') {
    return 'subcomponent-separator';
  }
  if (ch === '~') {
    return 'continuation-separator';
  }
  return '';
}

function clamp(value: number, min: number, max: number): number {
  if (value < min) {
    return min;
  }
  if (value > max) {
    return max;
  }
  return value;
}

export function ensureRemoveButton(widget: HTMLElement) {
  const jq = getJq();
  if (!jq || !widget) {
    return;
  }
  const $widget = jq(widget);
  if ($widget.children('.' + REMOVE_CLASS).length) {
    return;
  }
  $widget.prepend(
    '<button type="button" class="' + REMOVE_CLASS + '" title="Remove example message" contenteditable="false">&times;</button>',
  );
}

export function escapeHtml(value: string): string {
  return (value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escapeAttr(value: string): string {
  return escapeHtml(value).replace(/'/g, '&#39;');
}

export { WIDGET_CLASS, BODY_CLASS, LINK_CLASS, REMOVE_CLASS, VIEW_BAR_CLASS, VIEW_BTN_CLASS };
