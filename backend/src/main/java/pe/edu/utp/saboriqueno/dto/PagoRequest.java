package pe.edu.utp.saboriqueno.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

/** Identifica un cobro completo y el medio confirmado por el cajero. */
public record PagoRequest(
    @NotNull UUID solicitudId,
    @NotBlank @Pattern(regexp = "Efectivo|Tarjeta|Yape|Plin") String metodo,
    @Pattern(regexp = "^$|[0-9]{11}") String ruc,
    @Size(max = 50) String referencia) {}
