package com.biblioteca.acervo.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleBooksClienteTest {

    private final GoogleBooksCliente cliente = new GoogleBooksCliente(new ObjectMapper());

    @Test
    void interpretaVolumeComCapaIsbnEGenero() throws Exception {
        List<ObraExterna> obras = cliente.interpretarBusca("""
                {
                  "items": [
                    {
                      "id": "abc123",
                      "volumeInfo": {
                        "title": "Dom Casmurro",
                        "subtitle": "romance",
                        "authors": ["Machado de Assis", "Outro"],
                        "publisher": "Companhia das Letras",
                        "publishedDate": "1899-01-01",
                        "description": "<p>Um olhar sobre o ciúme.</p>",
                        "pageCount": 256,
                        "categories": ["Fiction / Romance"],
                        "imageLinks": { "thumbnail": "http://books.google.com/pequena.jpg" },
                        "industryIdentifiers": [
                          { "type": "ISBN_10", "identifier": "853591234X" },
                          { "type": "ISBN_13", "identifier": "978-85-359-1234-2" }
                        ]
                      }
                    }
                  ]
                }
                """);

        assertEquals(1, obras.size());
        ObraExterna obra = obras.get(0);
        assertEquals("Dom Casmurro: romance", obra.titulo());
        assertEquals("Machado de Assis, Outro", obra.autor());
        assertEquals("9788535912342", obra.isbn());
        assertEquals("Companhia das Letras", obra.editora());
        assertEquals(1899, obra.anoPublicacao());
        assertEquals(256, obra.numeroPaginas());
        assertEquals("Um olhar sobre o ciúme.", obra.descricao());
        assertEquals("Romance", obra.genero());
        assertEquals("Google Books", obra.origem());
        assertTrue(obra.capaUrl().contains("id=abc123"));
        assertTrue(obra.capaUrl().contains("zoom=3"));
        assertNull(obra.edicao());
    }
}
