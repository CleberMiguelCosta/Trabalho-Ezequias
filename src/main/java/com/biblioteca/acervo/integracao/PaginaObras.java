package com.biblioteca.acervo.integracao;

import java.util.List;

public record PaginaObras(List<ObraExterna> obras, int totalEstimado, boolean temMais) {

    public static PaginaObras vazia() {
        return new PaginaObras(List.of(), 0, false);
    }

    public PaginaObras {
        obras = obras == null ? List.of() : List.copyOf(obras);
        if (totalEstimado < 0) {
            totalEstimado = 0;
        }
    }
}
