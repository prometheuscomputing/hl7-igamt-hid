const WIDGET_CLASS = 'fr-exmsg';
const BODY_CLASS = 'fr-exmsg-body';
const LINK_CLASS = 'fr-exmsg-link';
const REMOVE_CLASS = 'fr-exmsg-remove';
const SELECTED_CLASS = 'fr-exmsg-selected';

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
        return String(html).replace(/<button[^>]*fr-exmsg-remove[^>]*>[\s\S]*?<\/button>/gi, '');
      });
      editor.events.$on(editor.$el, 'click', 'a.' + LINK_CLASS, function(e) {
        if (!e.ctrlKey && !e.metaKey) {
          e.preventDefault();
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

  if (alreadyRegistered) {
    return;
  }

  FE.DefineIcon('insertExampleMessage', { NAME: 'file-text-o' });
  FE.RegisterCommand('insertExampleMessage', {
    title: 'Insert Example Message',
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

export function setExampleMessageWidgetBody(widget: HTMLElement, body: string, missing?: boolean) {
  const jq = getJq();
  if (!jq || !widget) {
    return;
  }
  let pre = jq(widget).children('.' + BODY_CLASS);
  if (!pre.length) {
    pre = jq('<pre class="' + BODY_CLASS + '"></pre>');
    jq(widget).append(pre);
  }
  pre.text(body || '');
  jq(widget).toggleClass('fr-exmsg-missing', !!missing);
  ensureRemoveButton(widget);
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

export { WIDGET_CLASS, BODY_CLASS, LINK_CLASS, REMOVE_CLASS };
