package pe.edu.utp.saboriqueno.service;

import java.io.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import pe.edu.utp.saboriqueno.dao.CatalogoDAO;

@Service
public class ReporteService {
  private final CatalogoDAO dao;
  private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

  public ReporteService(CatalogoDAO dao) {
    this.dao = dao;
  }

  /** Genera un archivo Excel con celdas de texto y valores numéricos. */
  public byte[] inventario() {
    try (var book = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = book.createSheet("Inventario");
      var header = sheet.createRow(0);
      String[] cols = {"ID", "Insumo", "Unidad", "Stock actual", "Stock mínimo", "Alerta"};
      for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
      int n = 1;
      for (var x : dao.insumos()) {
        var row = sheet.createRow(n++);
        row.createCell(0).setCellValue(((Number) x.get("id_insumo")).longValue());
        row.createCell(1).setCellValue((String) x.get("nombre"));
        row.createCell(2).setCellValue((String) x.get("unidad_medida"));
        row.createCell(3).setCellValue(((Number) x.get("stock_actual")).doubleValue());
        row.createCell(4).setCellValue(((Number) x.get("stock_minimo")).doubleValue());
        row.createCell(5)
            .setCellValue(Boolean.TRUE.equals(x.get("alerta")) ? "Stock bajo" : "Normal");
      }
      for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, i == 1 ? 10000 : 4500);
      sheet.createFreezePane(0, 1);
      book.write(out);
      log.info("Reporte de inventario generado con {} filas", n - 1);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo generar el archivo.", e);
    }
  }
}
