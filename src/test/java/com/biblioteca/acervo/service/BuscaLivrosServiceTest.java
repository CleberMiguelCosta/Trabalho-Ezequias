package com.biblioteca.acervo.service;

import com.biblioteca.acervo.dto.LivroBuscaDTO;
import com.biblioteca.acervo.dto.PaginaBuscaDTO;
import com.biblioteca.acervo.exception.ConsultaExternaException;
import com.biblioteca.acervo.exception.DadosInvalidosException;
import com.biblioteca.acervo.integracao.FonteIndisponivelException;
import com.biblioteca.acervo.integracao.FonteLivros;
import com.biblioteca.acervo.integracao.ObraExterna;
import com.biblioteca.acervo.integracao.PaginaObras;
import com.biblioteca.acervo.model.Livro;
import com.biblioteca.acervo.repository.LivroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscaLivrosServiceTest {

    @Mock
    private LivroRepository livroRepository;

    private FonteFalsa google;
    private FonteFalsa openLibrary;
    private BuscaLivrosService service;

    @BeforeEach
    void preparar() {
        google = new FonteFalsa("Google Books");
        openLibrary = new FonteFalsa("Open Library");
        service = new BuscaLivrosService(google, openLibrary, null, livroRepository);
    }

    @Test
    void priorizaTituloExatoEPopularidade() {
        openLibrary.porTitulo = List.of(
                comPopularidade(obra("Guia de Dom Casmurro", "9781111111111", "1. ed."), 2),
                comPopularidade(obra("Dom Casmurro", "9788535914842", "1. ed."), 80),
                comPopularidade(obra("Dom Casmurro: estudo", "9782222222222", "1. ed."), 10)
        );

        PaginaBuscaDTO pagina = service.buscar("Dom Casmurro", 1, 12);

        assertEquals("Dom Casmurro", pagina.itens().get(0).titulo());
        assertEquals("9788535914842", pagina.itens().get(0).isbn());
    }

    @Test
    void complementaSinopseECapaSobDemanda() {
        openLibrary.porIsbn.put("9788535914842", ObraExterna.de(
                "Dom Casmurro", "Machado de Assis", "9788535914842", "Companhia das Letras",
                1899, "2ª edição", 256, "Sinopse longa o suficiente para o cadastro da obra.",
                "Romance", "https://covers.openlibrary.org/b/id/1-L.jpg?default=false", "Open Library"));

        LivroBuscaDTO resultado = service.complementar("978-85-359-1484-2");

        assertEquals("2ª edição", resultado.edicao());
        assertEquals("Sinopse longa o suficiente para o cadastro da obra.", resultado.descricao());
        assertFalse(resultado.capas().isEmpty());
    }

    @Test
    void usaOpenLibraryQuandoGoogleNaoEncontra() {
        ObraExterna encontrada = obra("O Cortiço", "9788535914842", "1. ed.");
        openLibrary.porTitulo = List.of(ObraExterna.de(
                encontrada.titulo(),
                encontrada.autor(),
                encontrada.isbn(),
                encontrada.editora(),
                encontrada.anoPublicacao(),
                encontrada.edicao(),
                encontrada.numeroPaginas(),
                encontrada.descricao(),
                encontrada.genero(),
                encontrada.capaUrl(),
                "Open Library"));

        PaginaBuscaDTO pagina = service.buscar("O Cortiço", 1, 12);

        assertEquals(1, pagina.itens().size());
        assertEquals("Open Library", pagina.itens().get(0).origem());
        assertEquals("1. ed.", pagina.itens().get(0).edicao());
    }

    @Test
    void marcaLivroJaCadastrado() {
        google.porTitulo = List.of(obra("Dom Casmurro", "9788535914842", "1. ed."));
        Livro existente = new Livro();
        existente.setId(7L);
        when(livroRepository.findByIsbn("9788535914842")).thenReturn(Optional.of(existente));

        LivroBuscaDTO resultado = service.buscar("Dom Casmurro", 1, 12).itens().get(0);

        assertTrue(resultado.jaCadastrado());
        assertEquals(7L, resultado.idExistente());
    }

    @Test
    void retornaListaVaziaQuandoNenhumaBaseTemOTitulo() {
        assertTrue(service.buscar("Titulo inexistente", 1, 12).itens().isEmpty());
    }

    @Test
    void retornaVazioQuandoGoogleFalhaEOpenLibraryNaoEncontra() {
        google.falhar = true;
        assertTrue(service.buscar("Titulo inexistente", 1, 12).itens().isEmpty());
    }

    @Test
    void informaFalhaDeConexaoQuandoAsDuasBasesFalham() {
        google.falhar = true;
        openLibrary.falhar = true;

        ConsultaExternaException erro = assertThrows(ConsultaExternaException.class,
                () -> service.buscar("Dom Casmurro", 1, 12));

        assertEquals(
                "Não foi possível consultar as bases de livros agora. Tente novamente em instantes.",
                erro.getMessage());
    }

    @Test
    void removeRepeticaoNoNomeDoAutor() {
        google.porTitulo = List.of(new ObraExterna(
                "Dom Casmurro",
                "Machado de Machado de Assis",
                "9788535914842",
                "Companhia das Letras",
                1899,
                "1. ed.",
                256,
                "Sinopse",
                "Romance",
                "https://books.google.com/books/content?id=abc&zoom=3",
                List.of("https://books.google.com/books/content?id=abc&zoom=3"),
                10,
                "Google Books"));

        assertEquals("Machado de Assis", service.buscar("Dom Casmurro", 1, 12).itens().get(0).autor());
    }

    @Test
    void paginaRealConsultaProximaFaixaNasFontes() {
        google.porTitulo = List.of(obra("Dom Casmurro", "9788535914842", "1. ed."));
        google.temMais = true;
        google.total = 40;

        PaginaBuscaDTO pagina = service.buscar("Dom Casmurro", 2, 12);

        assertEquals(2, google.ultimaPagina);
        assertEquals(12, google.ultimoLimite);
        assertEquals(2, pagina.pagina());
        assertTrue(pagina.temMais());
        assertEquals(40, pagina.totalEstimado());
    }

    @Test
    void recusaTituloCurtoDemais() {
        assertThrows(DadosInvalidosException.class, () -> service.buscar(" a ", 1, 12));
    }

    private ObraExterna obra(String titulo, String isbn, String edicao) {
        return ObraExterna.de(
                titulo,
                "Machado de Assis",
                isbn,
                "Companhia das Letras",
                1899,
                edicao,
                256,
                "Sinopse",
                "Romance",
                "https://books.google.com/books/content?id=abc&zoom=3",
                "Google Books"
        );
    }

    private ObraExterna comPopularidade(ObraExterna obra, int popularidade) {
        return new ObraExterna(
                obra.titulo(), obra.autor(), obra.isbn(), obra.editora(), obra.anoPublicacao(),
                obra.edicao(), obra.numeroPaginas(), obra.descricao(), obra.genero(),
                obra.capaUrl(), obra.capas(), popularidade, obra.origem());
    }

    private static final class FonteFalsa implements FonteLivros {

        private final String nome;
        private List<ObraExterna> porTitulo = List.of();
        private final Map<String, ObraExterna> porIsbn = new HashMap<>();
        private boolean falhar;
        private boolean temMais;
        private int total;
        private int ultimaPagina;
        private int ultimoLimite;

        private FonteFalsa(String nome) {
            this.nome = nome;
        }

        @Override
        public String nome() {
            return nome;
        }

        @Override
        public PaginaObras buscarPorTitulo(String consulta, int pagina, int limite) {
            if (falhar) {
                throw new FonteIndisponivelException(nome);
            }
            ultimaPagina = pagina;
            ultimoLimite = limite;
            return new PaginaObras(porTitulo, total, temMais);
        }

        @Override
        public Optional<ObraExterna> buscarPorIsbn(String isbn) {
            if (falhar) {
                throw new FonteIndisponivelException(nome);
            }
            return Optional.ofNullable(porIsbn.get(isbn));
        }
    }
}
