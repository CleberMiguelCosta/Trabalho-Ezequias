package com.biblioteca.acervo.integracao;

import java.util.Optional;

public interface FonteLivros {

    String nome();

    PaginaObras buscarPorTitulo(String consulta, int pagina, int limite);

    Optional<ObraExterna> buscarPorIsbn(String isbn);
}
