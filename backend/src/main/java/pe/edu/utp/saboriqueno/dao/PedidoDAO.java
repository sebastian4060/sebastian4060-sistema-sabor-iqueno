package pe.edu.utp.saboriqueno.dao;

import java.math.BigDecimal;
import java.util.*;
import pe.edu.utp.saboriqueno.dto.PedidoRequest;
import pe.edu.utp.saboriqueno.model.Producto;

public interface PedidoDAO {
  void bloquearSolicitud(UUID id);

  Map<String, Object> porSolicitud(UUID id);

  void bloquearMesa(long id);

  Long pedidoActivo(long mesa);

  void registrarSolicitud(UUID solicitud, long pedido, long usuario, String hash);

  void marcarNuevosPendientes(long pedido);

  long crear(PedidoRequest request, long usuario, String hash);

  long agregarDetalle(long pedido, Producto producto, int cantidad, String nota);

  void total(long pedido, BigDecimal total);

  Map<String, Object> bloquear(long id);

  void cambiarEstado(long id, String estado);

  void notificar(long id, String mensaje);

  List<Map<String, Object>> activos();

  Map<String, Object> detalle(long id);

  List<Map<String, Object>> lineas(long id);
}
