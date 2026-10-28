package pe.edu.utp.saboriqueno.dao;

import java.util.*;
import pe.edu.utp.saboriqueno.model.Producto;

public interface CatalogoDAO {
  List<Producto> productos();

  Producto producto(long id);

  List<Map<String, Object>> mesas();

  List<Map<String, Object>> insumos();

  List<Map<String, Object>> metodos();
}
