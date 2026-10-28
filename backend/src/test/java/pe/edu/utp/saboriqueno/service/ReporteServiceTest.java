package pe.edu.utp.saboriqueno.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import pe.edu.utp.saboriqueno.dao.CatalogoDAO;

class ReporteServiceTest {
  @Test
  void exportaTextoSinConvertirloEnFormula() throws Exception {
    CatalogoDAO dao = mock(CatalogoDAO.class);
    when(dao.insumos())
        .thenReturn(
            List.of(
                Map.of(
                    "id_insumo",
                    1,
                    "nombre",
                    "=2+3",
                    "unidad_medida",
                    "kg",
                    "stock_actual",
                    5,
                    "stock_minimo",
                    2,
                    "alerta",
                    false)));
    byte[] bytes = new ReporteService(dao).inventario();
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      assertEquals("=2+3", book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
      assertEquals(5, book.getSheetAt(0).getRow(1).getCell(3).getNumericCellValue());
    }
  }
}
