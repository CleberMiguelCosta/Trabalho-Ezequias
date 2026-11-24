package com.biblioteca.acervo.dto;

public record LivroResponseDTO(
        Long id,
        String titulo,
        String autor,
        String isbn,
        Integer anoPublicacao,
        String genero,
        String editora,
        Integer quantidade,
        String edicao,
        Integer numeroPaginas,
        String descricao,
        boolean temCapa,
        String statusLeitura
) {
}
