"use strict";
const $ = (id) => document.getElementById(id);
const all = (sel) => [...document.querySelectorAll(sel)];
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const money = (value) => "S/ " + Number(value || 0).toFixed(2);
const date = (value) => (value ? new Date(value).toLocaleString("es-PE") : "");
const allowed = {
  Administrador: [
    "mesero",
    "cocina",
    "cajero",
    "inventario",
    "reportes",
    "admin",
  ],
  Mozo: ["mesero"],
  Cocinero: ["cocina"],
  "Jefe de Cocina": ["cocina"],
  Cajero: ["cajero"],
};
const state = {
  user: null,
  csrf: null,
  tab: "mesero",
  products: [],
  tables: [],
  orders: [],
  insumos: [],
  gestion: {},
  reports: {},
  selection: {},
  notes: {},
  target: null,
  category: "",
  account: null,
  method: "Efectivo",
  inv: "insumos",
  admin: "usuarios",
  busy: false,
  refreshing: false,
  request: null,
  payment: null,
  compra: null,
};
const uuid = () => crypto.randomUUID();
function toast(text) {
  const e = document.createElement("div");
  e.className = "toast";
  e.textContent = text;
  $("toast-stack").append(e);
  setTimeout(() => e.remove(), 6500);
}
function status(text, error = false) {
  $("connection-state").textContent = text;
  $("connection-state").classList.toggle("status-error", error);
}
async function csrf() {
  const r = await fetch("/api/csrf");
  if (!r.ok)
    throw Error("No se pudo conectar con Java. Abra http://localhost:8080.");
  state.csrf = await r.json();
}
function loginScreen() {
  state.user = null;
  $("app").style.display = "none";
  $("login-screen").style.display = "flex";
  closeModal();
}
async function api(path, method = "GET", body) {
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") headers[state.csrf.headerName] = state.csrf.token;
  const r = await fetch("/api" + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (r.status === 401) {
    loginScreen();
    await csrf();
    throw Error("Inicie sesión para continuar.");
  }
  const raw = await r.text();
  let data = {};
  try {
    data = raw ? JSON.parse(raw) : null;
  } catch {}
  if (!r.ok) throw Error(data?.message || "No se pudo completar la operación.");
  return data;
}
function table(headers, rows) {
  return (
    '<div class="table-wrap"><table class="data-table"><thead><tr>' +
    headers.map((x) => "<th>" + esc(x) + "</th>").join("") +
    "</tr></thead><tbody>" +
    (rows.length
      ? rows
          .map(
            (r) =>
              "<tr>" + r.map((c) => "<td>" + c + "</td>").join("") + "</tr>",
          )
          .join("")
      : '<tr><td colspan="' + headers.length + '">Sin registros.</td></tr>') +
    "</tbody></table></div>"
  );
}
function btn(label, action, id = "", extra = "") {
  return `<button class="btn btn-secondary btn-sm" data-action="${action}" data-id="${id}" ${extra}>${label}</button>`;
}
function field(label, id, type = "text", value = "", extra = "") {
  return `<div class="field"><label for="${id}">${label}</label><input id="${id}" type="${type}" value="${esc(value)}" ${extra}></div>`;
}
function select(label, id, rows, key, labelKey, value = "") {
  return `<div class="field"><label for="${id}">${label}</label><select id="${id}">${rows.map((r) => `<option value="${r[key]}" ${String(value) === String(r[key]) ? "selected" : ""}>${esc(r[labelKey])}</option>`).join("")}</select></div>`;
}
function modal(title, html, save) {
  $("modal-title").textContent = title;
  $("modal-body").innerHTML = html;
  $("modal-footer").innerHTML = "";
  const cancel = document.createElement("button");
  cancel.className = "btn btn-secondary";
  cancel.textContent = "Cerrar";
  cancel.onclick = closeModal;
  $("modal-footer").append(cancel);
  if (save) {
    const b = document.createElement("button");
    b.className = "btn btn-success";
    b.textContent = "Guardar";
    b.onclick = async () => {
      b.disabled = true;
      try {
        await save();
        closeModal();
        await refresh();
      } catch (e) {
        toast(e.message);
      } finally {
        b.disabled = false;
      }
    };
    $("modal-footer").append(b);
  }
  $("app-modal").style.display = "flex";
}
function closeModal() {
  $("app-modal").style.display = "none";
}
async function doLogin() {
  await csrf();
  const r = await fetch("/api/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      [state.csrf.headerName]: state.csrf.token,
    },
    body: new URLSearchParams({
      email: $("login-email").value.trim(),
      password: $("login-password").value,
    }),
  });
  if (!r.ok) {
    await csrf();
    throw Error("Correo o contraseña incorrectos.");
  }
  $("login-password").value = "";
  await csrf();
  await start();
}
async function start() {
  state.user = await api("/me");
  state.tab = (allowed[state.user.rol] || [])[0];
  if (!state.tab) throw Error("Esta cuenta no tiene un módulo asignado.");
  $("login-screen").style.display = "none";
  $("login-error").style.display = "none";
  $("app").style.display = "flex";
  $("su-name").textContent = state.user.nombre;
  $("su-role").textContent = state.user.rol;
  $("su-avatar").textContent = state.user.nombre.slice(0, 2).toUpperCase();
  $("su-meta").textContent = state.user.email;
  $("top-user-name").textContent = state.user.nombre;
  all(".nav-item").forEach(
    (e) =>
      (e.hidden = !(allowed[state.user.rol] || []).includes(e.dataset.tab)),
  );
  [state.products] = await Promise.all([api("/productos")]);
  state.category = state.products[0]?.categoria || "";
  await switchTab(state.tab);
}
async function switchTab(tab) {
  if (!(allowed[state.user.rol] || []).includes(tab))
    throw Error("Operación no permitida.");
  state.tab = tab;
  all(".tab-panel").forEach((e) =>
    e.classList.toggle("active", e.id === "panel-" + tab),
  );
  all(".nav-item").forEach((e) =>
    e.classList.toggle("active", e.dataset.tab === tab),
  );
  $("nav-title").textContent = {
    mesero: "Mesas y pedidos",
    cocina: "Cocina",
    cajero: "Caja y tickets internos",
    inventario: "Inventario, compras y kardex",
    reportes: "Reportes de ventas",
    admin: "Administración",
  }[tab];
  $("sidebar").classList.remove("open");
  await refresh();
}
async function refresh() {
  if (!state.user || state.refreshing) return;
  state.refreshing = true;
  try {
    [state.tables, state.orders] = await Promise.all([
      api("/mesas"),
      api("/pedidos"),
    ]);
    if (
      state.user.rol === "Administrador" &&
      ["inventario", "admin", "reportes"].includes(state.tab)
    ) {
      [state.insumos, state.gestion, state.products] = await Promise.all([
        api("/insumos"),
        api("/gestion"),
        api("/productos"),
      ]);
      if (state.tab === "reportes")
        state.reports = await api("/gestion/reportes");
    }
    renderTables();
    renderKDS();
    renderCashier();
    if (state.tab === "inventario") renderInventory();
    if (state.tab === "admin") renderAdmin();
    if (state.tab === "reportes") renderReports();
    status("Conectado · " + new Date().toLocaleTimeString("es-PE"));
  } catch (e) {
    status("Sin conexión", true);
    throw e;
  } finally {
    state.refreshing = false;
  }
}
const orderLabel = (o) =>
  o.tipo_pedido === "SALON"
    ? "Mesa " + o.mesa_numero
    : "Para llevar · " + o.cliente_nombre;
