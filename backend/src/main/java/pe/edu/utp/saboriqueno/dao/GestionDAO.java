package pe.edu.utp.saboriqueno.dao;

import java.util.*;

/** Consultas de las pantallas de gestión, sin exponer hashes de usuarios. */
public interface GestionDAO {
  Map<String, Object> datos();

  Map<String, Object> reportes();
}
