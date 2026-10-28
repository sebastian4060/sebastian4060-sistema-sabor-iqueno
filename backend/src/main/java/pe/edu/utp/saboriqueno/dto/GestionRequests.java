package pe.edu.utp.saboriqueno.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class GestionRequests {
  private GestionRequests() {}

  public record Movimiento(
      @NotNull @Pattern(regexp = "ENTRADA|MERMA|AJUSTE") String tipo,
      @NotNull @DecimalMin("0") @Digits(integer = 7, fraction = 3) BigDecimal cantidad,
      @NotBlank @Size(max = 200) String motivo) {}

  public record Compra(
      @NotNull UUID solicitudId,
      @Positive long proveedorId,
      @NotNull @PastOrPresent LocalDate fecha,
      @NotNull @Size(max = 80) String documento,
      @NotEmpty @Size(max = 100) List<@Valid CompraItem> items) {}

  public record CompraItem(
      @Positive long insumoId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal cantidad,
      @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal costo) {}

  public record Proveedor(
      @NotBlank @Size(max = 100) String nombre,
      @NotNull @Pattern(regexp = "[0-9]{11}") String ruc) {}

  public record NuevoUsuario(
      @NotBlank @Size(max = 100) String nombre,
      @NotBlank @Size(max = 100) String apellido,
      @NotBlank @Email @Size(max = 100) String email,
      @NotNull @Size(min = 8, max = 72) String password,
      @Positive long rolId) {}

  public record Activo(@NotNull Boolean activo) {}

  public record Persona(
      @NotBlank @Size(max = 100) String nombre,
      @NotBlank @Size(max = 100) String apellido,
      @NotBlank @Size(max = 50) String cargo,
      @Positive Long turnoId) {}

  public record Turno(@Positive long turnoId) {}

  public record Receta(@NotEmpty @Size(max = 100) List<@Valid RecetaItem> items) {}

  public record RecetaItem(
      @Positive long insumoId,
      @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal cantidad) {}

  public record Precio(
      @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal precio) {}
}
