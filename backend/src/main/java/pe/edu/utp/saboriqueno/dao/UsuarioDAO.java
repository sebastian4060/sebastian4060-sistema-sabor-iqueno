package pe.edu.utp.saboriqueno.dao;

import java.util.Map;

/** Consulta credenciales y perfiles activos. */
public interface UsuarioDAO {
  Map<String, Object> buscarPorEmail(String email);
}
