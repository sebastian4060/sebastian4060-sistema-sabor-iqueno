package pe.edu.utp.saboriqueno.dao;

import java.math.BigDecimal;
import java.util.*;
import pe.edu.utp.saboriqueno.dto.PagoRequest;

public interface CajaDAO {
  Map<String, Object> pagoPrevio(UUID solicitud);

  boolean pagado(long pedido);

  Map<String, Object> cobrar(
      long pedido, Long mesa, BigDecimal total, long usuario, PagoRequest request, String hash);

  void liberarMesa(Long mesa);

  List<Map<String, Object>> ventas();
}