const orderFor = (id) => state.orders.find((o) => o.id_mesa === Number(id));
function orderCard(o, action = "select-order") {
  return `<div class="table-box" data-action="${action}" data-id="${o.id_pedido}"><strong>${esc(orderLabel(o))}</strong><p>#${o.id_pedido} · ${esc(o.estado)}</p><b>${money(o.total_pedido)}</b></div>`;
}
function renderTables() {
  $("nine-tables-grid").innerHTML = state.tables
    .map((t) => {
      const o = orderFor(t.id_mesa);
      return `<div class="table-box ${o ? "occupied" : ""}" data-action="select-table" data-id="${t.id_mesa}"><div class="table-header-card"><strong>Mesa ${t.numero}</strong><span class="badge ${o ? "badge-warn" : "badge-ok"}">${esc(o?.estado || t.estado)}</span></div><p>${t.capacidad} personas</p><strong>${money(o?.total_pedido)}</strong></div>`;
    })
    .join("");
  const takes = state.orders.filter((o) => o.tipo_pedido === "LLEVAR");
  $("tw-count-badge").textContent = takes.length;
  $("mesero-takeaway-list").innerHTML =
    takes.map((o) => orderCard(o, "view-order")).join("") ||
    "<p>No hay pedidos para llevar.</p>";
}
function selectTable(id) {
  const t = state.tables.find((t) => t.id_mesa === Number(id));
  if (["RESERVADA", "MANTENIMIENTO"].includes(t.estado))
    throw Error("La mesa no está disponible.");
  builder({ tipo: "SALON", id: Number(id), label: "Mesa " + t.numero });
}
function builder(target) {
  state.target = target;
  state.selection = {};
  state.notes = {};
  state.request = null;
  $("input-client-name").value = "";
  $("view-tables-selector").style.display = "none";
  $("view-order-builder").style.display = "block";
  $("client-name-container").style.display =
    target.tipo === "LLEVAR" ? "block" : "none";
  $("order-target-title").textContent = target.label;
  renderMenu();
  renderCart();
}
function renderMenu() {
  const categories = [...new Set(state.products.map((p) => p.categoria))];
  if (!categories.includes(state.category)) state.category = categories[0];
  $("menu-categories").innerHTML = categories
    .map(
      (c) =>
        `<button class="subtab ${c === state.category ? "active" : ""}" data-action="menu-cat" data-cat="${esc(c)}">${esc(c)}</button>`,
    )
    .join("");
  $("full-menu-list").innerHTML = state.products
    .filter((p) => p.categoria === state.category)
    .map(
      (p) =>
        `<div class="menu-item-row"><div class="item-info" style="flex:1"><h4>${esc(p.nombre)}</h4><span>${money(p.precio)}</span><div class="item-note"><input data-nota="${p.id}" placeholder="Nota para cocina" maxlength="300" value="${esc(state.notes[p.id] || "")}"></div></div><div class="qty-box"><button class="btn-qty" data-action="qty" data-id="${p.id}" data-delta="-1">−</button><span class="qty-num">${state.selection[p.id] || 0}</span><button class="btn-qty" data-action="qty" data-id="${p.id}" data-delta="1">+</button></div></div>`,
    )
    .join("");
}
function items() {
  return Object.entries(state.selection)
    .filter(([, q]) => q > 0)
    .map(([id, cantidad]) => ({
      productoId: Number(id),
      cantidad,
      observacion: state.notes[id] || null,
    }));
}
function renderCart() {
  const rows = items();
  $("summary-items-list").innerHTML =
    rows
      .map((i) => {
        const p = state.products.find((p) => p.id === i.productoId);
        return `<div class="summary-row"><span>${i.cantidad} × ${esc(p.nombre)}</span><strong>${money(i.cantidad * p.precio)}</strong></div>`;
      })
      .join("") || "<p>Seleccione productos.</p>";
  $("summary-total").textContent = money(
    rows.reduce(
      (a, i) =>
        a +
        i.cantidad * state.products.find((p) => p.id === i.productoId).precio,
      0,
    ),
  );
  $("btn-ver-comandas").textContent = "Ver pedido actual";
}
async function sendOrder() {
  if (!items().length) throw Error("Agregue productos al pedido.");
  if (!state.request)
    state.request = {
      solicitudId: uuid(),
      tipo: state.target.tipo,
      mesaId: state.target.tipo === "SALON" ? state.target.id : null,
      cliente: $("input-client-name").value.trim() || null,
      observacion: null,
      items: items(),
    };
  const o = await api("/pedidos", "POST", state.request);
  state.request = null;
  state.selection = {};
  state.notes = {};
  $("view-tables-selector").style.display = "block";
  $("view-order-builder").style.display = "none";
  toast("Pedido #" + o.id_pedido + " registrado.");
  await refresh();
}
async function viewOrder(id) {
  const o = await api("/pedidos/" + id);
  modal(
    "Pedido #" + id + " · " + orderLabel(o),
    orderDetails(o) +
      (["Administrador", "Mozo"].includes(state.user.rol) &&
      o.estado === "REGISTRADO"
        ? btn("Anular pedido", "cancel-order", id)
        : ""),
  );
}
function orderDetails(o) {
  return (
    table(
      ["Producto", "Cantidad", "Estado", "Subtotal"],
      (o.items || []).map((i) => [
        esc(i.nombre) + (i.observacion ? "<br>" + esc(i.observacion) : ""),
        i.cantidad,
        esc(i.estado_cocina),
        money(i.subtotal),
      ]),
    ) + `<p><strong>Total ${money(o.total_pedido)}</strong></p>`
  );
}
async function changeState(id, estado) {
  await api("/pedidos/" + id + "/estado", "PATCH", { estado });
  closeModal();
  await refresh();
}
function renderKDS() {
  for (const [statusId, container, count] of [
    ["REGISTRADO", "kds-col-pendientes", "count-pend"],
    ["EN_PREPARACION", "kds-col-prep", "count-prep"],
    ["LISTO", "kds-col-ready", "count-ready"],
  ]) {
    const rows = state.orders.filter(
      (o) =>
        o.estado === statusId ||
        (statusId === "LISTO" && o.estado === "SERVIDO"),
    );
    $(count).textContent = rows.length;
    $(container).innerHTML =
      rows
        .map((o) => {
          const minutes = Math.max(
            0,
            Math.floor((Date.now() - new Date(o.fecha_hora)) / 60000),
          );
          const next =
            statusId === "REGISTRADO"
              ? "EN_PREPARACION"
              : statusId === "EN_PREPARACION"
                ? "LISTO"
                : null;
          return `<div class="kds-ticket-item ${minutes > 20 ? "t-danger" : minutes >= 10 ? "t-warn" : "t-ok"}"><div class="kds-ticket-head"><b>${esc(orderLabel(o))}</b><span>${minutes} min</span></div><p>Pedido #${o.id_pedido}</p><ul class="kds-items">${(o.items || []).map((i) => `<li>${i.cantidad} × ${esc(i.nombre)} <small>(${esc(i.estado_cocina)})</small>${i.observacion ? "<br>" + esc(i.observacion) : ""}</li>`).join("")}</ul><div class="kds-actions">${btn("Imprimir", "print-order", o.id_pedido)}${next ? btn(next === "LISTO" ? "Marcar listo" : "Preparar", "kds-status", o.id_pedido, `data-status="${next}"`) : ""}</div></div>`;
        })
        .join("") || '<div class="empty-state">Sin pedidos.</div>';
  }
  $("badge-kds").textContent = state.orders.length;
  $("badge-kds").classList.toggle("hidden", !state.orders.length);
}
function renderCashier() {
  $("cashier-open-count").textContent =
    state.orders.length + " cuentas abiertas";
  $("cashier-tables-grid").innerHTML = state.tables
    .map((t) => {
      const o = orderFor(t.id_mesa);
      return `<div class="table-box" ${o ? `data-action="select-order" data-id="${o.id_pedido}"` : ""}><strong>Mesa ${t.numero}</strong><p>${esc(o?.estado || t.estado)}</p>${money(o?.total_pedido)}</div>`;
    })
    .join("");
  $("cashier-takeaway-list").innerHTML =
    state.orders
      .filter((o) => o.tipo_pedido === "LLEVAR")
      .map((o) => orderCard(o))
      .join("") || "<p>Sin pedidos para llevar.</p>";
  const o = state.orders.find((o) => o.id_pedido === state.account);
  $("cashier-title-panel").textContent = o
    ? orderLabel(o)
    : "Detalle de cuenta";
  $("cashier-items-summary").innerHTML = o
    ? orderDetails(o)
    : "Seleccione una cuenta.";
  $("cashier-total-amount").textContent = money(o?.total_pedido);
  $("cashier-comanda-count").textContent = o ? "Pedido #" + o.id_pedido : "";
  $("btn-process-pay").disabled =
    !o || !["LISTO", "SERVIDO"].includes(o.estado);
  $("btn-split").disabled = !o;
  if (!o) {
    state.account = null;
    $("cashier-split-area").innerHTML = "";
  }
  $("payment-options").innerHTML = ["Efectivo", "Tarjeta", "Yape", "Plin"]
    .map(
      (m) =>
        `<button class="pay-method-btn ${state.method === m ? "active" : ""}" data-action="select-paymethod" data-method="${m}">${m}</button>`,
    )
    .join("");
  if (!$("pay-reference"))
    $("cashier-cobro-extra").innerHTML =
      field(
        "Referencia de pago opcional",
        "pay-reference",
        "text",
        "",
        'maxlength="50"',
      ) +
      field(
        "Efectivo recibido opcional",
        "pay-received",
        "number",
        "",
        'min="0" step="0.01"',
      );
}
function splitAccount() {
  const o = state.orders.find((o) => o.id_pedido === state.account);
  modal(
    "Calcular reparto",
    field(
      "Número de comensales",
      "split-count",
      "number",
      2,
      'min="2" max="50"',
    ) +
      "<p>El reparto es orientativo. El pago se registra completo en un solo cobro.</p>" +
      btn("Calcular", "calculate-split") +
      '<div id="split-result"></div>',
  );
}
async function payment() {
  const o = state.orders.find((o) => o.id_pedido === state.account);
  if (!o) throw Error("Seleccione una cuenta.");
  const received = Number($("pay-received").value);
  if (
    state.method === "Efectivo" &&
    $("pay-received").value &&
    received < Number(o.total_pedido)
  )
    throw Error("El efectivo recibido no cubre el total.");
  if (!state.payment)
    state.payment = {
      solicitudId: uuid(),
      metodo: state.method,
      ruc: $("input-ruc").value.trim(),
      referencia: $("pay-reference").value.trim() || null,
    };
  const ticket = await api(
    "/pedidos/" + o.id_pedido + "/pago",
    "POST",
    state.payment,
  );
  state.payment = null;
  state.account = null;
  const content = `<h2>El Sabor Iqueño</h2><p>Ticket ${esc(ticket.numero_serie)}-${esc(ticket.numero_correlativo)}</p>${orderDetails(o)}<p>Método: ${esc(state.method)}</p>${state.method === "Efectivo" && received ? "<p>Vuelto: " + money(received - Number(o.total_pedido)) + "</p>" : ""}<p>Ticket interno sin validez tributaria.</p>`;
  $("input-ruc").value = "";
  $("pay-reference").value = "";
  $("pay-received").value = "";
  await refresh();
  modal("Pago registrado", content + btn("Imprimir ticket", "print-ticket"));
  state.ticketHTML = content;
  toast("Pago registrado.");
}
function printHTML(content) {
  $("print-area").innerHTML = content;
  window.print();
}
function renderInventory() {
  all("#inv-tabs .subtab").forEach((e) =>
    e.classList.toggle("active", e.dataset.sub === state.inv),
  );
  const g = state.gestion;
  if (state.inv === "insumos") {
    $("inv-body").innerHTML =
      '<div class="card"><div class="card-title"><span>Stock de insumos</span><a class="btn btn-secondary btn-sm" href="/api/reportes/inventario.xlsx">Descargar Excel</a></div>' +
      field("Buscar insumo", "stock-search") +
      '<div id="stock-table"></div></div>';
    renderStock();
  } else if (state.inv === "compras") {
    $("inv-body").innerHTML =
      '<div class="card"><div class="card-title"><span>Compras recibidas</span><div>' +
      btn("Nuevo proveedor", "new-provider") +
      " " +
      btn("Registrar compra", "new-purchase") +
      "</div></div>" +
      table(
        ["Compra", "Fecha", "Proveedor", "Documento", "Total"],
        (g.compras || []).map((c) => [
          "#" + c.id_compra,
          esc(c.fecha_compra),
          esc(c.proveedor),
          esc(c.documento),
          money(c.total),
        ]),
      ) +
      "<p>Últimas 100 compras.</p></div>";
  } else {
    $("inv-body").innerHTML =
      '<div class="card"><div class="card-title">Kardex de movimientos</div>' +
      select(
        "Insumo",
        "kardex-insumo",
        [{ id_insumo: "", nombre: "Todos" }, ...state.insumos],
        "id_insumo",
        "nombre",
      ) +
      '<div id="kardex-table"></div><p>Últimos 500 movimientos. El ajuste registra el stock físico final y conserva el saldo anterior.</p></div>';
    renderKardex();
  }
  const low = state.insumos.filter((i) => i.alerta).length;
  $("badge-stock").textContent = low;
  $("badge-stock").classList.toggle("hidden", !low);
}
function renderStock() {
  const search = $("stock-search").value.toLowerCase();
  $("stock-table").innerHTML = table(
    ["Insumo", "Unidad", "Stock", "Mínimo", "Costo", "Estado", "Acciones"],
    state.insumos
      .filter((i) => i.nombre.toLowerCase().includes(search))
      .map((i) => [
        esc(i.nombre),
        esc(i.unidad_medida),
        i.stock_actual,
        i.stock_minimo,
        money(i.precio_unitario),
        i.alerta ? "Stock bajo" : "Normal",
        ["ENTRADA", "MERMA", "AJUSTE"]
          .map((t) => btn(t, "stock-move", i.id_insumo, `data-mov="${t}"`))
          .join(" "),
      ]),
  );
}
function renderKardex() {
  const id = $("kardex-insumo").value;
  $("kardex-table").innerHTML = table(
    ["Fecha", "Insumo", "Tipo", "Cantidad", "Antes", "Saldo", "Motivo"],
    (state.gestion.movimientos || [])
      .filter((m) => !id || m.id_insumo === Number(id))
      .map((m) => [
        date(m.fecha_movimiento),
        esc(m.insumo),
        esc(m.tipo_movimiento),
        m.cantidad,
        m.stock_anterior,
        m.stock_resultante,
        esc(m.motivo),
      ]),
  );
}
function stockMove(id, tipo) {
  const i = state.insumos.find((i) => i.id_insumo === Number(id));
  modal(
    tipo + " · " + i.nombre,
    field(
      tipo === "AJUSTE" ? "Stock físico final" : "Cantidad",
      "mov-qty",
      "number",
      tipo === "AJUSTE" ? i.stock_actual : "",
      'min="0" step="0.001"',
    ) + field("Motivo", "mov-reason", "text", "", 'maxlength="200"'),
    () =>
      api("/gestion/insumos/" + id + "/movimientos", "POST", {
        tipo,
        cantidad: Number($("mov-qty").value),
        motivo: $("mov-reason").value.trim(),
      }),
  );
}
function purchase() {
  if (!state.gestion.proveedores?.length)
    throw Error("Registre un proveedor antes de la compra.");
  if (!state.insumos.length) throw Error("No hay insumos disponibles.");
  state.compra = null;
  modal(
    "Registrar compra recibida",
    select(
      "Proveedor",
      "purchase-provider",
      state.gestion.proveedores,
      "id_proveedor",
      "nombre",
    ) +
      field(
        "Fecha",
        "purchase-date",
        "date",
        new Date().toLocaleDateString("en-CA"),
      ) +
      field(
        "Documento de referencia",
        "purchase-doc",
        "text",
        "",
        'maxlength="80"',
      ) +
      '<p>Insumo · cantidad · costo unitario</p><div id="purchase-rows"></div>' +
      btn("Agregar insumo", "purchase-add"),
    async () => {
      if (!state.compra)
        state.compra = {
          solicitudId: uuid(),
          proveedorId: Number($("purchase-provider").value),
          fecha: $("purchase-date").value,
          documento: $("purchase-doc").value.trim(),
          items: all("#purchase-rows .purchase-row").map((e) => ({
            insumoId: Number(e.querySelector("select").value),
            cantidad: Number(e.querySelector("[data-qty]").value),
            costo: Number(e.querySelector("[data-cost]").value),
          })),
        };
      const r = await api("/gestion/compras", "POST", state.compra);
      toast("Compra #" + r.id_compra + " recibida.");
      state.compra = null;
    },
  );
  purchaseRow();
}
function purchaseRow() {
  const e = document.createElement("div");
  e.className = "purchase-row";
  e.style.cssText =
    "display:grid;grid-template-columns:2fr 1fr 1fr auto;gap:8px;margin-bottom:10px";
  e.innerHTML =
    "<select>" +
    state.insumos
      .map(
        (i) =>
          `<option value="${i.id_insumo}">${esc(i.nombre)} (${esc(i.unidad_medida)})</option>`,
      )
      .join("") +
    '</select><input aria-label="Cantidad" data-qty type="number" min="0.001" step="0.001" value="1"><input aria-label="Costo unitario" data-cost type="number" min="0" step="0.01" value="0">' +
    btn("Quitar", "remove-row");
  $("purchase-rows").append(e);
}
function renderReports() {
  const r = state.reports,
    s = r.resumen || {};
  $("rep-body").innerHTML =
    '<div class="kpi-grid">' +
    [
      ["Ventas de hoy", money(s.ventas_hoy)],
      ["Ventas acumuladas", money(s.ventas_total)],
      ["Cuentas pagadas", s.cuentas_pagadas || 0],
    ]
      .map(
        ([label, value]) =>
          `<div class="kpi-card"><span class="kpi-label">${label}</span><div class="kpi-value">${value}</div></div>`,
      )
      .join("") +
    '</div><div class="card"><h3>Ventas por fecha</h3>' +
    table(
      ["Fecha", "Cuentas", "Total"],
      (r.porDia || []).map((x) => [esc(x.fecha), x.cuentas, money(x.total)]),
    ) +
    '<p>Últimas 30 fechas con ventas.</p></div><div class="grid-2" style="margin-top:16px"><div class="card"><h3>Métodos de pago</h3>' +
    table(
      ["Método", "Pagos", "Total"],
      (r.porMetodo || []).map((x) => [esc(x.metodo), x.pagos, money(x.total)]),
    ) +
    '</div><div class="card"><h3>Productos más vendidos</h3>' +
    table(
      ["Producto", "Cantidad", "Total"],
      (r.productos || []).map((x) => [
        esc(x.nombre),
        x.cantidad,
        money(x.total),
      ]),
    ) +
    "</div></div>";
}
function renderAdmin() {
  all("#adm-tabs .subtab").forEach((e) =>
    e.classList.toggle("active", e.dataset.sub === state.admin),
  );
  const g = state.gestion;
  if (state.admin === "usuarios")
    $("adm-body").innerHTML =
      '<div class="card"><div class="card-title"><span>Usuarios y roles</span>' +
      btn("Crear usuario", "new-user") +
      "</div>" +
      table(
        ["Nombre", "Correo", "Rol", "Estado", "Acción"],
        (g.usuarios || []).map((u) => [
          esc(u.nombre + " " + u.apellido),
          esc(u.email),
          esc(u.rol),
          u.activo ? "Activo" : "Inactivo",
          u.id_usuario === state.user.id
            ? "Sesión actual"
            : btn(
                u.activo ? "Desactivar" : "Activar",
                "toggle-user",
                u.id_usuario,
              ),
        ]),
      ) +
      "</div>";
  if (state.admin === "personal")
    $("adm-body").innerHTML =
      '<div class="card"><div class="card-title"><span>Personal y turno de hoy</span>' +
      btn("Registrar personal", "new-person") +
      "</div>" +
      table(
        ["Nombre", "Cargo", "Turno de hoy", "Acción"],
        (g.personal || []).map((p) => [
          esc(p.nombre + " " + p.apellido),
          esc(p.cargo),
          esc(p.turno || "Sin asignar"),
          btn("Asignar turno", "assign-shift", p.id_personal),
        ]),
      ) +
      "<p>La asignación corresponde a la fecha actual. El historial se conserva por día.</p></div>";
  if (state.admin === "auditoria")
    $("adm-body").innerHTML =
      '<div class="card"><h3>Auditoría de cambios</h3>' +
      table(
        ["Fecha", "Usuario", "Tabla", "Acción", "Registro", "IP"],
        (g.auditoria || []).map((a) => [
          date(a.fecha_hora),
          esc(a.usuario),
          esc(a.tabla_afectada),
          esc(a.accion),
          a.registro_id,
          esc(a.ip_origen),
        ]),
      ) +
      "<p>Últimos 200 cambios de datos.</p></div>";
  if (state.admin === "menu")
    $("adm-body").innerHTML =
      '<div class="card"><h3>Menú y recetas</h3>' +
      table(
        ["Producto", "Categoría", "Precio", "Receta", "Acciones"],
        state.products.map((p) => [
          esc(p.nombre),
          esc(p.categoria),
          money(p.precio),
          (g.recetas || []).filter((r) => r.id_producto === p.id).length +
            " insumos",
          btn("Cambiar precio", "edit-price", p.id) +
            " " +
            btn("Editar receta", "edit-recipe", p.id),
        ]),
      ) +
      "</div>";
}
function newUser() {
  modal(
    "Crear usuario",
    field("Nombre", "user-name") +
      field("Apellido", "user-lastname") +
      field("Correo", "user-email", "email") +
      field(
        "Contraseña de 8 a 72 caracteres",
        "user-password",
        "password",
        "",
        'minlength="8" maxlength="72" autocomplete="new-password"',
      ) +
      select("Rol", "user-role", state.gestion.roles, "id_rol", "nombre"),
    () =>
      api("/gestion/usuarios", "POST", {
        nombre: $("user-name").value,
        apellido: $("user-lastname").value,
        email: $("user-email").value,
        password: $("user-password").value,
        rolId: Number($("user-role").value),
      }),
  );
}
function newPerson() {
  modal(
    "Registrar personal",
    field("Nombre", "person-name") +
      field("Apellido", "person-lastname") +
      field("Cargo", "person-job") +
      select(
        "Turno de hoy",
        "person-shift",
        state.gestion.turnos,
        "id_turno",
        "nombre",
      ),
    () =>
      api("/gestion/personal", "POST", {
        nombre: $("person-name").value,
        apellido: $("person-lastname").value,
        cargo: $("person-job").value,
        turnoId: Number($("person-shift").value) || null,
      }),
  );
}
function recipe(id) {
  const p = state.products.find((p) => p.id === Number(id));
  modal(
    "Receta de " + p.nombre,
    '<p>Las cantidades usan la unidad del insumo seleccionado.</p><div id="recipe-rows"></div>' +
      btn("Agregar insumo", "recipe-add"),
    () =>
      api("/gestion/productos/" + id + "/receta", "PUT", {
        items: all("#recipe-rows .recipe-row").map((e) => ({
          insumoId: Number(e.querySelector("select").value),
          cantidad: Number(e.querySelector("input").value),
        })),
      }),
  );
  const rows = state.gestion.recetas.filter(
    (r) => r.id_producto === Number(id),
  );
  rows.forEach((r) => recipeRow(r.id_insumo, r.cantidad));
  if (!rows.length) recipeRow();
}
function recipeRow(id = "", qty = 1) {
  const e = document.createElement("div");
  e.className = "recipe-row";
  e.style.cssText =
    "display:grid;grid-template-columns:2fr 1fr auto;gap:8px;margin-bottom:10px";
  e.innerHTML =
    "<select>" +
    state.insumos
      .map(
        (i) =>
          `<option value="${i.id_insumo}" ${Number(id) === i.id_insumo ? "selected" : ""}>${esc(i.nombre)} (${esc(i.unidad_medida)})</option>`,
      )
      .join("") +
    `</select><input aria-label="Cantidad" type="number" min="0.001" step="0.001" value="${qty}">` +
    btn("Quitar", "remove-row");
  $("recipe-rows").append(e);
}
function csv(name, rows) {
  const safe = (v) => {
    let s = String(v ?? "");
    if (/^[\s]*[=+@-]/.test(s)) s = "'" + s;
    return '"' + s.replace(/"/g, '""') + '"';
  };
  const blob = new Blob(
    ["\ufeff" + rows.map((r) => r.map(safe).join(";")).join("\r\n")],
    { type: "text/csv;charset=utf-8" },
  );
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name + ".csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
const actions = {
  "do-login": doLogin,
  "do-logout": async () => {
    await api("/logout", "POST");
    state.account = null;
    state.payment = null;
    state.request = null;
    state.target = null;
    state.selection = {};
    $("view-tables-selector").style.display = "block";
    $("view-order-builder").style.display = "none";
    loginScreen();
    await csrf();
  },
  "fill-login": (e) => {
    $("login-email").value = e.dataset.email;
    $("login-password").value = "Cambiar123!";
  },
  "switch-tab": (e) => switchTab(e.dataset.tab),
  "toggle-sidebar": () => $("sidebar").classList.toggle("open"),
  "select-table": (e) => selectTable(e.dataset.id),
  "open-takeaway": () =>
    builder({ tipo: "LLEVAR", id: null, label: "Nuevo pedido para llevar" }),
  "back-to-tables": () => {
    $("view-tables-selector").style.display = "block";
    $("view-order-builder").style.display = "none";
  },
  "menu-cat": (e) => {
    state.category = e.dataset.cat;
    renderMenu();
  },
  qty: (e) => {
    const id = e.dataset.id;
    state.selection[id] = Math.min(
      100,
      Math.max(0, (state.selection[id] || 0) + Number(e.dataset.delta)),
    );
    state.request = null;
    renderMenu();
    renderCart();
  },
  "clear-selection": () => {
    state.selection = {};
    state.notes = {};
    state.request = null;
    renderMenu();
    renderCart();
  },
  "send-order": sendOrder,
  "view-order": (e) => viewOrder(e.dataset.id),
  "open-comandas": () => {
    const o = state.target?.tipo === "SALON" ? orderFor(state.target.id) : null;
    if (!o) throw Error("Seleccione un pedido existente desde la lista.");
    return viewOrder(o.id_pedido);
  },
  "cancel-order": async (e) => {
    if (confirm("¿Anular el pedido y devolver los insumos disponibles?"))
      await changeState(e.dataset.id, "ANULADO");
  },
  "kds-status": (e) => changeState(e.dataset.id, e.dataset.status),
  refresh: refresh,
  "print-order": async (e) => {
    const o = await api("/pedidos/" + e.dataset.id);
    printHTML(
      "<h2>Comanda #" +
        o.id_pedido +
        "</h2><p>" +
        esc(orderLabel(o)) +
        "</p>" +
        orderDetails(o),
    );
  },
  "print-comandas": () =>
    printHTML(
      "<h2>Pedidos activos de cocina</h2>" +
        state.orders
          .map(
            (o) =>
              "<h3>#" +
              o.id_pedido +
              " " +
              esc(orderLabel(o)) +
              "</h3>" +
              orderDetails(o),
          )
          .join(""),
    ),
  "select-order": (e) => {
    state.account = Number(e.dataset.id);
    state.payment = null;
    $("cashier-split-area").innerHTML = "";
    renderCashier();
  },
  "select-paymethod": (e) => {
    state.method = e.dataset.method;
    state.payment = null;
    renderCashier();
  },
  "process-payment": payment,
  "print-ticket": () => printHTML(state.ticketHTML),
  "open-split": splitAccount,
  "calculate-split": () => {
    const n = Number($("split-count").value);
    if (!Number.isInteger(n) || n < 2 || n > 50)
      throw Error("Ingrese de 2 a 50 comensales.");
    const o = state.orders.find((o) => o.id_pedido === state.account);
    const cents = Math.round(Number(o.total_pedido) * 100),
      base = Math.floor(cents / n),
      extra = cents % n;
    $("split-result").innerHTML = table(
      ["Comensal", "Importe"],
      Array.from({ length: n }, (_, i) => [
        i + 1,
        money((base + (i < extra ? 1 : 0)) / 100),
      ]),
    );
  },
  "inv-tab": (e) => {
    state.inv = e.dataset.sub;
    renderInventory();
  },
  "stock-move": (e) => stockMove(e.dataset.id, e.dataset.mov),
  conciliar: () =>
    modal(
      "Conciliación de stock",
      state.gestion.conciliacion?.length
        ? table(
            ["Insumo", "Stock actual", "Último saldo", "Diferencia"],
            state.gestion.conciliacion.map((i) => [
              esc(i.nombre),
              i.stock_actual,
              i.ultimo_saldo,
              i.diferencia,
            ]),
          )
        : "<p>El stock coincide con el último movimiento de cada insumo que tiene kardex. El conteo físico debe verificarse manualmente.</p>",
    ),
  "new-provider": () =>
    modal(
      "Nuevo proveedor",
      field("Nombre", "provider-name") +
        field(
          "RUC de 11 dígitos",
          "provider-ruc",
          "text",
          "",
          'maxlength="11"',
        ),
      () =>
        api("/gestion/proveedores", "POST", {
          nombre: $("provider-name").value,
          ruc: $("provider-ruc").value,
        }),
    ),
  "new-purchase": purchase,
  "purchase-add": () => {
    state.compra = null;
    purchaseRow();
  },
  "remove-row": (e) => {
    e.parentElement.remove();
    state.compra = null;
  },
  "adm-tab": (e) => {
    state.admin = e.dataset.sub;
    renderAdmin();
  },
  "new-user": newUser,
  "toggle-user": async (e) => {
    const u = state.gestion.usuarios.find(
      (u) => u.id_usuario === Number(e.dataset.id),
    );
    if (confirm((u.activo ? "¿Desactivar" : "¿Activar") + " esta cuenta?")) {
      await api("/gestion/usuarios/" + u.id_usuario, "PATCH", {
        activo: !u.activo,
      });
      await refresh();
    }
  },
  "new-person": newPerson,
  "assign-shift": (e) =>
    modal(
      "Asignar turno de hoy",
      select(
        "Turno",
        "shift-select",
        state.gestion.turnos,
        "id_turno",
        "nombre",
      ),
      () =>
        api("/gestion/personal/" + e.dataset.id + "/turno", "PUT", {
          turnoId: Number($("shift-select").value),
        }),
    ),
  "edit-price": (e) => {
    const p = state.products.find((p) => p.id === Number(e.dataset.id));
    modal(
      "Precio de " + p.nombre,
      field(
        "Precio de venta",
        "product-price",
        "number",
        p.precio,
        'min="0.01" step="0.01"',
      ),
      () =>
        api("/gestion/productos/" + p.id + "/precio", "PATCH", {
          precio: Number($("product-price").value),
        }),
    );
  },
  "edit-recipe": (e) => recipe(e.dataset.id),
  "recipe-add": () => recipeRow(),
  "export-reporte": () =>
    csv("ventas", [
      ["Fecha", "Cuentas pagadas", "Total"],
      ...(state.reports.porDia || []).map((r) => [r.fecha, r.cuentas, r.total]),
    ]),
};
document.addEventListener("click", async (event) => {
  const e = event.target.closest("[data-action]");
  if (!e || !actions[e.dataset.action] || e.disabled || state.busy) return;
  state.busy = true;
  e.disabled = true;
  try {
    await actions[e.dataset.action](e);
  } catch (err) {
    toast(err.message);
    if (e.dataset.action === "do-login") {
      $("login-error").textContent = err.message;
      $("login-error").style.display = "block";
    }
  } finally {
    state.busy = false;
    if (e.isConnected) e.disabled = false;
    if (e.dataset.action === "process-payment") renderCashier();
  }
});
document.addEventListener("input", (e) => {
  if (e.target.dataset.nota) {
    state.notes[e.target.dataset.nota] = e.target.value;
    state.request = null;
  }
  if (e.target.id === "input-client-name") state.request = null;
  if (["input-ruc", "pay-reference"].includes(e.target.id))
    state.payment = null;
  if (e.target.closest("#purchase-rows") || e.target.id.startsWith("purchase-"))
    state.compra = null;
  if (e.target.id === "stock-search") renderStock();
});
document.addEventListener("change", (e) => {
  if (e.target.id === "kardex-insumo") renderKardex();
  if (e.target.closest("#purchase-rows") || e.target.id.startsWith("purchase-"))
    state.compra = null;
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closeModal();
  if (
    e.key === "Enter" &&
    ["login-email", "login-password"].includes(e.target.id)
  )
    document.querySelector('[data-action="do-login"]').click();
});
async function init() {
  $("demo-users").innerHTML = [
    ["Administrador", "admin"],
    ["Mozo", "mozo"],
    ["Cocina", "cocina"],
    ["Caja", "caja"],
  ]
    .map(
      ([role, email]) =>
        `<button class="demo-user-btn" data-action="fill-login" data-email="${email}@demo.local">${role}: ${email}@demo.local</button>`,
    )
    .join("");
  try {
    await csrf();
    const r = await fetch("/api/me");
    if (r.ok) await start();
    else loginScreen();
  } catch (e) {
    toast(e.message);
  }
  setInterval(() => {
    if (
      state.user &&
      !state.busy &&
      ["mesero", "cocina", "cajero"].includes(state.tab)
    )
      refresh().catch((e) => toast(e.message));
  }, 5000);
}
init();
