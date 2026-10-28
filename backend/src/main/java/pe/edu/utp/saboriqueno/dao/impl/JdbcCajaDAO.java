package pe.edu.utp.saboriqueno.dao.impl;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.utp.saboriqueno.dao.CajaDAO;
import pe.edu.utp.saboriqueno.dto.PagoRequest;

@Repository
public class JdbcCajaDAO implements CajaDAO {
  private final JdbcTemplate jdbc;

  public JdbcCajaDAO(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> pagoPrevio(UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT"
                + " p.id_pago,p.monto,p.solicitud_hash,c.id_pedido,c.id_usuario_cajero,co.numero_serie,co.numero_correlativo"
                + " FROM pago p JOIN cuenta c USING(id_cuenta) JOIN comprobante co USING(id_cuenta)"
                + " WHERE p.solicitud_id=?",
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public boolean pagado(long pedido) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM cuenta WHERE id_pedido=? AND estado='PAGADA')",
            Boolean.class,
            pedido));
  }

  public Map<String, Object> cobrar(
      long pedido, Long mesa, BigDecimal total, long user, PagoRequest r, String hash) {
    long cuenta =
        jdbc.queryForObject(
            "INSERT INTO cuenta(id_pedido,id_mesa,id_usuario_cajero,subtotal,estado)"
                + " VALUES(?,?,?,?,'PAGADA') RETURNING id_cuenta",
            Long.class,
            pedido,
            mesa,
            user,
            total);
    jdbc.update(
        "INSERT INTO detalle_cuenta(id_cuenta,id_producto,cantidad,precio_unitario) SELECT"
            + " ?,id_producto,cantidad,precio_unitario FROM detalle_pedido WHERE id_pedido=? AND"
            + " activo",
        cuenta,
        pedido);
    long metodo =
        jdbc.queryForObject(
            "SELECT id_metodo_pago FROM metodo_pago WHERE nombre=? AND activo",
            Long.class,
            r.metodo());
    long pago =
        jdbc.queryForObject(
            "INSERT INTO"
                + " pago(id_cuenta,id_metodo_pago,monto,referencia,solicitud_id,solicitud_hash)"
                + " VALUES(?,?,?,?,?,?) RETURNING id_pago",
            Long.class,
            cuenta,
            metodo,
            total,
            r.referencia(),
            r.solicitudId(),
            hash);
    String correlativo =
        jdbc.queryForObject("SELECT nextval('ticket_correlativo_seq')::text", String.class);
    jdbc.update(
        "INSERT INTO comprobante(id_cuenta,tipo,numero_serie,numero_correlativo,cliente_ruc)"
            + " VALUES(?,'TICKET','T001',?,?)",
        cuenta,
        correlativo,
        r.ruc());
    return Map.of(
        "id_pago",
        pago,
        "id_cuenta",
        cuenta,
        "monto",
        total,
        "numero_serie",
        "T001",
        "numero_correlativo",
        correlativo,
        "message",
        "Pago registrado. Ticket interno sin validez tributaria.");
  }

  public void liberarMesa(Long mesa) {
    if (mesa != null)
      jdbc.update(
          "UPDATE mesa SET estado='LIBRE' WHERE id_mesa=? AND NOT EXISTS(SELECT 1 FROM pedido p"
              + " JOIN estado_pedido e USING(id_estado_pedido) WHERE p.id_mesa=? AND p.activo AND"
              + " e.nombre NOT IN ('PAGADO','ANULADO'))",
          mesa,
          mesa);
  }

  public List<Map<String, Object>> ventas() {
    return jdbc.queryForList(
        "SELECT p.id_pago,p.fecha_hora,p.monto,m.nombre"
            + " metodo,c.id_pedido,co.numero_serie,co.numero_correlativo FROM pago p JOIN"
            + " metodo_pago m USING(id_metodo_pago) JOIN cuenta c USING(id_cuenta) JOIN comprobante"
            + " co USING(id_cuenta) WHERE p.estado='CONFIRMADO' ORDER BY p.id_pago DESC LIMIT 500");
  }
}
