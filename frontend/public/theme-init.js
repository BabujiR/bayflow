// Applies the saved light/dark theme before the first paint, to avoid a flash of the wrong
// theme. Kept as a file (not inline) so the Content-Security-Policy can forbid inline scripts.
(function () {
  try {
    var saved = localStorage.getItem('bayflow-theme');
    var dark = saved === 'dark' || (saved !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
    if (dark) document.documentElement.classList.add('dark');
  } catch (e) {
    // Storage blocked: fall back to the light theme.
  }
})();
