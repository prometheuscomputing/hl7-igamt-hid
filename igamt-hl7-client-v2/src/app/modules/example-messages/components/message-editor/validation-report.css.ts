export const VALIDATION_REPORT_CSS = `
body { font-family: Arial, Helvetica, sans-serif; color: #222; margin: 16px; }
.row4 a, .row3 a { color: #003399; text-decoration: underline; }
.headerReport { width: 250px; }
.row1 {
  font-weight: bold;
  font-size: 15px;
  color: #000;
  vertical-align: top;
  background-color: #d9edf7;
  width: 100px;
  padding: 8px;
}
.row2 { background-color: #f0f0f0; width: 100px; padding: 8px; }
.row3 { vertical-align: top; padding: 8px; }
.row5 { font-weight: bold; background-color: #FFD630; vertical-align: top; padding: 8px; }
.row6 { vertical-align: top; background-color: #f0f0f0; width: 100px; padding: 8px; }
.forumline {
  background-color: #fff;
  border: 1px solid #006699;
  width: 100%;
  border-collapse: collapse;
}
.forumline td, .forumline th { padding: 8px; }
.maintitle { font-weight: bold; font-size: 22px; font-family: Georgia, Verdana; color: #000; }
.title-background { background-color: #d9edf7; }
.report-section { margin-bottom: 20px; }
.border_bottom { border-bottom: 1pt solid #006699; }
.border_right { border-right: 1pt solid #006699; }
.alternate0 { background: #fff; }
.alternate1 { background: #f0f0f0; }
textarea { font-family: Menlo, Monaco, Consolas, monospace; font-size: 12px; }
`;

export const VALIDATION_REPORT_SCRIPT = `
function toggle_visibility(id, elm) {
  var e = document.getElementById(id);
  if (!e) { return; }
  e.style.display = elm.checked ? '' : 'none';
}
function toggle_visibilityC(cls, elm) {
  var e = document.getElementsByClassName(cls);
  for (var i = 0; i < e.length; i++) {
    e[i].style.display = elm.checked ? '' : 'none';
  }
}
function fi(id) {
  var div = document.getElementById(id);
  var btn = document.getElementById('btn');
  if (!div) { return; }
  if (div.style.display !== 'none') {
    if (btn && btn.childNodes[0]) { btn.childNodes[0].nodeValue = 'View'; }
    div.style.display = 'none';
  } else {
    if (btn && btn.childNodes[0]) { btn.childNodes[0].nodeValue = 'Hide'; }
    div.style.display = '';
  }
}
function ShowSep(id) {
  var tabC = document.getElementById('msgC' + id);
  var tabS = document.getElementById('msgS' + id);
  if (tabC) { tabC.style.display = 'none'; }
  if (tabS) { tabS.style.display = ''; }
}
function HideSep(id) {
  var tabC = document.getElementById('msgC' + id);
  var tabS = document.getElementById('msgS' + id);
  if (tabC) { tabC.style.display = ''; }
  if (tabS) { tabS.style.display = 'none'; }
}
`;
