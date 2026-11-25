package com.biblioteca.acervo.integracao;

import com.biblioteca.acervo.dto.AutorWikiDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikipediaAutorClienteTest {

    private final WikipediaAutorCliente cliente = new WikipediaAutorCliente(new ObjectMapper());

    @Test
    void interpretaResumoPadrao() throws Exception {
        String json = """
                {
                  "type": "standard",
                  "title": "Machado de Assis",
                  "description": "escritor brasileiro",
                  "extract": "Joaquim Maria Machado de Assis foi um escritor brasileiro.",
                  "thumbnail": { "source": "https://exemplo.test/machado.jpg" },
                  "content_urls": { "desktop": { "page": "https://pt.wikipedia.org/wiki/Machado_de_Assis" } }
                }
                """;

        AutorWikiDTO bio = invocarResumo(json, "Machado_de_Assis");
        assertTrue(bio.encontrado());
        assertEquals("Machado de Assis", bio.titulo());
        assertEquals("escritor brasileiro", bio.descricao());
        assertTrue(bio.extrato().contains("Machado de Assis"));
        assertEquals("https://exemplo.test/machado.jpg", bio.imagemUrl());
        assertEquals("https://pt.wikipedia.org/wiki/Machado_de_Assis", bio.paginaUrl());
    }

    @Test
    void ignoraPaginaDeDesambiguacao() throws Exception {
        String json = """
                {
                  "type": "disambiguation",
                  "title": "Assis",
                  "extract": "Assis pode referir-se a:"
                }
                """;
        AutorWikiDTO bio = invocarResumo(json, "Assis");
        assertFalse(bio.encontrado());
    }

    private AutorWikiDTO invocarResumo(String json, String titulo) throws Exception {
        Method metodo = WikipediaAutorCliente.class.getDeclaredMethod("interpretarResumo", String.class, String.class);
        metodo.setAccessible(true);
        return (AutorWikiDTO) metodo.invoke(cliente, json, titulo);
    }
}
