package pe.edu.utp.saboriqueno.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.saboriqueno.dao.InventarioDAO;
import pe.edu.utp.saboriqueno.dto.InsumoMovimientoRequest;
import pe.edu.utp.saboriqueno.model.UsuarioAutenticado;

@Service
public class InventarioService {
  private final InventarioDAO dao;
  private final AuditoriaService auditoria;

  public InventarioService(InventarioDAO dao, AuditoriaService auditoria) {
    this.dao = dao;
    this.auditoria = auditoria;
  }

  @Transactional
  public void entrada(long id, InsumoMovimientoRequest r, UsuarioAutenticado user, String ip) {
    auditoria.contexto(user.id(), ip);
    var stock = dao.bloquearStock(id);
    dao.movimiento(
        id, user.id(), null, "ENTRADA", r.cantidad(), stock, stock.add(r.cantidad()), r.motivo());
  }
}
