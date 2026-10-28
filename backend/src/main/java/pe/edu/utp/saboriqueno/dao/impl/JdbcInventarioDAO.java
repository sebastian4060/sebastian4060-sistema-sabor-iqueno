package pe.edu.utp.saboriqueno.dao.impl;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.utp.saboriqueno.dao.InventarioDAO;

@Repository
public class JdbcInventarioDAO implements InventarioDAO {
  private final JdbcTemplate jdbc;

  public JdbcInventarioDAO(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> receta(long producto) {
    return jdbc.queryForList(
        "SELECT d.id_insumo,d.cantidad,d.unidad_medida,i.unidad_medida unidad_stock FROM receta r"
            + " JOIN receta_detalle d USING(id_receta) JOIN insumo i USING(id_insumo) WHERE"
            + " r.id_producto=? AND r.activo AND d.activo AND i.activo ORDER BY d.id_insumo",
        producto);
  }

  public BigDecimal bloquearStock(long id) {
    return jdbc.queryForObject(
        "SELECT stock_actual FROM insumo WHERE id_insumo=? AND activo AND eliminado_en IS NULL FOR"
            + " UPDATE",
        BigDecimal.class,
        id);
  }

  public void movimiento(
      long id,
      long user,
      Long detalle,
      String tipo,
      BigDecimal qty,
      BigDecimal antes,
      BigDecimal despues,
      String motivo) {
    jdbc.update("UPDATE insumo SET stock_actual=? WHERE id_insumo=?", despues, id);
    jdbc.update(
        "INSERT INTO"
            + " movimiento_inventario(id_insumo,id_usuario,id_detalle_pedido,tipo_movimiento,cantidad,stock_anterior,stock_resultante,motivo)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id,
        user,
        detalle,
        tipo,
        qty,
        antes,
        despues,
        motivo);
  }

  public List<Map<String, Object>> salidas(long pedido) {
    return jdbc.queryForList(
        "SELECT m.* FROM movimiento_inventario m JOIN detalle_pedido d USING(id_detalle_pedido)"
            + " WHERE d.id_pedido=? AND m.tipo_movimiento='SALIDA' ORDER BY"
            + " m.id_insumo,m.id_movimiento",
        pedido);
  }
}
