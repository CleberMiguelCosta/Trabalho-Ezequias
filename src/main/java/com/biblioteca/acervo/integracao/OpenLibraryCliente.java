package com.biblioteca.acervo.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Component("openLibraryCliente")
public class OpenLibraryCliente implements FonteLivros {

    private static final int MAX_POR_PAGINA = 40;
    private static final String CAMPOS = "key,title,subtitle,author_name,isbn,publisher,"
            + "first_publish_year,number_of_pages_median,subject,cover_i,cover_edition_key,"
            + "edition_count,ratings_average,want_to_read_count";

    private final RestClient client;
    private final ObjectMapper mapper;

    public OpenLibraryCliente(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = ClientesHttp.criar("https://openlibrary.org", 4, 8);
    }

    @Override
    public String nome() {
        return "Open Library";
    }

    @Override
    public PaginaObras buscarPorTitulo(String consulta, int pagina, int limite) {
        int paginaSegura = Math.max(1, pagina);
        int limiteSeguro = Math.min(MAX_POR_PAGINA, Math.max(1, limite));
        // q= com curinga no último termo cobre títulos parciais (ex.: "Dom Cas" → Dom Casmurro).
        // sort=editions foi removido: na Open Library ele destrói a relevância da consulta.
        String corpo = obter(
                "/search.json?q={consulta}&page={pagina}&limit={limite}&fields=" + CAMPOS,
                consultaComCuringa(consulta),
                paginaSegura,
                limiteSeguro);
        try {
            return interpretarPagina(corpo, paginaSegura, limiteSeguro);
        } catch (IOException ex) {
            throw new FonteIndisponivelException(nome(), ex);
        }
    }

    @Override
    public Optional<ObraExterna> buscarPorIsbn(String isbn) {
        String normalizado = Textos.isbn(isbn);
        if (normalizado == null) {
            return Optional.empty();
        }
        try {
            String corpo = client.get()
                    .uri("/isbn/{isbn}.json", normalizado)
                    .retrieve()
                    .body(String.class);
            ObraExterna obra = interpretarEdicao(corpo);
            if (obra == null) {
                return Optional.empty();
            }
            return Optional.of(completarDescricaoDaObra(obra, corpo));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new FonteIndisponivelException(nome(), ex);
        } catch (IOException ex) {
            throw new FonteIndisponivelException(nome(), ex);
        }
    }

    public PaginaObras interpretarPagina(String json, int pagina, int limite) throws IOException {
        if (json == null || json.isBlank()) {
            return PaginaObras.vazia();
        }
        JsonNode raiz = mapper.readTree(json);
        int total = Math.max(0, raiz.path("numFound").asInt(0));
        JsonNode docs = raiz.path("docs");
        if (!docs.isArray() || docs.isEmpty()) {
            return new PaginaObras(List.of(), total, false);
        }
        List<ObraExterna> obras = new ArrayList<>();
        for (JsonNode doc : docs) {
            ObraExterna obra = mapearDocumento(doc);
            if (obra != null) {
                obras.add(obra);
            }
        }
        boolean temMais = docs.size() >= limite && (total <= 0 || pagina * limite < total);
        return new PaginaObras(obras, total, temMais);
    }

    public List<ObraExterna> interpretarBusca(String json) throws IOException {
        return interpretarPagina(json, 1, MAX_POR_PAGINA).obras();
    }

    public ObraExterna interpretarEdicao(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode edicao = mapper.readTree(json);
        String isbn = Textos.escolherIsbn(edicao.path("isbn_13"));
        if (isbn == null) {
            isbn = Textos.escolherIsbn(edicao.path("isbn_10"));
        }
        List<String> capas = capasDaEdicao(edicao, isbn);
        return new ObraExterna(
                juntarTitulo(edicao),
                null,
                isbn,
                Textos.primeiroTexto(edicao.path("publishers")),
                Textos.ano(edicao.path("publish_date").asText(null)),
                Textos.limpar(edicao.path("edition_name").asText(null)),
                Textos.paginas(edicao.path("number_of_pages")),
                Textos.descricao(edicao.path("description")),
                Generos.deLista(edicao.path("subjects")),
                capas.isEmpty() ? null : capas.get(0),
                capas,
                null,
                nome()
        );
    }

    private ObraExterna mapearDocumento(JsonNode doc) {
        String titulo = juntarTitulo(doc);
        if (titulo == null) {
            return null;
        }
        String isbn = Textos.escolherIsbn(doc.path("isbn"));
        List<String> capas = capasDoDocumento(doc, isbn);
        Integer popularidade = popularidadeDe(doc);
        return new ObraExterna(
                titulo,
                Textos.juntar(doc.path("author_name")),
                isbn,
                Textos.primeiroTexto(doc.path("publisher")),
                Textos.ano(doc.path("first_publish_year").asText(null)),
                null,
                Textos.paginas(doc.path("number_of_pages_median")),
                null,
                Generos.deLista(doc.path("subject")),
                capas.isEmpty() ? null : capas.get(0),
                capas,
                popularidade,
                nome()
        );
    }

