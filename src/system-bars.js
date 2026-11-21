(() => {
  if (window.__rpdSystemBars || !window.AndroidSystemBars) return;
  window.__rpdSystemBars = true;

  function rgb(color) {
    if (!color || color === 'transparent') return null;
    if (/^#[0-9a-f]{6}$/i.test(color)) return color;
    const matchRgb = color.match(/^rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s\/]+([\d.]+))?\s*\)$/i);
    if (matchRgb) {
      if (matchRgb[4] === '0') return null;
      return '#' + matchRgb.slice(1, 4).map(value => Number(value).toString(16).padStart(2, '0')).join('');
    }
    const oklch = color.match(/^oklch\(\s*([\d.]+)(%?)\s+([\d.]+)(%?)\s+(-?[\d.]+)(?:deg)?(?:\s*\/\s*([\d.]+%?))?\s*\)$/i);
    if (!oklch || oklch[6] === '0') return null;
    const L = Number(oklch[1]) / (oklch[2] ? 100 : 1);
    const C = Number(oklch[3]) / (oklch[4] ? 100 : 1);
    const hue = Number(oklch[5]) * Math.PI / 180;
    const a = C * Math.cos(hue);
    const b = C * Math.sin(hue);
    const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
    const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
    const s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3;
    const linear = [
      4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
      -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
      -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
    ];
    return '#' + linear.map(value => {
      const gamma = value <= 0.0031308 ? 12.92 * value : 1.055 * value ** (1 / 2.4) - 0.055;
      return Math.round(Math.max(0, Math.min(1, gamma)) * 255).toString(16).padStart(2, '0');
    }).join('');
  }

  function token(name) {
    return rgb(getComputedStyle(document.documentElement).getPropertyValue(name).trim());
  }

  function visibleColor(y) {
    const elements = document.elementsFromPoint(innerWidth / 2, y);
    for (const element of elements) {
      const style = getComputedStyle(element);
      if (style.visibility !== 'hidden' && style.backgroundColor !== 'rgba(0, 0, 0, 0)') {
        const color = rgb(style.backgroundColor);
        if (color) return color;
      }
    }
    return null;
  }

  let previous = '';
  let timer;
  function update() {
    clearTimeout(timer);
    timer = setTimeout(() => {
      if (!document.body || innerWidth < 1 || innerHeight < 1) return;
      const base = token('--surface-base');
      if (!base) return;
      const profile = /^\/profile\/?$/.test(location.pathname);
      const auth = /^\/(login|update-password)\/?$/.test(location.pathname);
      const nav = document.querySelector('[data-slot="mobile-nav"]');
      const hasNav = !auth && nav && nav.getBoundingClientRect().height > 0 &&
        getComputedStyle(nav).visibility !== 'hidden';
      const status = auth ? base : profile
        ? token('--surface-gray-1') || base
        : visibleColor(Math.min(12, innerHeight - 1)) || base;
      const navigation = hasNav
        ? rgb(getComputedStyle(nav).backgroundColor) || token('--surface-elevation-2') || base
        : auth ? base
        : profile ? token('--surface-gray-1') || base
        : visibleColor(Math.max(0, innerHeight - 2)) || base;
      const colors = JSON.stringify({ status, navigation });
      if (colors !== previous) {
        previous = colors;
        window.AndroidSystemBars.postMessage(colors);
      }
    }, 80);
  }

  function observe() {
    if (!document.documentElement) {
      addEventListener('DOMContentLoaded', observe, { once: true });
      return;
    }
    new MutationObserver(update).observe(document.documentElement, {
      attributes: true,
      childList: true,
      subtree: true,
      attributeFilter: ['class', 'style', 'data-theme', 'hidden'],
    });
    update();
  }
  addEventListener('load', update);
  addEventListener('pageshow', update);
  addEventListener('resize', update);
  addEventListener('scroll', update, { capture: true, passive: true });
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', update);
  observe();

  if (window.AndroidDownload) {
    const blobs = new Map();
    const createUrl = URL.createObjectURL.bind(URL);
    const revokeUrl = URL.revokeObjectURL.bind(URL);
    const anchorClick = HTMLAnchorElement.prototype.click;
    URL.createObjectURL = function (obj) {
      const url = createUrl(obj);
      if (obj instanceof Blob) blobs.set(url, obj);
      return url;
    };
    URL.revokeObjectURL = function (url) {
      setTimeout(() => blobs.delete(url), 5000);
      return revokeUrl(url);
    };
    HTMLAnchorElement.prototype.click = function () {
      const blob = this.href && blobs.get(this.href);
      if (blob && this.hasAttribute('download')) {
        const name = this.getAttribute('download') || 'download';
        const fr = new FileReader();
        fr.onloadend = () => window.AndroidDownload.save(fr.result, name, blob.type || '');
        fr.readAsDataURL(blob);
        return;
      }
      return anchorClick.apply(this, arguments);
    };
  }
})();
