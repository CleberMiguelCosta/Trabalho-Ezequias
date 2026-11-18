package com.biblioteca.acervo.dto;

import java.util.List;

public record LivroBuscaDTO(
        String titulo,
        String autor,
        String isbn,
        String editora,
        Integer anoPublicacao,
        String edicao,
        Integer numeroPaginas,
        String descricao,
        String genero,
        String capaUrl,
        List<String> capas,
        String origem,
        boolean jaCadastrado,
        Long idExistente
) {
}
