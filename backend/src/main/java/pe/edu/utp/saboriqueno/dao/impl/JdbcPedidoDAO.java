package pe.edu.utp.saboriqueno.dao.impl;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.utp.saboriqueno.dao.PedidoDAO;
import pe.edu.utp.saboriqueno.dto.PedidoRequest;
import pe.edu.utp.saboriqueno.exception.BusinessException;
import pe.edu.utp.saboriqueno.model.Producto;

@Repository
public class JdbcPedidoDAO implements PedidoDAO {
  private final JdbcTemplate jdbc;

  public JdbcPedidoDAO(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void bloquearSolicitud(UUID id) {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", id.toString());
  }

  public Map<String, Object> porSolicitud(UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT id_pedido,id_usuario AS id_usuario_mozo,solicitud_hash FROM solicitud_pedido"
                + " WHERE solicitud_id=?",
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public void bloquearMesa(long id) {
    var m =
        jdbc.queryForMap(
            "SELECT estado FROM mesa WHERE id_mesa=? AND activo AND eliminado_en IS NULL FOR"
                + " UPDATE",
            id);
    if (Set.of("RESERVADA", "MANTENIMIENTO").contains(m.get("estado")))
      throw new BusinessException("La mesa no está disponible.");
  }

  public Long pedidoActivo(long mesa) {
    var ids =
        jdbc.queryForList(
            "SELECT p.id_pedido FROM pedido p JOIN estado_pedido e USING(id_estado_pedido) WHERE"
                + " p.id_mesa=? AND p.activo AND e.nombre NOT IN ('PAGADO','ANULADO') ORDER BY"
                + " p.id_pedido",
            Long.class,
            mesa);
    if (ids.size() > 1)
      throw new BusinessException(
          "La mesa tiene varios pedidos activos. Revise los datos antes de continuar.");
    return ids.isEmpty() ? null : ids.get(0);
  }

  public void registrarSolicitud(UUID solicitud, long pedido, long usuario, String hash) {
    jdbc.update(
        "INSERT INTO solicitud_pedido(solicitud_id,id_pedido,id_usuario,solicitud_hash)"
            + " VALUES(?,?,?,?)",
        solicitud,
        pedido,
        usuario,
        hash);
  }

  /** Reactiva la atención sin modificar los estados de los platos anteriores. */
  public void marcarNuevosPendientes(long pedido) {
    jdbc.update(
        "UPDATE pedido SET id_estado_pedido=(SELECT id_estado_pedido FROM estado_pedido WHERE"
            + " nombre='REGISTRADO') WHERE id_pedido=?",
        pedido);
  }

  public long crear(PedidoRequest r, long user, String hash) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " pedido(id_mesa,id_usuario_mozo,id_estado_pedido,tipo_pedido,cliente_nombre,observacion,solicitud_id,solicitud_hash)"
            + " VALUES(?,?,(SELECT id_estado_pedido FROM estado_pedido WHERE"
            + " nombre='REGISTRADO'),?,?,?,?,?) RETURNING id_pedido",
        Long.class,
        r.mesaId(),
        user,
        r.tipo(),
        r.cliente(),
        r.observacion(),
        r.solicitudId(),
        hash);
  }

  public long agregarDetalle(long id, Producto p, int qty, String nota) {
    return jdbc.queryForObject(
        "INSERT INTO detalle_pedido(id_pedido,id_producto,cantidad,precio_unitario,observacion)"
            + " VALUES(?,?,?,?,?) RETURNING id_detalle_pedido",
        Long.class,
        id,
        p.id(),
        qty,
        p.precio(),
        nota);
  }

  public void total(long id, BigDecimal total) {
    jdbc.update("UPDATE pedido SET total_pedido=? WHERE id_pedido=?", total, id);
    jdbc.update(
        "UPDATE mesa SET estado='OCUPADA' WHERE id_mesa=(SELECT id_mesa FROM pedido WHERE"
            + " id_pedido=?)",
        id);
  }

  public Map<String, Object> bloquear(long id) {
    return jdbc.queryForMap(
        "SELECT p.*,e.nombre estado FROM pedido p JOIN estado_pedido e USING(id_estado_pedido)"
            + " WHERE p.id_pedido=? AND p.activo FOR UPDATE OF p",
        id);
  }

  public void cambiarEstado(long id, String estado) {
    jdbc.update(
        "UPDATE pedido SET id_estado_pedido=(SELECT id_estado_pedido FROM estado_pedido WHERE"
            + " nombre=?) WHERE id_pedido=?",
        estado,
        id);
    String detalle =
        switch (estado) {
          case "EN_PREPARACION" -> "PREPARANDO";
          case "LISTO" -> "LISTO";
          case "SERVIDO", "PAGADO" -> "ENTREGADO";
          case "ANULADO" -> "ANULADO";
          default -> "PENDIENTE";
        };
    if (estado.equals("EN_PREPARACION")) {
      jdbc.update(
          "UPDATE detalle_pedido SET estado_cocina='PREPARANDO' WHERE id_pedido=? AND"
              + " estado_cocina='PENDIENTE'",
          id);
    } else if (estado.equals("LISTO")) {
      jdbc.update(
          "UPDATE detalle_pedido SET estado_cocina='LISTO' WHERE id_pedido=? AND"
              + " estado_cocina='PREPARANDO'",
          id);
    } else {
      jdbc.update("UPDATE detalle_pedido SET estado_cocina=? WHERE id_pedido=?", detalle, id);
    }
  }

  public void notificar(long id, String mensaje) {
    jdbc.update("INSERT INTO notificacion_cocina(id_pedido,mensaje) VALUES(?,?)", id, mensaje);
  }

  private static final String SELECT =
      "SELECT p.id_pedido,p.id_mesa,m.numero"
          + " mesa_numero,p.cliente_nombre,p.tipo_pedido,p.total_pedido,p.fecha_hora,p.observacion,e.nombre"
          + " estado FROM pedido p JOIN estado_pedido e USING(id_estado_pedido) LEFT JOIN mesa m"
          + " USING(id_mesa) ";

  public List<Map<String, Object>> activos() {
    return jdbc.queryForList(
        SELECT
            + "WHERE p.activo AND e.nombre NOT IN ('PAGADO','ANULADO') ORDER BY"
            + " p.fecha_hora,p.id_pedido");
  }

  public Map<String, Object> detalle(long id) {
    return jdbc.queryForMap(SELECT + "WHERE p.id_pedido=?", id);
  }

  public List<Map<String, Object>> lineas(long id) {
    return jdbc.queryForList(
        "SELECT"
            + " d.id_detalle_pedido,d.id_producto,p.nombre,d.cantidad,d.precio_unitario,d.subtotal,d.observacion,d.estado_cocina"
            + " FROM detalle_pedido d JOIN producto p USING(id_producto) WHERE d.id_pedido=? AND"
            + " d.activo ORDER BY d.id_detalle_pedido",
        id);
  }
}
