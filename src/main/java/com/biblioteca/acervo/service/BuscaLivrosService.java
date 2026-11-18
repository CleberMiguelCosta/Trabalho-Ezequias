package com.biblioteca.acervo.service;

import com.biblioteca.acervo.dto.LivroBuscaDTO;
import com.biblioteca.acervo.dto.PaginaBuscaDTO;
import com.biblioteca.acervo.exception.ConsultaExternaException;
import com.biblioteca.acervo.exception.DadosInvalidosException;
import com.biblioteca.acervo.integracao.FonteIndisponivelException;
import com.biblioteca.acervo.integracao.FonteLivros;
import com.biblioteca.acervo.integracao.ObraExterna;
import com.biblioteca.acervo.integracao.PaginaObras;
import com.biblioteca.acervo.integracao.Textos;
import com.biblioteca.acervo.model.Livro;
import com.biblioteca.acervo.repository.LivroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class BuscaLivrosService {

    private static final Logger log = LoggerFactory.getLogger(BuscaLivrosService.class);
    private static final String FALHA_CONSULTA =
            "Não foi possível consultar as bases de livros agora. Tente novamente em instantes.";
    private static final int LIMITE_PADRAO = 12;
    private static final int LIMITE_MAXIMO = 40;
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    private final FonteLivros googleBooks;
    private final FonteLivros openLibrary;
    private final LivroRepository livroRepository;
    private final ConcurrentHashMap<String, EntradaCache> cache = new ConcurrentHashMap<>();
    private final ExecutorService consultas = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "busca-livros");
        thread.setDaemon(true);
        return thread;
    });

    public BuscaLivrosService(
            @Qualifier("googleBooksCliente") FonteLivros googleBooks,
            @Qualifier("openLibraryCliente") FonteLivros openLibrary,
            LivroRepository livroRepository) {
        this.googleBooks = googleBooks;
        this.openLibrary = openLibrary;
        this.livroRepository = livroRepository;
    }

    public PaginaBuscaDTO buscar(String titulo, Integer pagina, Integer limite) {
        String consulta = normalizarConsulta(titulo);
        int paginaSegura = pagina == null || pagina < 1 ? 1 : pagina;
        int limiteSeguro = limite == null ? LIMITE_PADRAO : Math.min(LIMITE_MAXIMO, Math.max(1, limite));
        String chave = Textos.normalizarBusca(consulta) + "|" + paginaSegura + "|" + limiteSeguro;

        EntradaCache cached = cache.get(chave);
        if (cached != null && !cached.expirada()) {
            return cached.pagina();
        }

        PaginaBuscaDTO paginaBusca = consultarPagina(consulta, paginaSegura, limiteSeguro);
        cache.put(chave, new EntradaCache(paginaBusca, System.currentTimeMillis() + CACHE_TTL_MS));
        limparCacheExpirado();
        return paginaBusca;
    }

    public LivroBuscaDTO complementar(String isbn) {
        String normalizado = Textos.isbn(isbn);
        if (normalizado == null) {
            throw new DadosInvalidosException("Informe um ISBN válido para complementar a ficha.");
        }
        ObraExterna atual = null;
        try {
            atual = openLibrary.buscarPorIsbn(normalizado).orElse(null);
        } catch (FonteIndisponivelException ex) {
            log.warn("Open Library indisponível no complemento do ISBN {}", normalizado);
        }
        try {
            Optional<ObraExterna> google = googleBooks.buscarPorIsbn(normalizado);
            if (google.isPresent()) {
                atual = atual == null ? google.get() : atual.completarCom(google.get()).comCapas(google.get().capas());
            }
        } catch (FonteIndisponivelException ex) {
            log.warn("Google Books indisponível no complemento do ISBN {}", normalizado);
        }
        if (atual == null || atual.titulo() == null || atual.titulo().isBlank()) {
            throw new DadosInvalidosException("Não foi possível complementar os dados desse ISBN.");
        }
        return toDto(atual);
    }

    private PaginaBuscaDTO consultarPagina(String consulta, int pagina, int limite) {
        CompletableFuture<Consulta> googleFuture = CompletableFuture.supplyAsync(
                () -> consultar(() -> googleBooks.buscarPorTitulo(consulta, pagina, limite)), consultas);
        CompletableFuture<Consulta> openFuture = CompletableFuture.supplyAsync(
                () -> consultar(() -> openLibrary.buscarPorTitulo(consulta, pagina, limite)), consultas);

        Consulta google = aguardar(googleFuture);
        Consulta open = aguardar(openFuture);

        if (google.falhou() && open.falhou()) {
            throw new ConsultaExternaException(FALHA_CONSULTA);
        }

        List<ObraExterna> mescladas = new ArrayList<>();
        if (!open.falhou()) {
            mescladas.addAll(open.pagina().obras());
        }
        if (!google.falhou()) {
            mescladas.addAll(google.pagina().obras());
        }

        List<ObraExterna> obras = ordenarPorRelevancia(agruparEdicoes(mescladas), consulta);
        List<LivroBuscaDTO> itens = obras.stream().map(this::toDto).toList();

        int total = Math.max(
                google.falhou() ? 0 : google.pagina().totalEstimado(),
                open.falhou() ? 0 : open.pagina().totalEstimado());
        boolean temMais = (!google.falhou() && google.pagina().temMais())
                || (!open.falhou() && open.pagina().temMais());

        return new PaginaBuscaDTO(itens, pagina, limite, temMais, total > 0 ? total : null);
    }

    private Consulta aguardar(CompletableFuture<Consulta> future) {
        try {
            return future.orTimeout(9, TimeUnit.SECONDS).join();
        } catch (CompletionException ex) {
            log.warn("Consulta externa excedeu o tempo limite: {}", ex.getMessage());
            return new Consulta(PaginaObras.vazia(), true);
        }
    }

    private String normalizarConsulta(String titulo) {
        String consulta = titulo == null
                ? ""
                : titulo.replace("{", " ").replace("}", " ").trim().replaceAll("\\s+", " ");
        if (consulta.length() < 2) {
            throw new DadosInvalidosException("Informe ao menos 2 caracteres do título.");
        }
        if (consulta.length() > 150) {
            throw new DadosInvalidosException("O título da busca deve ter no máximo 150 caracteres.");
        }
        return consulta;
    }

    private List<ObraExterna> agruparEdicoes(List<ObraExterna> obras) {
        Map<String, ObraExterna> porChave = new LinkedHashMap<>();
        for (ObraExterna obra : obras) {
            if (obra == null || obra.titulo() == null || obra.titulo().isBlank()) {
                continue;
            }
            String chave = chaveEdicao(obra);
            porChave.merge(chave, obra, this::maisCompleta);
        }
        return new ArrayList<>(porChave.values());
    }

    private String chaveEdicao(ObraExterna obra) {
        if (obra.isbn() != null && !obra.isbn().isBlank()) {
            return "isbn:" + obra.isbn();
        }
        return "obra:"
                + Textos.normalizarBusca(obra.titulo()) + "|"
                + Textos.normalizarBusca(obra.autor()) + "|"
                + (obra.anoPublicacao() == null ? "" : obra.anoPublicacao()) + "|"
                + Textos.normalizarBusca(obra.editora());
    }

    private ObraExterna maisCompleta(ObraExterna atual, ObraExterna nova) {
        if (nova.pontuacao() > atual.pontuacao()) {
            return nova.completarCom(atual);
        }
        return atual.completarCom(nova);
    }

    private List<ObraExterna> ordenarPorRelevancia(List<ObraExterna> obras, String consulta) {
        String termo = Textos.normalizarBusca(consulta);
        return obras.stream()
                .sorted(Comparator
                        .comparingInt((ObraExterna obra) -> scoreRelevancia(obra, termo)).reversed()
                        .thenComparing(obra -> obra.titulo() == null ? "" : obra.titulo(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int scoreRelevancia(ObraExterna obra, String termo) {
        String titulo = Textos.normalizarBusca(obra.titulo());
        String autor = Textos.normalizarBusca(obra.autor());
        int score = 0;

        if (titulo.equals(termo)) {
            score += 1200;
        } else if (titulo.startsWith(termo)) {
            score += 800;
        } else if (titulo.contains(termo)) {
            score += 450;
        }

        String[] partes = termo.split(" ");
        int partesNoTitulo = 0;
        for (String parte : partes) {
            if (parte.length() < 2) {
                continue;
            }
            if (titulo.contains(parte)) {
                partesNoTitulo++;
                score += 50;
            }
            if (autor.contains(parte)) {
                score += 15;
            }
        }
        if (partes.length > 1 && partesNoTitulo == partes.length) {
            score += 200;
        }

        // Penaliza títulos longos pouco relacionados (ensaios/críticas sobre a obra).
        if (titulo.length() > termo.length() * 3 && !titulo.startsWith(termo)) {
            score -= 120;
        }

        if (obra.capaUrl() != null) {
            score += 40;
        }
        if (obra.isbn() != null) {
            score += 25;
        }
        if (obra.descricao() != null && obra.descricao().length() >= 80) {
            score += 30;
        }
        if (obra.popularidade() != null) {
            score += Math.min(300, obra.popularidade());
        }
        return score;
    }

    private LivroBuscaDTO toDto(ObraExterna obra) {
        ObraExterna ajustada = Textos.ajustar(obra);
        Long idExistente = null;
        boolean cadastrado = false;
        if (ajustada.isbn() != null) {
            Optional<Livro> existente = livroRepository.findByIsbn(ajustada.isbn());
            if (existente.isPresent()) {
                cadastrado = true;
                idExistente = existente.get().getId();
            }
        }
        List<String> capas = new ArrayList<>(new LinkedHashSet<>(
                ajustada.capas() == null ? List.of() : ajustada.capas()));
        if (ajustada.capaUrl() != null && !capas.contains(ajustada.capaUrl())) {
            capas.add(0, ajustada.capaUrl());
        }
        return new LivroBuscaDTO(
                ajustada.titulo(),
                ajustada.autor(),
                ajustada.isbn(),
                ajustada.editora(),
                ajustada.anoPublicacao(),
                ajustada.edicao(),
                ajustada.numeroPaginas(),
                ajustada.descricao(),
                ajustada.genero(),
                ajustada.capaUrl(),
                List.copyOf(capas),
                ajustada.origem(),
                cadastrado,
                idExistente
        );
    }

    private Consulta consultar(java.util.function.Supplier<PaginaObras> chamada) {
        try {
            PaginaObras pagina = chamada.get();
            return new Consulta(pagina == null ? PaginaObras.vazia() : pagina, false);
        } catch (FonteIndisponivelException ex) {
            log.warn("Fonte de livros indisponível: {}", ex.getMessage());
            return new Consulta(PaginaObras.vazia(), true);
        }
    }

    private void limparCacheExpirado() {
        long agora = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expiraEm() < agora);
    }

    private record Consulta(PaginaObras pagina, boolean falhou) {
    }

    private record EntradaCache(PaginaBuscaDTO pagina, long expiraEm) {
        boolean expirada() {
            return System.currentTimeMillis() > expiraEm;
        }
    }
}
