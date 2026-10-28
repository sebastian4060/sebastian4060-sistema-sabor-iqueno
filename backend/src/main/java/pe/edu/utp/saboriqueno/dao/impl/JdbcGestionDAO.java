package pe.edu.utp.saboriqueno.dao.impl;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.utp.saboriqueno.dao.GestionDAO;

@Repository
public class JdbcGestionDAO implements GestionDAO {
  private final JdbcTemplate jdbc;

  public JdbcGestionDAO(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> datos() {
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "roles",
        jdbc.queryForList(
            "SELECT id_rol,nombre FROM rol WHERE activo AND nombre IN"
                + " ('Administrador','Mozo','Cocinero','Cajero') ORDER BY id_rol"));
    result.put(
        "usuarios",
        jdbc.queryForList(
            "SELECT u.id_usuario,u.nombre,u.apellido,u.email,u.activo,r.nombre rol FROM usuario u"
                + " JOIN rol r USING(id_rol) WHERE u.eliminado_en IS NULL ORDER BY u.id_usuario"));
    result.put(
        "proveedores",
        jdbc.queryForList(
            "SELECT id_proveedor,nombre,ruc FROM proveedor WHERE activo ORDER BY nombre"));
    result.put(
        "compras",
        jdbc.queryForList(
            "SELECT c.id_compra,c.fecha_compra,c.total,c.documento,p.nombre proveedor FROM compra c"
                + " JOIN proveedor p USING(id_proveedor) WHERE c.activo ORDER BY c.id_compra DESC"
                + " LIMIT 100"));
    result.put(
        "movimientos",
        jdbc.queryForList(
            "SELECT m.id_movimiento,m.id_insumo,i.nombre"
                + " insumo,m.tipo_movimiento,m.cantidad,m.stock_anterior,m.stock_resultante,m.fecha_movimiento,m.motivo,u.nombre"
                + " usuario FROM movimiento_inventario m JOIN insumo i USING(id_insumo) JOIN"
                + " usuario u USING(id_usuario) ORDER BY m.id_movimiento DESC LIMIT 500"));
    result.put(
        "conciliacion",
        jdbc.queryForList(
            "SELECT i.id_insumo,i.nombre,i.stock_actual,m.stock_resultante"
                + " ultimo_saldo,i.stock_actual-m.stock_resultante diferencia FROM insumo i JOIN"
                + " LATERAL (SELECT stock_resultante FROM movimiento_inventario WHERE"
                + " id_insumo=i.id_insumo ORDER BY id_movimiento DESC LIMIT 1) m ON true WHERE"
                + " i.activo AND i.stock_actual<>m.stock_resultante"));
    result.put(
        "personal",
        jdbc.queryForList(
            "SELECT p.id_personal,p.nombre,p.apellido,p.cargo,p.activo,t.nombre turno,tp.id_turno"
                + " FROM personal p LEFT JOIN turno_personal tp ON tp.id_personal=p.id_personal AND"
                + " tp.fecha_asignacion=CURRENT_DATE LEFT JOIN turno t USING(id_turno) WHERE"
                + " p.eliminado_en IS NULL ORDER BY p.id_personal"));
    result.put(
        "turnos",
        jdbc.queryForList(
            "SELECT id_turno,nombre,hora_inicio,hora_fin FROM turno WHERE activo ORDER BY"
                + " id_turno"));
    result.put(
        "auditoria",
        jdbc.queryForList(
            "SELECT a.id_auditoria,a.fecha_hora,coalesce(u.nombre,'Sistema')"
                + " usuario,a.tabla_afectada,a.accion,a.registro_id,a.ip_origen FROM auditoria a"
                + " LEFT JOIN usuario u USING(id_usuario) ORDER BY a.id_auditoria DESC LIMIT 200"));
    result.put(
        "recetas",
        jdbc.queryForList(
            "SELECT r.id_producto,d.id_insumo,d.cantidad FROM receta r JOIN receta_detalle d"
                + " USING(id_receta) WHERE r.activo AND d.activo ORDER BY"
                + " r.id_producto,d.id_insumo"));
    return result;
  }

  public Map<String, Object> reportes() {
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "resumen",
        jdbc.queryForMap(
            "SELECT count(*) cuentas_pagadas,coalesce(sum(total),0)"
                + " ventas_total,coalesce(sum(total) FILTER (WHERE"
                + " fecha_hora::date=CURRENT_DATE),0) ventas_hoy FROM cuenta WHERE"
                + " estado='PAGADA'"));
    result.put(
        "porDia",
        jdbc.queryForList(
            "SELECT fecha_hora::date fecha,count(*) cuentas,sum(total) total FROM cuenta WHERE"
                + " estado='PAGADA' GROUP BY fecha_hora::date ORDER BY fecha DESC LIMIT 30"));
    result.put(
        "porMetodo",
        jdbc.queryForList(
            "SELECT m.nombre metodo,count(*) pagos,sum(p.monto) total FROM pago p JOIN metodo_pago"
                + " m USING(id_metodo_pago) WHERE p.estado='CONFIRMADO' GROUP BY m.nombre ORDER BY"
                + " total DESC"));
    result.put(
        "productos",
        jdbc.queryForList(
            "SELECT p.nombre,sum(d.cantidad) cantidad,sum(d.subtotal) total FROM detalle_cuenta d"
                + " JOIN producto p USING(id_producto) JOIN cuenta c USING(id_cuenta) WHERE"
                + " c.estado='PAGADA' GROUP BY p.nombre ORDER BY cantidad DESC LIMIT 10"));
    return result;
  }
}
