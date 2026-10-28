package pe.edu.utp.saboriqueno.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.saboriqueno.dao.*;
import pe.edu.utp.saboriqueno.dto.PagoRequest;
import pe.edu.utp.saboriqueno.exception.BusinessException;
import pe.edu.utp.saboriqueno.model.UsuarioAutenticado;

@Service
public class CajaService {
  private final PedidoDAO pedidos;
  private final CajaDAO caja;
  private final AuditoriaService auditoria;

  public CajaService(PedidoDAO pedidos, CajaDAO caja, AuditoriaService auditoria) {
    this.pedidos = pedidos;
    this.caja = caja;
    this.auditoria = auditoria;
  }

  /** Registra un cobro una sola vez y cierra la cuenta en la misma transacción. */
  @Transactional
  public Map<String, Object> cobrar(long id, PagoRequest r, UsuarioAutenticado user, String ip) {
    auditoria.contexto(user.id(), ip);
    pedidos.bloquearSolicitud(r.solicitudId());
    String hash = PedidoService.hash(id + ":" + r.toString());
    var previo = caja.pagoPrevio(r.solicitudId());
    if (previo != null) {
      if (((Number) previo.get("id_usuario_cajero")).longValue() != user.id()
          || !hash.equals(previo.get("solicitud_hash")))
        throw new BusinessException("La solicitud ya se utilizó con otros datos.");
      var result = new HashMap<>(previo);
      result.remove("solicitud_hash");
      return result;
    }
    var datos = pedidos.detalle(id);
    if (datos.get("id_mesa") != null)
      pedidos.bloquearMesa(((Number) datos.get("id_mesa")).longValue());
    var p = pedidos.bloquear(id);
    if (!Set.of("LISTO", "SERVIDO").contains(p.get("estado")) || caja.pagado(id))
      throw new BusinessException("El pedido debe estar listo y no pagado.");
    var total = (BigDecimal) p.get("total_pedido");
    if (total.signum() <= 0) throw new BusinessException("El total debe ser mayor a cero.");
    Long mesa = p.get("id_mesa") == null ? null : ((Number) p.get("id_mesa")).longValue();
    var result = caja.cobrar(id, mesa, total, user.id(), r, hash);
    pedidos.cambiarEstado(id, "PAGADO");
    caja.liberarMesa(mesa);
    return result;
  }
}
