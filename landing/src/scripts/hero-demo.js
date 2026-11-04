// Demo animada de la maqueta del hero (HeroMock.astro). Reproduce en bucle una venta completa:
//   escanear productos -> ticket -> factura DIAN -> medio de pago -> cobrar -> ventas y stock en vivo
//   -> factura pendiente -> factura validada.
//
// - El HTML de la maqueta es el estado FINAL. Esto lo lleva al estado inicial y lo va encendiendo; los estilos
//   estan en global.css bajo `html.anim [data-mock]:not(.is-off)`.
// - `html.anim` lo pone el <head> solo si no hay prefers-reduced-motion: ese publico ve siempre la venta terminada.
// - Solo corre mientras la maqueta esta en pantalla y la pestana visible; el usuario puede pausarla
//   (boton [data-mock-toggle], WCAG 2.2.2) y entonces se muestra la venta terminada.

const ITEMS = {
  leche: { name: 'Leche 1 L', code: '7701234567890' },
  pan: { name: 'Pan tajado', code: '7701234567891' },
  gaseosa: { name: 'Gaseosa 1,5 L', code: '7701234567892' },
  arroz: { name: 'Arroz 500 g', code: '7701234567893' },
};

// Escaneos en orden: la leche se escanea dos veces (1x -> 2x). `total` es el acumulado del ticket.
const SCANS = [
  { item: 'leche', qty: 1, line: 0, total: 4200 },
  { item: 'leche', qty: 2, line: 0, total: 8400 },
  { item: 'pan', line: 1, total: 14200 },
  { item: 'gaseosa', line: 2, total: 20700 },
  { item: 'arroz', line: 3, total: 24100 },
];

const SPARK_BEFORE = '62%';

const fmt = (n) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
const money = (n) => `$ ${fmt(n)}`;

