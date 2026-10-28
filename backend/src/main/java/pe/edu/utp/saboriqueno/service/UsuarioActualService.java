package pe.edu.utp.saboriqueno.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pe.edu.utp.saboriqueno.dao.UsuarioDAO;
import pe.edu.utp.saboriqueno.model.UsuarioAutenticado;

@Service
public class UsuarioActualService {
  private final UsuarioDAO dao;

  public UsuarioActualService(UsuarioDAO dao) {
    this.dao = dao;
  }

  public UsuarioAutenticado obtener(Authentication auth) {
    var u = dao.buscarPorEmail(auth.getName());
    return new UsuarioAutenticado(
        ((Number) u.get("id_usuario")).longValue(),
        (String) u.get("email"),
        (String) u.get("nombre"),
        (String) u.get("rol"));
  }
}
