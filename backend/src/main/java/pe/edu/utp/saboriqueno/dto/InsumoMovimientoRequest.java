package pe.edu.utp.saboriqueno.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record InsumoMovimientoRequest(
    @NotNull @DecimalMin("0.001") @Digits(integer = 7, fraction = 3) BigDecimal cantidad,
    @NotBlank @Size(max = 200) String motivo) {}
