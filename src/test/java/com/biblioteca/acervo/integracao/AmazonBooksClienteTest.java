package com.biblioteca.acervo.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonBooksClienteTest {

    private final AmazonBooksCliente cliente = new AmazonBooksCliente(
            new ObjectMapper(), "", "", "", "webservices.amazon.com.br", "us-east-1", "www.amazon.com.br");

    @Test
    void desabilitadoSemCredenciais() {
        assertFalse(cliente.habilitado());
        assertTrue(cliente.buscarPorTitulo("Dom Casmurro", 1, 10).obras().isEmpty());
        assertTrue(cliente.buscarPorIsbn("9788535914842").isEmpty());
    }

    @Test
    void interpretaItemComCapaIsbnEAutor() throws Exception {
        List<ObraExterna> obras = cliente.interpretarBusca("""
                {
                  "SearchResult": {
                    "TotalResultCount": 1,
                    "Items": [
                      {
                        "Images": {
                          "Primary": {
                            "Large": { "URL": "https://m.media-amazon.com/images/I/capa-L.jpg" },
                            "Medium": { "URL": "https://m.media-amazon.com/images/I/capa-M.jpg" }
                          }
                        },
                        "ItemInfo": {
                          "Title": { "DisplayValue": "Dom Casmurro" },
                          "ByLineInfo": {
                            "Contributors": [
                              { "Name": "Machado de Assis", "Role": "Author" }
                            ],
                            "Brand": { "DisplayValue": "Companhia das Letras" }
                          },
                          "ContentInfo": {
                            "PagesCount": { "DisplayValue": 256 },
                            "PublicationDate": { "DisplayValue": "1899-01-01" },
                            "Edition": { "DisplayValue": "2ª edição" }
                          },
                          "ExternalIds": {
                            "ISBNs": { "DisplayValues": ["9788535914842"] }
                          },
                          "Features": {
                            "DisplayValues": ["Sinopse curta da obra."]
                          }
                        }
                      }
                    ]
                  }
                }
                """);

        assertEquals(1, obras.size());
        ObraExterna obra = obras.get(0);
        assertEquals("Dom Casmurro", obra.titulo());
        assertEquals("Machado de Assis", obra.autor());
        assertEquals("9788535914842", obra.isbn());
        assertEquals("Companhia das Letras", obra.editora());
        assertEquals(1899, obra.anoPublicacao());
        assertEquals("2ª edição", obra.edicao());
        assertEquals(256, obra.numeroPaginas());
        assertEquals("Sinopse curta da obra.", obra.descricao());
        assertEquals("Amazon", obra.origem());
        assertEquals("https://m.media-amazon.com/images/I/capa-L.jpg", obra.capaUrl());
        assertTrue(obra.capas().contains("https://m.media-amazon.com/images/I/capa-M.jpg"));
    }
}