function init(root) {
  const q = (s) => root.querySelector(s);
  const qa = (s) => [...root.querySelectorAll(s)];

  const el = {
    grid: q('[data-m="grid"]'),
    search: q('[data-m="search"]'),
    searchText: q('[data-m="search-text"]'),
    empty: q('[data-m="empty"]'),
    lines: qa('[data-line]'),
    qty: q('[data-m="qty"]'),
    amt: q('[data-m="amt"]'),
    total: q('[data-m="total"]'),
    dian: q('[data-m="dian"]'),
    pays: qa('[data-pay]'),
    btn: q('[data-m="btn"]'),
    btnLabel: q('[data-m="btn-label"]'),
    kpi: q('[data-m="kpi"]'),
    spark: q('[data-m="spark"]'),
    lowCount: q('[data-m="low-count"]'),
    lowPan: q('[data-m="low-pan"]'),
    toast: q('[data-m="toast"]'),
    toastSub: q('[data-m="toast-sub"]'),
    cursor: q('[data-m="cursor"]'),
  };
  const nums = qa('[data-before]');
  const toggleBtn = document.querySelector('[data-mock-toggle]');

  // Si el marcado cambia y falta una pieza, la demo se desactiva en vez de romper la pagina.
  const required = ['grid', 'search', 'searchText', 'empty', 'qty', 'amt', 'total', 'dian', 'btn', 'btnLabel', 'kpi', 'spark', 'lowCount', 'lowPan', 'toast', 'toastSub', 'cursor'];
  if (required.some((k) => !el[k]) || el.lines.length < 4 || el.pays.length < 3) {
    root.classList.add('is-off');
    return;
  }

  // Estado final (el del HTML): para volver a el al pausar.
  const texts = [el.searchText, el.total, el.qty, el.amt, el.kpi, el.lowCount, el.btnLabel, el.toastSub, ...nums];
  texts.forEach((n) => { n.dataset.final = n.textContent; });
  el.spark.dataset.final = el.spark.style.height;

  const saleTotal = SCANS[SCANS.length - 1].total;
  const kpiAfter = Number(el.kpi.dataset.final.replace(/\D/g, ''));
  const kpiBefore = kpiAfter - saleTotal;

  let timers = [];
  let raf = 0;
  let running = false;
  let visible = false;
  let userPaused = false;

  const at = (ms, fn) => timers.push(setTimeout(fn, ms));
  const replay = (node, cls) => { node.classList.remove(cls); void node.offsetWidth; node.classList.add(cls); };

  function setTotal(n) {
    el.total.textContent = money(n);
    if (n > 0) {
      el.btnLabel.textContent = `Cobrar ${money(n)}`;
      el.btn.classList.remove('is-disabled');
    }
  }

  function countUp(node, from, to, ms) {
    cancelAnimationFrame(raf);
    const t0 = performance.now();
    const tick = (now) => {
      const p = Math.min(1, (now - t0) / ms);
      node.textContent = money(Math.round(from + (to - from) * (1 - Math.pow(1 - p, 3))));
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
  }

  // ---- Cursor ----
  function place(target, { dx = 0, dy = 0, instant = false } = {}) {
    const base = root.getBoundingClientRect();
    const r = target.getBoundingClientRect();
    const x = r.left - base.left + r.width / 2 + dx;
    const y = r.top - base.top + r.height / 2 + dy;
    if (instant) el.cursor.style.transition = 'none';
    el.cursor.style.transform = `translate(${x}px, ${y}px)`;
    if (instant) { void el.cursor.offsetWidth; el.cursor.style.transition = ''; }
  }
  function cursorTo(target, first) {
    if (first) {
      place(target, { dx: 90, dy: 80, instant: true });
      el.cursor.classList.add('is-visible');
      at(40, () => place(target));
    } else {
      place(target);
    }
  }
  const click = () => replay(el.cursor, 'is-click');

  // ---- Estados ----
  function reset() {
    root.classList.add('is-instant');
    el.empty.classList.remove('is-gone');
    el.lines.forEach((l) => l.classList.remove('is-in'));
    el.qty.textContent = '1×';
    el.amt.textContent = money(4200);
    el.total.textContent = money(0);
    el.dian.classList.remove('is-on');
    el.pays.forEach((p) => p.classList.remove('is-sel'));
    el.btn.classList.remove('is-done', 'is-busy', 'is-pressed');
    el.btn.classList.add('is-disabled');
    el.btnLabel.textContent = 'Cobrar';
    el.kpi.textContent = money(kpiBefore);
    el.spark.style.height = SPARK_BEFORE;
    el.lowCount.textContent = '2';
    el.lowPan.classList.remove('is-in');
    nums.forEach((n) => { n.textContent = n.dataset.before; n.classList.remove('m-flash'); });
    el.toast.classList.remove('is-in', 'is-queued', 'is-pop');
    el.toastSub.textContent = 'Estado: pendiente';
    el.searchText.textContent = el.searchText.dataset.final;
    el.search.classList.remove('is-scanning');
    qa('.m-card.is-scan').forEach((c) => c.classList.remove('is-scan'));
    el.cursor.classList.remove('is-visible', 'is-click');
    void root.offsetWidth;
    // Sin transiciones en este fotograma; al siguiente, el fundido de entrada del bloque.
    requestAnimationFrame(() => {
      root.classList.remove('is-instant');
      requestAnimationFrame(() => el.grid.classList.remove('is-fading'));
    });
  }

  function restoreFinal() {
    cancelAnimationFrame(raf);
    texts.forEach((n) => { n.textContent = n.dataset.final; });
    el.spark.style.height = el.spark.dataset.final;
    el.pays.forEach((p, i) => p.classList.toggle('is-sel', i === 0));
    el.toast.classList.remove('is-queued', 'is-pop');
    el.grid.classList.remove('is-fading');
    qa('.m-card.is-scan').forEach((c) => c.classList.remove('is-scan'));
    el.search.classList.remove('is-scanning');
    el.cursor.classList.remove('is-visible');
  }

  // ---- Guion ----
  function scan(s) {
    const it = ITEMS[s.item];
    const card = q(`[data-card="${s.item}"]`);
    el.search.classList.add('is-scanning');
    el.searchText.textContent = `${it.code} · ${it.name}`;
    if (card) card.classList.add('is-scan');
    el.empty.classList.add('is-gone');
    el.lines[s.line].classList.add('is-in');
    if (s.item === 'leche') {
      el.qty.textContent = `${s.qty}×`;
      el.amt.textContent = money(4200 * s.qty);
      replay(el.qty, 'is-bump');
      replay(el.amt, 'is-bump');
    }
    setTotal(s.total);
    replay(el.total, 'is-bump');
    at(650, () => {
      el.search.classList.remove('is-scanning');
      el.searchText.textContent = el.searchText.dataset.final;
      if (card) card.classList.remove('is-scan');
    });
  }

  function completeSale() {
    el.btn.classList.remove('is-busy');
    el.btn.classList.add('is-done');
    el.btnLabel.textContent = 'Venta registrada';
    el.cursor.classList.remove('is-visible');
    countUp(el.kpi, kpiBefore, kpiAfter, 1000);
    el.spark.style.height = el.spark.dataset.final;
    nums.forEach((n) => { n.textContent = n.dataset.final; replay(n, 'm-flash'); });
    el.lowCount.textContent = el.lowCount.dataset.final;
    replay(el.lowCount, 'is-bump');
    el.lowPan.classList.add('is-in');
  }

  function schedule() {
    // Cada linea: [ms desde el inicio del ciclo, accion].
    SCANS.forEach((s, i) => at(700 + i * 900, () => scan(s)));

    at(5200, () => cursorTo(el.dian, true));
    at(6150, () => { click(); el.dian.classList.add('is-on'); });

    at(6900, () => cursorTo(el.pays[0]));
    at(7850, () => { click(); el.pays[0].classList.add('is-sel'); });

    at(8500, () => cursorTo(el.btn));
    at(9450, () => { click(); el.btn.classList.add('is-pressed'); });
    at(9650, () => {
      el.btn.classList.remove('is-pressed');
      el.btn.classList.add('is-busy');
      el.btnLabel.textContent = 'Cobrando…';
    });
    at(10600, completeSale);

    at(11700, () => {
      el.toast.classList.add('is-in', 'is-queued');
    });
    at(13200, () => {
      el.toast.classList.remove('is-queued');
      el.toastSub.textContent = 'Estado: validada';
      replay(el.toast, 'is-pop');
      at(700, () => el.toast.classList.remove('is-pop'));
    });

    // Pausa para leer el resultado, fundido y vuelta a empezar.
    at(16200, () => { el.grid.classList.add('is-fading'); el.toast.classList.remove('is-in'); });
    at(16700, cycle);
  }

  function cycle() {
    timers.forEach(clearTimeout);
    timers = [];
    reset();
    schedule();
  }

  function start() {
    if (running) return;
    running = true;
    root.classList.remove('is-off');
    cycle();
  }
  function stop() {
    running = false;
    timers.forEach(clearTimeout);
    timers = [];
    cancelAnimationFrame(raf);
  }

  function sync() {
    const should = visible && !document.hidden && !userPaused;
    if (should && !running) start();
    else if (!should && running) stop();
  }

  // ---- Control del usuario ----
  function setPaused(paused) {
    userPaused = paused;
    if (paused) {
      stop();
      restoreFinal();
      root.classList.add('is-off');
    } else {
      sync();
    }
    if (toggleBtn) {
      toggleBtn.setAttribute('aria-pressed', String(paused));
      toggleBtn.textContent = paused ? 'Reanudar animación' : 'Pausar animación';
    }
  }
  toggleBtn?.addEventListener('click', () => setPaused(!userPaused));

  // Estado inicial: ticket vacio, listo para empezar cuando la maqueta aparezca en pantalla.
  reset();

  if ('IntersectionObserver' in window) {
    new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; sync(); }, { threshold: 0.2 }).observe(root);
  } else {
    visible = true;
  }
  document.addEventListener('visibilitychange', sync);

  // Si el sistema pasa a "reducir movimiento" con la pagina abierta, se queda la venta terminada.
  const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
  mq.addEventListener?.('change', (e) => { if (e.matches) setPaused(true); });
  sync();
}

const root = document.querySelector('[data-mock]');
if (root && document.documentElement.classList.contains('anim')) init(root);
