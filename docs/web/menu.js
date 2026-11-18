// The language menu in the header (details.lang): closes again on a click elsewhere or on Escape,
// as menus do – without this script it stays usable, it just closes only on its own button.
(function () {
  var menu = document.querySelector('details.lang');
  if (!menu) return;
  document.addEventListener('click', function (event) {
    if (menu.open && !menu.contains(event.target)) menu.open = false;
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && menu.open) { menu.open = false; menu.querySelector('summary').focus(); }
  });
})();
