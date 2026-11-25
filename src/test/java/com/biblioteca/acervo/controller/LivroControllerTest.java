package com.biblioteca.acervo.controller;

import com.biblioteca.acervo.dto.LivroBuscaDTO;
import com.biblioteca.acervo.dto.PaginaBuscaDTO;
import com.biblioteca.acervo.exception.ConsultaExternaException;
import com.biblioteca.acervo.repository.LivroRepository;
import com.biblioteca.acervo.service.BuscaLivrosService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:biblioteca;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class LivroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LivroRepository livroRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BuscaLivrosService buscaLivrosService;

    @BeforeEach
    void limpar() {
        livroRepository.deleteAll();
    }

    @Test
    void deveExecutarCicloCompletoDeCrud() throws Exception {
        String respostaCriacao = mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("Dom Casmurro", "978-12-345-6789-0", 2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/livros/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.titulo").value("Dom Casmurro"))
                .andExpect(jsonPath("$.autor").value("Machado de Assis"))
                .andExpect(jsonPath("$.isbn").value("9781234567890"))
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.temCapa").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(respostaCriacao).get("id").asLong();

        mockMvc.perform(get("/api/livros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/api/livros/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genero").value("Romance"));

        mockMvc.perform(put("/api/livros/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("Dom Casmurro (edição revista)", "9781234567890", 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.titulo").value("Dom Casmurro (edição revista)"))
                .andExpect(jsonPath("$.quantidade").value(4));

        mockMvc.perform(delete("/api/livros/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/livros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/livros/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Livro não encontrado para o id " + id + "."));
    }

    @Test
    void deveListarVazioERecusarIdentificadorInexistente() throws Exception {
        mockMvc.perform(get("/api/livros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/livros/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(delete("/api/livros/{id}", 999))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/livros/{id}", 999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("Livro ausente", "9780000000001", 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRejeitarDadosInvalidosEIsbnDuplicado() throws Exception {
        mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": " ",
                                  "autor": "",
                                  "isbn": "123",
                                  "genero": "",
                                  "quantidade": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Verifique os campos informados."))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("titulo")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("quantidade")));

        mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("ISBN curto", "12345", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(
                        "Informe um ISBN com 10 ou 13 dígitos. Hífens são opcionais."));

        mockMvc.perform(get("/api/livros/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O identificador informado é inválido."));

        mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("Primeiro", "9788535914842", 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("Segundo", "978-85-359-1484-2", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(
                        "Já existe um livro cadastrado com o ISBN 9788535914842."));
    }

    @Test
    void devePersistirDetalhesOpcionaisERecusarCapaDeOrigemDesconhecida() throws Exception {
        String resposta = mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "O Cortiço",
                                  "autor": "Aluísio Azevedo",
                                  "isbn": "9788535914842",
                                  "anoPublicacao": 1890,
                                  "genero": "Romance",
                                  "editora": "Penguin",
                                  "quantidade": 2,
                                  "edicao": "1. ed.",
                                  "numeroPaginas": 320,
                                  "descricao": "Retrato do cortiço."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.edicao").value("1. ed."))
                .andExpect(jsonPath("$.numeroPaginas").value(320))
                .andExpect(jsonPath("$.descricao").value("Retrato do cortiço."))
                .andExpect(jsonPath("$.temCapa").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(resposta).get("id").asLong();

        mockMvc.perform(get("/api/livros/{id}/capa", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Este livro não possui capa."));

        mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "Capa externa",
                                  "autor": "Autor",
                                  "isbn": "9780000000002",
                                  "anoPublicacao": 2000,
                                  "genero": "Romance",
                                  "quantidade": 1,
                                  "capaOrigem": "https://example.com/capa.jpg"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A origem da capa não é permitida."));
    }

    @Test
    void deveExporBuscaExternaEFalhaDeConexao() throws Exception {
        when(buscaLivrosService.buscar("Dom Casmurro", null, null)).thenReturn(new PaginaBuscaDTO(
                List.of(new LivroBuscaDTO(
                        "Dom Casmurro",
                        "Machado de Assis",
                        "9788535914842",
                        "Companhia das Letras",
                        1899,
                        "2ª edição",
                        256,
                        "Sinopse",
                        "Romance",
                        "https://books.google.com/books/content?id=abc&zoom=3",
                        List.of("https://books.google.com/books/content?id=abc&zoom=3"),
                        "Google Books e Open Library",
                        true,
                        4L
                )),
                1,
                12,
                true,
                48
        ));

        mockMvc.perform(get("/api/livros/busca").param("titulo", "Dom Casmurro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].titulo").value("Dom Casmurro"))
                .andExpect(jsonPath("$.itens[0].edicao").value("2ª edição"))
                .andExpect(jsonPath("$.itens[0].jaCadastrado").value(true))
                .andExpect(jsonPath("$.itens[0].idExistente").value(4))
                .andExpect(jsonPath("$.temMais").value(true))
                .andExpect(jsonPath("$.pagina").value(1));

        when(buscaLivrosService.buscar("Sumiço", null, null)).thenThrow(new ConsultaExternaException(
                "Não foi possível consultar as bases de livros agora. Tente novamente em instantes."));

        mockMvc.perform(get("/api/livros/busca").param("titulo", "Sumiço"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensagem").value(
                        "Não foi possível consultar as bases de livros agora. Tente novamente em instantes."));
    }

    @Test
    void deveBuscarLivrosJaCatalogadosPorNomeAutorIsbnEEditora() throws Exception {
        mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("Dom Casmurro", "9788535914842", 1)
                                .replace("Companhia das Letras", "Penguin")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/livros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonLivro("O Cortiço", "9788503010201", 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/livros").param("termo", "Penguin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Dom Casmurro"));

        mockMvc.perform(get("/api/livros").param("termo", "Machado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/livros").param("termo", "978-85-030-1020-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("O Cortiço"));
    }

    private String jsonLivro(String titulo, String isbn, int quantidade) {
        return """
                {
                  "titulo": "%s",
                  "autor": "Machado de Assis",
                  "isbn": "%s",
                  "anoPublicacao": 1899,
                  "genero": "Romance",
                  "editora": "Companhia das Letras",
                  "quantidade": %d
                }
                """.formatted(titulo, isbn, quantidade);
    }
}
