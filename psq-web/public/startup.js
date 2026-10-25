(function () {
  var panel, title, message, help;
  var ready = false;
  var pendingFailure = false;
  var timer;

  function failed() {
    if (ready) return;
    pendingFailure = true;
    window.clearTimeout(timer);
    if (!panel) return;
    panel.hidden = false;
    panel.setAttribute('role', 'alert');
    title.textContent = '暂时无法打开 PSQ';
    message.textContent = '页面未能完成加载，请重新加载后重试。';
    help.hidden = false;
  }
  function complete() {
    ready = true;
    window.clearTimeout(timer);
    if (panel) panel.hidden = true;
  }

  document.addEventListener('psq:ready', complete);
  window.addEventListener('error', function (event) {
    if (event.target === window || event.target && event.target.tagName === 'SCRIPT') failed();
  }, true);
  function initialize() {
    panel = document.getElementById('psq-startup');
    if (!panel) return;
    title = document.getElementById('psq-startup-title');
    message = document.getElementById('psq-startup-message');
    help = document.getElementById('psq-startup-help');
    var root = document.getElementById('root');
    if (ready || root && root.getAttribute('data-psq-ready') === 'true') { complete(); return; }
    panel.hidden = false;
    panel.setAttribute('role', 'status');
    if (pendingFailure || !('noModule' in document.createElement('script'))) failed();
    else timer = window.setTimeout(failed, 20000);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize);
  else initialize();
}());
