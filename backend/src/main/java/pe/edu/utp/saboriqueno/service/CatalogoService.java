package pe.edu.utp.saboriqueno.service;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.springframework.stereotype.Service;
import pe.edu.utp.saboriqueno.dao.CatalogoDAO;
import pe.edu.utp.saboriqueno.model.Producto;

@Service
public class CatalogoService {
  private final CatalogoDAO dao;

  public CatalogoService(CatalogoDAO dao) {
    this.dao = dao;
  }

  /** Devuelve una lista que no permite agregar ni eliminar elementos. */
  public List<Producto> productos() {
    return ImmutableList.copyOf(dao.productos());
  }
}
