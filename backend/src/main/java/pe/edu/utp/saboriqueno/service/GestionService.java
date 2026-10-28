package pe.edu.utp.saboriqueno.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.saboriqueno.dao.InventarioDAO;
import pe.edu.utp.saboriqueno.dto.GestionRequests.*;
import pe.edu.utp.saboriqueno.exception.BusinessException;
import pe.edu.utp.saboriqueno.model.UsuarioAutenticado;

/** Operaciones sencillas de gestión, dentro de una transacción con auditoría. */
@Service
public class GestionService {
  private final JdbcTemplate jdbc;
  private final InventarioDAO inventario;
  private final AuditoriaService auditoria;
  private final PasswordEncoder encoder;

  public GestionService(
      JdbcTemplate jdbc,
      InventarioDAO inventario,
      AuditoriaService auditoria,
      PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.inventario = inventario;
    this.auditoria = auditoria;
    this.encoder = encoder;
  }

  private void contexto(UsuarioAutenticado u, String ip) {
    auditoria.contexto(u.id(), ip);
  }

  @Transactional
  public void movimiento(long id, Movimiento r, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    var antes = inventario.bloquearStock(id);
    var despues =
        switch (r.tipo()) {
          case "ENTRADA" -> antes.add(r.cantidad());
          case "MERMA" -> antes.subtract(r.cantidad());
          case "AJUSTE" -> r.cantidad();
          default -> throw new BusinessException("Tipo de movimiento inválido.");
        };
    if (despues.signum() < 0) throw new BusinessException("La merma supera el stock disponible.");
    var cantidad = r.tipo().equals("AJUSTE") ? despues.subtract(antes).abs() : r.cantidad();
    if (cantidad.signum() == 0) throw new BusinessException("El movimiento no cambia el stock.");
    inventario.movimiento(id, u.id(), null, r.tipo(), cantidad, antes, despues, r.motivo().trim());
  }

