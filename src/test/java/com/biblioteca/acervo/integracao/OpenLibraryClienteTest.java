package com.biblioteca.acervo.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpenLibraryClienteTest {

    private final OpenLibraryCliente cliente = new OpenLibraryCliente(new ObjectMapper());

    @Test
    void interpretaBuscaPorTitulo() throws Exception {
        List<ObraExterna> obras = cliente.interpretarBusca("""
                {
                  "docs": [
                    {
                      "title": "O Cortiço",
                      "author_name": ["Aluísio Azevedo"],
                      "isbn": ["8594318601", "9788535914842"],
                      "publisher": ["Penguin Companhia"],
                      "first_publish_year": 1890,
                      "number_of_pages_median": 280,
                      "subject": ["accessible book", "Fiction"]
                    }
                  ]
                }
                """);

        ObraExterna obra = obras.get(0);
        assertEquals("O Cortiço", obra.titulo());
        assertEquals("Aluísio Azevedo", obra.autor());
        assertEquals("9788535914842", obra.isbn());
        assertEquals("Penguin Companhia", obra.editora());
        assertEquals(1890, obra.anoPublicacao());
        assertEquals(280, obra.numeroPaginas());
        assertEquals("Ficção", obra.genero());
        assertEquals("https://covers.openlibrary.org/b/isbn/9788535914842-L.jpg?default=false", obra.capaUrl());
        assertEquals("Open Library", obra.origem());
    }

    @Test
    void interpretaEdicaoComDescricaoECapa() throws Exception {
        ObraExterna obra = cliente.interpretarEdicao("""
                {
                  "title": "Dom Casmurro",
                  "edition_name": "2ª edição",
                  "number_of_pages": 256,
                  "publishers": ["Companhia das Letras"],
                  "publish_date": "June 1994",
                  "description": { "type": "/type/text", "value": "Sinopse <b>curta</b>." },
                  "covers": [999],
                  "subjects": ["Fiction"],
                  "isbn_13": ["9788535901234"]
                }
                """);

        assertEquals("2ª edição", obra.edicao());
        assertEquals(256, obra.numeroPaginas());
        assertEquals("Companhia das Letras", obra.editora());
        assertEquals(1994, obra.anoPublicacao());
        assertEquals("Sinopse curta.", obra.descricao());
        assertEquals("Ficção", obra.genero());
        assertEquals("9788535901234", obra.isbn());
        assertEquals("https://covers.openlibrary.org/b/id/999-L.jpg?default=false", obra.capaUrl());
        assertNull(obra.autor());
    }
}
