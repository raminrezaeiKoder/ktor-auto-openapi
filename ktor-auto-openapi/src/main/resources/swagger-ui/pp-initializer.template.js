/* ParvanPajooh / — Swagger custom shell
   - Light/Dark toggle with persistence
   - Collapse/Expand all
   - Fixed footer spacing
   - Clean intro line; remove title badges
   - Minimal one-line servers toolbar (no card)
   - Auto height sync with first tag
   - Works with: __SPEC_URL__
*/
(function () {
  const SPEC_URL = "__SPEC_URL__";
  if (window.__ppBuilt) return;
  window.__ppBuilt = true;

  // ---------- THEME ----------
  const LS_KEY = "ppTheme";
  const prefersDark = () =>
    window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;

  function getTheme() {
    return localStorage.getItem(LS_KEY) || (prefersDark() ? "dark" : "light");
  }
  function setTheme(t) {
    document.documentElement.setAttribute("data-theme", t);
    localStorage.setItem(LS_KEY, t);
    const hdr = document.querySelector(".pp-header");
    if (hdr) hdr.classList.toggle("is-dark", t === "dark");
  }
  setTheme(getTheme());

  // ---------- HEADER ----------
  function addHeader() {
    if (document.querySelector(".pp-header")) return;
    const hdr = document.createElement("div");
    hdr.className = "pp-header";
    hdr.innerHTML = `
      <div class="pp-header__brand">
        <img class="pp-logo" src="pp-logo.svg" alt="" />
        <span class="pp-title">PARVAN PAJOOH <span class="accent">swagger</span></span>
      </div>
      <div class="pp-header__tools">
        <button class="pp-btn" id="pp-theme-toggle" title="Toggle theme" aria-label="Toggle theme">
          <span class="pp-icon-sun">☀︎</span>
          <span class="pp-icon-moon">◐</span>
        </button>
        <button class="pp-btn" id="pp-collapse-all" title="Collapse all">Collapse all</button>
        <button class="pp-btn" id="pp-expand-all" title="Expand all">Expand all</button>
      </div>
    `;
    document.body.prepend(hdr);

    hdr.querySelector("#pp-theme-toggle").addEventListener("click", () => {
      setTheme(getTheme() === "dark" ? "light" : "dark");
      reserveAllScrollContainers();
    });
    hdr.classList.toggle("is-dark", getTheme() === "dark");

    hdr.querySelector("#pp-collapse-all").addEventListener("click", () => {
      document.querySelectorAll("#swagger-ui h3.opblock-tag").forEach(h => {
        if (/is-open/.test(h.className)) h.click();
      });
    });
    hdr.querySelector("#pp-expand-all").addEventListener("click", () => {
      document.querySelectorAll("#swagger-ui h3.opblock-tag").forEach(h => {
        if (!/is-open/.test(h.className)) h.click();
      });
    });
  }

  // ---------- FOOTER ----------
  function addFooter() {
    if (document.querySelector(".pp-footer")) return;
    const f = document.createElement("div");
    f.className = "pp-footer";
    f.innerHTML = `<div class="pp-footer__inner">Developed by <strong>Ramin Rezaei</strong></div>`;
    document.body.appendChild(f);
  }
  function addFooterSpacer() {
    if (document.querySelector(".pp-footer-spacer")) return;
    const spacer = document.createElement("div");
    spacer.className = "pp-footer-spacer";
    document.body.appendChild(spacer);

    const footer = document.querySelector(".pp-footer");
    const update = () => {
      const h = Math.ceil(footer?.getBoundingClientRect().height || 56);
      spacer.style.height = `${h}px`;
    };
    if (footer && window.ResizeObserver) new ResizeObserver(update).observe(footer);
    window.addEventListener("resize", update);
    update();
  }
  function reserveAllScrollContainers() {
    const footer = document.querySelector(".pp-footer");
    const h = Math.ceil(footer?.getBoundingClientRect().height || 56);
    document.body.style.paddingBottom = `${h}px`;
    const root = document.querySelector("#swagger-ui");
    const column = document.querySelector("#swagger-ui .swagger-ui");
    if (root)   root.style.paddingBottom   = `${h}px`;
    if (column) column.style.paddingBottom = `${h}px`;
  }
  function watchFooter() {
    const footer = document.querySelector(".pp-footer");
    const updateAll = () => reserveAllScrollContainers();
    if (footer && window.ResizeObserver) new ResizeObserver(updateAll).observe(footer);
    window.addEventListener("resize", updateAll);
    updateAll();
  }

  // ---------- Height sync ----------
  function syncControlsHeightToTag() {
    const firstTag = document.querySelector('#swagger-ui h3.opblock-tag');
    const root = document.documentElement;
    const h = firstTag ? Math.round(firstTag.getBoundingClientRect().height) : 56;
    root.style.setProperty('--pp-controls-h', `${h}px`);
  }

  // ---------- Minimal servers bar ----------
function buildMinimalServersBar() {
  const ui = document.querySelector('#swagger-ui');
  const sc = ui?.querySelector('.scheme-container');
  if (!ui || !sc) return;

  // Find ALL server controls in the whole scheme container (some themes render 2)
  const allControls = Array.from(
    sc.querySelectorAll('select, input')
  );

  // Choose ONE primary control: prefer <select>, else first <input>
  const primary =
    allControls.find(el => el.tagName === 'SELECT') ||
    allControls[0] ||
    null;

  // Build the slim toolbar
  const bar = document.createElement('div');
  bar.className = 'pp-controls-bar';
  bar.setAttribute('role', 'toolbar');

  const left = document.createElement('div');
  left.className = 'pp-controls-left';

  // Always show a clean label
  const lab = document.createElement('label');
  lab.textContent = 'Servers';
  left.appendChild(lab);

  if (primary) {
    left.appendChild(primary); // move the real node so events/behavior remain intact
  }

  const spacer = document.createElement('div');
  spacer.className = 'pp-controls-spacer';

  // Move the real "Authorize" button (keep its handlers)
  const authorizeWrapper = sc.querySelector('.auth-wrapper, .authorize-wrapper, .auth-btn-wrapper, .authorize');
  if (authorizeWrapper) {
    const authBtn = authorizeWrapper.querySelector('button, .authorize__btn') || authorizeWrapper;
    if (authBtn) bar.appendChild(left), bar.appendChild(spacer), bar.appendChild(authBtn);
    else bar.appendChild(left), bar.appendChild(spacer);
  } else {
    bar.appendChild(left);
    bar.appendChild(spacer);
  }

  // Insert bar above and nuke the original box
  sc.parentNode.insertBefore(bar, sc);
  sc.remove();

  // Remove any leftover server controls anywhere else (defensive)
  // (e.g., some builds duplicate in detached fragments)
  Array.from(ui.querySelectorAll('.scheme-container select, .scheme-container input'))
    .forEach(el => { if (el !== primary) el.remove(); });

  // Final sanity: if somehow two inputs ended up in the bar, keep only the first
  const dupes = bar.querySelectorAll('select, input');
  for (let i = 1; i < dupes.length; i++) dupes[i].remove();

  // Sync height to first tag header
  syncControlsHeightToTag();
}

  // ---------- Header polish ----------
  function enhanceApiHeaderNoSticky() {
    const ui = document.querySelector('#swagger-ui');
    if (!ui) return;

    const info = ui.querySelector('.info');
    if (info) {
      const baseUrl = info.querySelector('.base-url');
      const oldDesc = info.querySelector('div.description, p, .markdown');
      const title   = info.querySelector('.title');

      // Remove version/OAS badges
      info.querySelectorAll('.title .version-stamp, .title .flavor, .title small')
          .forEach(el => el.remove());

      if (!info.querySelector('.pp-intro')) {
        const intro = document.createElement('div');
        intro.className = 'pp-intro';
        intro.innerHTML = `
          <span class="pp-intro-icon" aria-hidden="true">ℹ</span>
          <span class="pp-intro-text">
            A dedicated Ktor plugin that renders a custom Swagger UI and lists all endpoints from
            <code>openapi.json</code>. Choose a server and authorize to try endpoints.
          </span>
          ${baseUrl ? `<a class="pp-spec-link" href="${baseUrl.textContent.trim()}" rel="nofollow">openapi.json</a>` : ''}
        `;
        if (title) title.insertAdjacentElement('afterend', intro);
      }
      if (oldDesc)  oldDesc.style.display = 'none';
      if (baseUrl)  baseUrl.style.display = 'none';
    }

    buildMinimalServersBar();
    document.body.classList.remove('pp-controls-sticky');
  }

  // ---------- Boot Swagger ----------
  window.ui = SwaggerUIBundle({
    url: SPEC_URL,
    dom_id: "#swagger-ui",
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: "StandaloneLayout",
    docExpansion: "list",
    defaultModelsExpandDepth: -1,
    defaultModelExpandDepth: 0,
    tagsSorter: "alpha",
    operationsSorter: "alpha",
    onComplete: function () {
      addHeader();
      addFooter();
      addFooterSpacer();
      watchFooter();
      enhanceApiHeaderNoSticky();

      const root = document.querySelector("#swagger-ui");
      if (root) {
        const mo = new MutationObserver(() => {
          addHeader();
          addFooter();
          addFooterSpacer();
          reserveAllScrollContainers();
          enhanceApiHeaderNoSticky();
        });
        mo.observe(root, { childList: true, subtree: true });
      }

      window.addEventListener('resize', syncControlsHeightToTag);
    }
  });
})();