    private String consultaComCuringa(String consulta) {
        String[] partes = consulta.trim().split("\\s+");
        if (partes.length == 0) {
            return consulta;
        }
        StringBuilder montada = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) {
                montada.append(' ');
            }
            montada.append(partes[i]);
            if (i == partes.length - 1 && !partes[i].endsWith("*")) {
                montada.append('*');
            }
        }
        return montada.toString();
    }

    private Integer popularidadeDe(JsonNode doc) {
        int edicoes = doc.path("edition_count").asInt(0);
        int desejados = doc.path("want_to_read_count").asInt(0);
        double nota = doc.path("ratings_average").asDouble(0);
        int score = edicoes + desejados / 10 + (int) (nota * 5);
        return score > 0 ? score : null;
    }

    private String juntarTitulo(JsonNode node) {
        String titulo = Textos.limpar(node.path("title").asText(null));
        if (titulo == null) {
            return null;
        }
        String subtitulo = Textos.limpar(node.path("subtitle").asText(null));
        if (subtitulo != null && !titulo.toLowerCase().contains(subtitulo.toLowerCase())) {
            return titulo + ": " + subtitulo;
        }
        return titulo;
    }

    private ObraExterna completarDescricaoDaObra(ObraExterna obra, String jsonEdicao) {
        if (obra.descricao() != null && !obra.descricao().isBlank()) {
            return obra;
        }
        try {
            JsonNode edicao = mapper.readTree(jsonEdicao);
            JsonNode works = edicao.path("works");
            if (!works.isArray() || works.isEmpty()) {
                return obra;
            }
            String chave = Textos.limpar(works.get(0).path("key").asText(null));
            if (chave == null) {
                return obra;
            }
            String caminho = chave.startsWith("/") ? chave : "/" + chave;
            String corpo = client.get()
                    .uri(caminho + ".json")
                    .retrieve()
                    .body(String.class);
            JsonNode raiz = mapper.readTree(corpo);
            String descricao = Textos.descricao(raiz.path("description"));
            if (descricao == null) {
                return obra;
            }
            return obra.completarCom(ObraExterna.de(
                    obra.titulo(), null, null, null, null, null, null,
                    descricao, Generos.deLista(raiz.path("subjects")), null, nome()));
        } catch (Exception ex) {
            return obra;
        }
    }

    private List<String> capasDoDocumento(JsonNode doc, String isbn) {
        LinkedHashSet<String> capas = new LinkedHashSet<>();
        adicionarCapa(capas, capaPorIndice(doc.path("cover_i")));
        if (isbn != null) {
            adicionarCapa(capas, capaPorIsbn(isbn));
        }
        JsonNode isbns = doc.path("isbn");
        if (isbns.isArray()) {
            int lidos = 0;
            for (JsonNode item : isbns) {
                String codigo = Textos.isbn(item.asText(null));
                if (codigo == null || codigo.equals(isbn)) {
                    continue;
                }
                adicionarCapa(capas, capaPorIsbn(codigo));
                lidos++;
                if (lidos >= 4) {
                    break;
                }
            }
        }
        return List.copyOf(capas);
    }

    private List<String> capasDaEdicao(JsonNode edicao, String isbn) {
        LinkedHashSet<String> capas = new LinkedHashSet<>();
        JsonNode covers = edicao.path("covers");
        if (covers.isArray()) {
            for (JsonNode cover : covers) {
                if (cover.canConvertToInt() && cover.asInt() > 0) {
                    adicionarCapa(capas, "https://covers.openlibrary.org/b/id/" + cover.asInt() + "-L.jpg?default=false");
                }
            }
        }
        adicionarCapa(capas, capaPorIsbn(isbn));
        return List.copyOf(capas);
    }

    private void adicionarCapa(LinkedHashSet<String> capas, String url) {
        if (url != null && !url.isBlank()) {
            capas.add(url.trim());
        }
    }

    private String capaPorIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }
        return "https://covers.openlibrary.org/b/isbn/" + isbn + "-L.jpg?default=false";
    }

    private String capaPorIndice(JsonNode coverI) {
        if (coverI == null || !coverI.canConvertToInt() || coverI.asInt() <= 0) {
            return null;
        }
        return "https://covers.openlibrary.org/b/id/" + coverI.asInt() + "-L.jpg?default=false";
    }

    private String obter(String caminho, Object... valores) {
        try {
            return client.get()
                    .uri(caminho, valores)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (requisicao, resposta) -> {
                        throw new FonteIndisponivelException(nome());
                    })
                    .body(String.class);
        } catch (FonteIndisponivelException ex) {
            throw ex;
        } catch (RestClientException ex) {
            if (ex.getCause() instanceof FonteIndisponivelException causa) {
                throw causa;
            }
            throw new FonteIndisponivelException(nome(), ex);
        }
    }
}
