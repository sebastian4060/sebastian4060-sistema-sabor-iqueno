package pe.edu.utp.saboriqueno.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.saboriqueno.dao.*;
import pe.edu.utp.saboriqueno.dto.PedidoRequest;
import pe.edu.utp.saboriqueno.exception.BusinessException;
import pe.edu.utp.saboriqueno.model.*;

@Service
public class PedidoService {
  private final PedidoDAO pedidos;
  private final CatalogoDAO catalogo;
  private final InventarioDAO inventario;
  private final AuditoriaService auditoria;
  private final CajaDAO caja;
  private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

  public PedidoService(
      PedidoDAO pedidos,
      CatalogoDAO catalogo,
      InventarioDAO inventario,
      AuditoriaService auditoria,
      CajaDAO caja) {
    this.caja = caja;
    this.pedidos = pedidos;
    this.catalogo = catalogo;
    this.inventario = inventario;
    this.auditoria = auditoria;
  }

  public static String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Guarda todos los detalles y descuenta insumos de forma atómica. */
  @Transactional
  public Map<String, Object> crear(PedidoRequest r, UsuarioAutenticado user, String ip) {
    if (r.items() == null || r.items().isEmpty())
      throw new BusinessException("Agregue al menos un producto.");
    if (r.tipo().equals("SALON") && r.mesaId() == null)
      throw new BusinessException("Seleccione una mesa.");
    if (r.tipo().equals("LLEVAR") && (r.mesaId() != null || StringUtils.isBlank(r.cliente())))
      throw new BusinessException("Ingrese el nombre del cliente y no asigne mesa.");
    auditoria.contexto(user.id(), ip);
    pedidos.bloquearSolicitud(r.solicitudId());
    String hash = hash(r);
    var anterior = pedidos.porSolicitud(r.solicitudId());
    if (anterior != null) {
      if (((Number) anterior.get("id_usuario_mozo")).longValue() != user.id()
          || !hash.equals(anterior.get("solicitud_hash")))
        throw new BusinessException("La solicitud ya se utilizó con otros datos.");
      return detalle(((Number) anterior.get("id_pedido")).longValue());
    }
    Long pedidoExistente = null;
    BigDecimal totalAnterior = BigDecimal.ZERO;
    if (r.mesaId() != null) {
      pedidos.bloquearMesa(r.mesaId());
      pedidoExistente = pedidos.pedidoActivo(r.mesaId());
      if (pedidoExistente != null) {
        var activo = pedidos.bloquear(pedidoExistente);
        totalAnterior = (BigDecimal) activo.get("total_pedido");
      }
    }
    List<Producto> productos = new ArrayList<>();
    List<List<Map<String, Object>>> recetas = new ArrayList<>();
    Map<Long, BigDecimal> consumo = new TreeMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (var item : r.items()) {
      var p = catalogo.producto(item.productoId());
      productos.add(p);
      total = total.add(ReglasPedido.importe(p.precio(), item.cantidad()));
      var receta = inventario.receta(p.id());
      if (receta.isEmpty())
        throw new BusinessException("El producto " + p.nombre() + " no tiene una receta activa.");
      recetas.add(receta);
      for (var x : receta) {
        if (!Objects.equals(x.get("unidad_medida"), x.get("unidad_stock")))
          throw new BusinessException("La unidad de la receta no coincide con el inventario.");
        long id = ((Number) x.get("id_insumo")).longValue();
        BigDecimal qty =
            ((BigDecimal) x.get("cantidad")).multiply(BigDecimal.valueOf(item.cantidad()));
        consumo.merge(id, qty, BigDecimal::add);
      }
    }
    Map<Long, BigDecimal> stocks = new HashMap<>();
    for (var entry : consumo.entrySet()) {
      var stock = inventario.bloquearStock(entry.getKey());
      ReglasPedido.descontar(stock, entry.getValue());
      stocks.put(entry.getKey(), stock);
    }
    long id;
    if (pedidoExistente == null) {
      id = pedidos.crear(r, user.id(), hash);
    } else {
      id = pedidoExistente;
    }
    pedidos.registrarSolicitud(r.solicitudId(), id, user.id(), hash);
    for (int n = 0; n < r.items().size(); n++) {
      var item = r.items().get(n);
      long detalle =
          pedidos.agregarDetalle(
              id, productos.get(n), item.cantidad(), StringUtils.trimToNull(item.observacion()));
      for (var x : recetas.get(n)) {
        long insumo = ((Number) x.get("id_insumo")).longValue();
        BigDecimal qty =
            ((BigDecimal) x.get("cantidad")).multiply(BigDecimal.valueOf(item.cantidad()));
        BigDecimal antes = stocks.get(insumo), despues = antes.subtract(qty);
        inventario.movimiento(
            insumo, user.id(), detalle, "SALIDA", qty, antes, despues, "Consumo por pedido");
        stocks.put(insumo, despues);
      }
    }
    pedidos.total(id, totalAnterior.add(total));
    pedidos.marcarNuevosPendientes(id);
    pedidos.notificar(id, "Nuevo pedido registrado");
    log.info("Pedido {} registrado por usuario {}", id, user.id());
    return detalle(id);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> activos() {
    var rows = pedidos.activos();
    rows.forEach(r -> r.put("items", pedidos.lineas(((Number) r.get("id_pedido")).longValue())));
    return rows;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> detalle(long id) {
    var r = pedidos.detalle(id);
    r.put("items", pedidos.lineas(id));
    return r;
  }

  @Transactional
  public Map<String, Object> estado(long id, String nuevo, UsuarioAutenticado user, String ip) {
    auditoria.contexto(user.id(), ip);
    var datos = pedidos.detalle(id);
    if (datos.get("id_mesa") != null)
      pedidos.bloquearMesa(((Number) datos.get("id_mesa")).longValue());
    var p = pedidos.bloquear(id);
    ReglasPedido.transicion((String) p.get("estado"), nuevo);
    if (nuevo.equals("ANULADO")) {
      boolean hayPreparados =
          pedidos.lineas(id).stream()
              .anyMatch(linea -> !"PENDIENTE".equals(linea.get("estado_cocina")));
      if (hayPreparados)
        throw new BusinessException(
            "No se puede anular una cuenta con platos que ya entraron a preparación.");
      for (var m : inventario.salidas(id)) {
        long insumo = ((Number) m.get("id_insumo")).longValue();
        var stock = inventario.bloquearStock(insumo);
        var qty = (BigDecimal) m.get("cantidad");
        inventario.movimiento(
            insumo,
            user.id(),
            ((Number) m.get("id_detalle_pedido")).longValue(),
            "ENTRADA",
            qty,
            stock,
            stock.add(qty),
            "Devolución por anulación");
      }
    }
    pedidos.cambiarEstado(id, nuevo);
    if (nuevo.equals("ANULADO") && p.get("id_mesa") != null)
      caja.liberarMesa(((Number) p.get("id_mesa")).longValue());
    pedidos.notificar(id, "Estado actualizado: " + nuevo);
    return detalle(id);
  }
}
