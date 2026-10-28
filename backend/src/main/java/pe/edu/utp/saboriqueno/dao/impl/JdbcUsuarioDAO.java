package pe.edu.utp.saboriqueno.dao.impl;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.utp.saboriqueno.dao.UsuarioDAO;

@Repository
public class JdbcUsuarioDAO implements UsuarioDAO {
  private final JdbcTemplate jdbc;

  public JdbcUsuarioDAO(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> buscarPorEmail(String email) {
    return jdbc.queryForMap(
        "SELECT u.id_usuario,u.email,u.nombre,u.password_hash,r.nombre rol FROM usuario u JOIN rol"
            + " r USING(id_rol) WHERE lower(u.email)=lower(?) AND u.activo AND u.eliminado_en IS"
            + " NULL AND r.activo",
        email);
  }
}
