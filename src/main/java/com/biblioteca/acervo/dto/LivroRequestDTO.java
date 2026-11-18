package com.biblioteca.acervo.dto;

import com.biblioteca.acervo.LimitesLivro;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LivroRequestDTO(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = LimitesLivro.TITULO, message = "O título deve ter no máximo 300 caracteres.")
        String titulo,

        @Size(max = LimitesLivro.AUTOR, message = "O autor deve ter no máximo 300 caracteres.")
        String autor,

        @Size(max = LimitesLivro.ISBN, message = "O ISBN deve ter no máximo 20 caracteres.")
        String isbn,

        @Min(value = 1450, message = "O ano de publicação deve ser a partir de 1450.")
        @Max(value = 2100, message = "O ano de publicação é inválido.")
        Integer anoPublicacao,

        @Size(max = LimitesLivro.GENERO, message = "O gênero deve ter no máximo 80 caracteres.")
        String genero,

        @Size(max = LimitesLivro.EDITORA, message = "A editora deve ter no máximo 180 caracteres.")
        String editora,

        @Min(value = 0, message = "A quantidade de exemplares não pode ser negativa.")
        @Max(value = 10000, message = "A quantidade de exemplares excede o limite permitido.")
        Integer quantidade,

        @Size(max = LimitesLivro.EDICAO, message = "A edição deve ter no máximo 80 caracteres.")
        String edicao,

        @Min(value = 1, message = "O número de páginas deve ser maior que zero.")
        @Max(value = LimitesLivro.PAGINAS_MAX, message = "O número de páginas excede o limite permitido.")
        Integer numeroPaginas,

        @Size(max = LimitesLivro.DESCRICAO, message = "A descrição deve ter no máximo 2000 caracteres.")
        String descricao,

        @Size(max = LimitesLivro.CAPA_ORIGEM, message = "A origem da capa é inválida.")
        String capaOrigem
) {
}
