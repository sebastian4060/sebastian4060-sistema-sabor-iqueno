package pe.edu.utp.saboriqueno.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Asocia cada escritura con el usuario autenticado durante la transacción actual. */
@Service
public class AuditoriaService {
  private final JdbcTemplate jdbc;

  public AuditoriaService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void contexto(long usuario, String ip) {
    jdbc.queryForObject(
        "SELECT set_config('app.user_id', ?, true)", String.class, Long.toString(usuario));
    jdbc.queryForObject("SELECT set_config('app.ip', ?, true)", String.class, ip);
  }
}