  @Transactional
  public long compra(Compra r, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    jdbc.queryForList(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", r.solicitudId().toString());
    var prev =
        jdbc.queryForList(
            "SELECT id_compra,solicitud_hash FROM compra WHERE solicitud_id=?", r.solicitudId());
    String hash = PedidoService.hash(r);
    if (!prev.isEmpty()) {
      if (!hash.equals(prev.get(0).get("solicitud_hash")))
        throw new BusinessException("La solicitud ya fue utilizada con otros datos.");
      return ((Number) prev.get(0).get("id_compra")).longValue();
    }
    jdbc.queryForObject(
        "SELECT id_proveedor FROM proveedor WHERE id_proveedor=? AND activo",
        Long.class,
        r.proveedorId());
    var ids = new HashSet<Long>();
    BigDecimal total = BigDecimal.ZERO;
    for (var item : r.items()) {
      if (!ids.add(item.insumoId()))
        throw new BusinessException("No repita un insumo en la compra.");
      total = total.add(item.cantidad().multiply(item.costo()));
    }
    var stocks = new HashMap<Long, BigDecimal>();
    for (long id : new TreeSet<>(ids)) stocks.put(id, inventario.bloquearStock(id));
    long compra =
        jdbc.queryForObject(
            "INSERT INTO"
                + " compra(id_proveedor,id_usuario,fecha_compra,total,estado,documento,solicitud_id,solicitud_hash)"
                + " VALUES(?,?,?,?, 'RECIBIDA',?,?,?) RETURNING id_compra",
            Long.class,
            r.proveedorId(),
            u.id(),
            r.fecha(),
            total,
            r.documento(),
            r.solicitudId(),
            hash);
    for (var item : r.items()) {
      long detalle =
          jdbc.queryForObject(
              "INSERT INTO detalle_compra(id_compra,id_insumo,cantidad,precio_unitario)"
                  + " VALUES(?,?,?,?) RETURNING id_detalle_compra",
              Long.class,
              compra,
              item.insumoId(),
              item.cantidad(),
              item.costo());
      var antes = stocks.get(item.insumoId());
      var despues = antes.add(item.cantidad());
      jdbc.update(
          "UPDATE insumo SET stock_actual=?,precio_unitario=? WHERE id_insumo=?",
          despues,
          item.costo(),
          item.insumoId());
      jdbc.update(
          "INSERT INTO"
              + " movimiento_inventario(id_insumo,id_usuario,id_detalle_compra,tipo_movimiento,cantidad,stock_anterior,stock_resultante,motivo)"
              + " VALUES(?,?,?,'ENTRADA',?,?,?,?)",
          item.insumoId(),
          u.id(),
          detalle,
          item.cantidad(),
          antes,
          despues,
          "Compra #" + compra + " " + r.documento());
    }
    return compra;
  }

  @Transactional
  public void proveedor(Proveedor r, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    jdbc.update("INSERT INTO proveedor(nombre,ruc) VALUES(?,?)", r.nombre().trim(), r.ruc());
  }

  @Transactional
  public void usuario(NuevoUsuario r, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    if (r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("La contraseña supera el máximo de 72 bytes.");
    var rol =
        jdbc.queryForObject(
            "SELECT nombre FROM rol WHERE id_rol=? AND activo", String.class, r.rolId());
    if (!Set.of("Administrador", "Mozo", "Cocinero", "Cajero").contains(rol))
      throw new BusinessException("Rol no permitido.");
    jdbc.update(
        "INSERT INTO usuario(id_rol,nombre,apellido,email,password_hash) VALUES(?,?,?,?,?)",
        r.rolId(),
        r.nombre().trim(),
        r.apellido().trim(),
        r.email().trim().toLowerCase(Locale.ROOT),
        encoder.encode(r.password()));
  }

  @Transactional
  public void activo(long id, boolean activo, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    if (id == u.id()) throw new BusinessException("No puede desactivar su propia cuenta.");
    jdbc.queryForMap(
        "SELECT id_usuario FROM usuario WHERE id_usuario=? AND eliminado_en IS NULL FOR UPDATE",
        id);
    jdbc.update("UPDATE usuario SET activo=? WHERE id_usuario=?", activo, id);
  }

  @Transactional
  public void personal(Persona r, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    long id =
        jdbc.queryForObject(
            "INSERT INTO personal(nombre,apellido,cargo) VALUES(?,?,?) RETURNING id_personal",
            Long.class,
            r.nombre().trim(),
            r.apellido().trim(),
            r.cargo().trim());
    if (r.turnoId() != null) asignar(id, r.turnoId());
  }

  private void asignar(long id, long turno) {
    jdbc.queryForObject(
        "SELECT id_personal FROM personal WHERE id_personal=? AND activo FOR UPDATE",
        Long.class,
        id);
    jdbc.queryForObject(
        "SELECT id_turno FROM turno WHERE id_turno=? AND activo", Long.class, turno);
    jdbc.update(
        "DELETE FROM turno_personal WHERE id_personal=? AND fecha_asignacion=CURRENT_DATE", id);
    jdbc.update(
        "INSERT INTO turno_personal(id_personal,id_turno,fecha_asignacion)"
            + " VALUES(?,?,CURRENT_DATE)",
        id,
        turno);
  }

  @Transactional
  public void turno(long id, long turno, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    asignar(id, turno);
  }

  @Transactional
  public void receta(long id, Receta r, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    jdbc.queryForObject(
        "SELECT id_producto FROM producto WHERE id_producto=? AND activo FOR UPDATE",
        Long.class,
        id);
    long receta =
        jdbc.queryForObject(
            "INSERT INTO receta(id_producto,descripcion) VALUES(?,'Receta del producto') ON"
                + " CONFLICT(id_producto) DO UPDATE SET activo=true RETURNING id_receta",
            Long.class,
            id);
    var ids = new HashSet<Long>();
    for (var it : r.items())
      if (!ids.add(it.insumoId())) throw new BusinessException("No repita un insumo en la receta.");
    jdbc.update("DELETE FROM receta_detalle WHERE id_receta=?", receta);
    for (var it : r.items()) {
      String unidad =
          jdbc.queryForObject(
              "SELECT unidad_medida FROM insumo WHERE id_insumo=? AND activo",
              String.class,
              it.insumoId());
      jdbc.update(
          "INSERT INTO receta_detalle(id_receta,id_insumo,cantidad,unidad_medida) VALUES(?,?,?,?)",
          receta,
          it.insumoId(),
          it.cantidad(),
          unidad);
    }
  }

  @Transactional
  public void precio(long id, BigDecimal precio, UsuarioAutenticado u, String ip) {
    contexto(u, ip);
    if (jdbc.update("UPDATE producto SET precio_venta=? WHERE id_producto=? AND activo", precio, id)
        == 0) throw new BusinessException("Producto no encontrado.");
  }
}
