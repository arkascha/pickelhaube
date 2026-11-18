// The language menu in the header (details.lang): closes again on a click elsewhere or on Escape,
// as menus do – without this script it stays usable, it just closes only on its own button.
// Choosing a language there is remembered in the cookie pickelhaube_lang ("en" or "de", one year):
// the server then opens the home page, /pickelhaube/, in that language instead of the browser's
// (see the privacy policy, section 9). Only a choice made here sets it.
(function () {
  var menu = document.querySelector('details.lang');
  if (!menu) return;
  document.addEventListener('click', function (event) {
    if (menu.open && !menu.contains(event.target)) menu.open = false;
  });
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && menu.open) { menu.open = false; menu.querySelector('summary').focus(); }
  });
  var links = menu.querySelectorAll('li a[lang]');
  for (var i = 0; i < links.length; i++) {
    links[i].addEventListener('click', function () {
      var lang = this.getAttribute('lang');
      if (lang !== 'en' && lang !== 'de') return;
      document.cookie = 'pickelhaube_lang=' + lang + '; path=/pickelhaube; max-age=31536000; SameSite=Lax' +
        (location.protocol === 'https:' ? '; Secure' : '');
    });
  }
})();
