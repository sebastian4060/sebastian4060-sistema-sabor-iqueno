package pe.edu.utp.saboriqueno.dao.impl;

import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import pe.edu.utp.saboriqueno.dao.CatalogoDAO;
import pe.edu.utp.saboriqueno.model.Producto;

@Repository
public class JdbcCatalogoDAO implements CatalogoDAO {
  private final JdbcTemplate jdbc;
  private static final RowMapper<Producto> MAPPER =
      (r, n) ->
          new Producto(
              r.getLong("id_producto"),
              r.getString("nombre"),
              r.getString("categoria"),
              r.getBigDecimal("precio_venta"));

  public JdbcCatalogoDAO(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Producto> productos() {
    return jdbc.query(
        "SELECT p.*,c.nombre categoria FROM producto p JOIN categoria_producto c"
            + " USING(id_categoria_producto) WHERE p.activo AND p.eliminado_en IS NULL ORDER BY"
            + " p.id_producto",
        MAPPER);
  }

  public Producto producto(long id) {
    return jdbc.queryForObject(
        "SELECT p.*,c.nombre categoria FROM producto p JOIN categoria_producto c"
            + " USING(id_categoria_producto) WHERE p.id_producto=? AND p.activo AND p.eliminado_en"
            + " IS NULL",
        MAPPER,
        id);
  }

  public List<Map<String, Object>> mesas() {
    return jdbc.queryForList(
        "SELECT id_mesa,numero,capacidad,estado FROM mesa WHERE activo AND eliminado_en IS NULL"
            + " ORDER BY numero");
  }

  public List<Map<String, Object>> insumos() {
    return jdbc.queryForList(
        "SELECT"
            + " id_insumo,nombre,unidad_medida,stock_actual,stock_minimo,precio_unitario,stock_actual<=stock_minimo"
            + " alerta FROM insumo WHERE activo AND eliminado_en IS NULL ORDER BY nombre");
  }

  public List<Map<String, Object>> metodos() {
    return jdbc.queryForList(
        "SELECT id_metodo_pago,nombre FROM metodo_pago WHERE activo ORDER BY id_metodo_pago");
  }
}
