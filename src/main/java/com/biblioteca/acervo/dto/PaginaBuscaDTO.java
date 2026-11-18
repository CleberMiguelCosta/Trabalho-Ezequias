package com.biblioteca.acervo.dto;

import java.util.List;

public record PaginaBuscaDTO(
        List<LivroBuscaDTO> itens,
        int pagina,
        int limite,
        boolean temMais,
        Integer totalEstimado
) {
}
