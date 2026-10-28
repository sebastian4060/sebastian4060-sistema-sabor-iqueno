package pe.edu.utp.saboriqueno.dao;

import java.math.BigDecimal;
import java.util.*;

public interface InventarioDAO {
  List<Map<String, Object>> receta(long producto);

  BigDecimal bloquearStock(long insumo);

  void movimiento(
      long insumo,
      long usuario,
      Long detalle,
      String tipo,
      BigDecimal cantidad,
      BigDecimal antes,
      BigDecimal despues,
      String motivo);

  List<Map<String, Object>> salidas(long pedido);
}
