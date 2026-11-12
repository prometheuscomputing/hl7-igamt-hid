(function(){document.querySelectorAll('.toc-panel a.toc-link').forEach(function(a){a.addEventListener('click',function(e){e.stopPropagation();});});var ex=document.getElementById('toc-expand-all');var cl=document.getElementById('toc-collapse-all');if(ex)ex.addEventListener('click',function(){document.querySelectorAll('.toc-panel details').forEach(function(d){d.open=true;});});if(cl)cl.addEventListener('click',function(){document.querySelectorAll('.toc-panel details').forEach(function(d){d.open=false;});});var sx=document.getElementById('struct-expand-all');var sc=document.getElementById('struct-collapse-all');if(sx)sx.addEventListener('click',function(){document.querySelectorAll('#msg-struct-tree details').forEach(function(d){d.open=true;});});if(sc)sc.addEventListener('click',function(){document.querySelectorAll('#msg-struct-tree details').forEach(function(d){d.open=false;});});var current=document.querySelector('[aria-current=page]');if(current)current.scrollIntoView({block:'nearest'});function focusHash(){var target=document.getElementById(decodeURIComponent(location.hash.slice(1)));if(target){target.focus();target.scrollIntoView();}}window.addEventListener('hashchange',focusHash);focusHash();})();

// Progressive enhancement: the static page URL remains usable without JavaScript.
(function () {
  var heading = document.querySelector('.page h1');
  if (!heading) return;
  var tools = document.createElement('div');
  tools.className = 'permalink-tools';
  var button = document.createElement('button');
  button.type = 'button';
  button.textContent = 'Copy permanent link';
  var status = document.createElement('span');
  status.setAttribute('role', 'status');
  status.setAttribute('aria-live', 'polite');
  button.addEventListener('click', function () {
    var url = window.location.href;
    function manualCopy() {
      var field = tools.querySelector('input');
      if (!field) {
        field = document.createElement('input');
        field.type = 'text';
        field.readOnly = true;
        field.setAttribute('aria-label', 'Permanent link ready to copy');
        tools.appendChild(field);
      }
      field.value = url;
      field.focus();
      field.select();
      status.textContent = 'Link selected. Use your usual copy shortcut.';
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(function () {
        status.textContent = 'Permanent link copied.';
      }).catch(manualCopy);
    } else {
      manualCopy();
    }
  });
  tools.appendChild(button);
  tools.appendChild(status);
  heading.parentNode.insertBefore(tools, heading.nextSibling);
})();
