package com.biblioteca.acervo.dto;

import com.biblioteca.acervo.LimitesLivro;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AnotacaoRequestDTO(
        @Min(value = LimitesLivro.AVALIACAO_MIN, message = "A avaliação deve ser no mínimo 1 estrela.")
        @Max(value = LimitesLivro.AVALIACAO_MAX, message = "A avaliação deve ser no máximo 5 estrelas.")
        Integer avaliacao,

        @Size(max = LimitesLivro.ANOTACAO, message = "A anotação deve ter no máximo 4000 caracteres.")
        String anotacao
) {
}
