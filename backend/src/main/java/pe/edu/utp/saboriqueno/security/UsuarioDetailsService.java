package pe.edu.utp.saboriqueno.security;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import pe.edu.utp.saboriqueno.dao.UsuarioDAO;

@Service
public class UsuarioDetailsService implements UserDetailsService {
  private final UsuarioDAO dao;

  public UsuarioDetailsService(UsuarioDAO dao) {
    this.dao = dao;
  }

  public UserDetails loadUserByUsername(String email) {
    try {
      var u = dao.buscarPorEmail(email);
      return User.withUsername((String) u.get("email"))
          .password((String) u.get("password_hash"))
          .authorities((String) u.get("rol"))
          .build();
    } catch (EmptyResultDataAccessException e) {
      throw new UsernameNotFoundException("Credenciales inválidas");
    }
  }
}
