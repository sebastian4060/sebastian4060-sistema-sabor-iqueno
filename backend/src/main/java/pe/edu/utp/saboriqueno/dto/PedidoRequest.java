package pe.edu.utp.saboriqueno.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Recibe productos y cantidades; el servidor calcula los precios. */
public record PedidoRequest(
    @NotNull UUID solicitudId,
    @Pattern(regexp = "SALON|LLEVAR") @NotNull String tipo,
    Long mesaId,
    @Size(max = 100) String cliente,
    @Size(max = 500) String observacion,
    @NotEmpty @Size(max = 100) List<@Valid Item> items) {
  public record Item(
      @Positive long productoId,
      @Min(1) @Max(100) int cantidad,
      @Size(max = 300) String observacion) {}
}
