package mtf.com.overture.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ExchangeRequest(@NotBlank String code) {
}
