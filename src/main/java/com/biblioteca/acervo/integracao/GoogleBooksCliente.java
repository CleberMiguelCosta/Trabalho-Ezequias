package com.biblioteca.acervo.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component("googleBooksCliente")
public class GoogleBooksCliente implements FonteLivros {

    private static final int MAX_POR_PAGINA = 40;

    private final RestClient client;
    private final ObjectMapper mapper;

    public GoogleBooksCliente(ObjectMapper mapper) {
        this.mapper = mapper;
        // Timeout curto: a cota anônima costuma falhar rápido (429) e não deve atrasar a Open Library.
        this.client = ClientesHttp.criar("https://www.googleapis.com", 3, 4);
    }

    @Override
    public String nome() {
        return "Google Books";
    }

    @Override
    public PaginaObras buscarPorTitulo(String consulta, int pagina, int limite) {
        int paginaSegura = Math.max(1, pagina);
        int limiteSeguro = Math.min(MAX_POR_PAGINA, Math.max(1, limite));
        int inicio = (paginaSegura - 1) * limiteSeguro;
        // intitle: prioriza o título e ainda aceita trechos parciais.
        String corpo = obter(
                "/books/v1/volumes?q={q}&startIndex={inicio}&maxResults={max}&printType={tipo}&orderBy={ordem}",
                "intitle:" + consulta,
                inicio,
                limiteSeguro,
                "books",
                "relevance");
        try {
            PaginaObras paginaObras = interpretarPagina(corpo, inicio, limiteSeguro);
            if (!paginaObras.obras().isEmpty() || inicio > 0) {
                return paginaObras;
            }
            // Fallback amplo só na primeira página quando intitle não encontra nada.
            String amplo = obter(
                    "/books/v1/volumes?q={q}&startIndex={inicio}&maxResults={max}&printType={tipo}&orderBy={ordem}",
                    consulta,
                    0,
                    limiteSeguro,
                    "books",
                    "relevance");
            return interpretarPagina(amplo, 0, limiteSeguro);
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
        String corpo = obter("/books/v1/volumes?q={q}&maxResults={max}&printType={tipo}",
                "isbn:" + normalizado, 3, "books");
        try {
            return interpretarPagina(corpo, 0, 3).obras().stream().findFirst();
        } catch (IOException ex) {
            throw new FonteIndisponivelException(nome(), ex);
        }
    }

    public PaginaObras interpretarPagina(String json, int inicio, int limite) throws IOException {
        if (json == null || json.isBlank()) {
            return PaginaObras.vazia();
        }
        JsonNode raiz = mapper.readTree(json);
        int total = Math.max(0, raiz.path("totalItems").asInt(0));
        JsonNode items = raiz.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return new PaginaObras(List.of(), total, false);
        }
        List<ObraExterna> obras = new ArrayList<>();
        for (JsonNode item : items) {
            ObraExterna obra = mapearItem(item);
            if (obra != null) {
                obras.add(obra);
            }
        }
        boolean temMais = inicio + obras.size() < total && !obras.isEmpty();
        return new PaginaObras(obras, total, temMais);
    }

    public List<ObraExterna> interpretarBusca(String json) throws IOException {
        return interpretarPagina(json, 0, MAX_POR_PAGINA).obras();
    }

    private ObraExterna mapearItem(JsonNode item) {
        JsonNode info = item.path("volumeInfo");
        String titulo = Textos.limpar(info.path("title").asText(null));
        if (titulo == null) {
            return null;
        }
        String subtitulo = Textos.limpar(info.path("subtitle").asText(null));
        if (subtitulo != null && !titulo.toLowerCase().contains(subtitulo.toLowerCase())) {
            titulo = titulo + ": " + subtitulo;
        }
        String id = Textos.limpar(item.path("id").asText(null));
        List<String> capas = capasDe(id, info.path("imageLinks"));
        String capa = capas.isEmpty() ? null : capas.get(0);
        Integer popularidade = info.path("ratingsCount").canConvertToInt()
                ? info.path("ratingsCount").asInt()
                : null;
        return new ObraExterna(
                titulo,
                Textos.juntar(info.path("authors")),
                isbnDe(info.path("industryIdentifiers")),
                Textos.limpar(info.path("publisher").asText(null)),
                Textos.ano(info.path("publishedDate").asText(null)),
                null,
                Textos.paginas(info.path("pageCount")),
                Textos.limparHtml(info.path("description").asText(null)),
                Generos.deLista(info.path("categories")),
                capa,
                capas,
                popularidade,
                nome()
        );
    }

    private String isbnDe(JsonNode identificadores) {
        if (!identificadores.isArray()) {
            return null;
        }
        String isbn10 = null;
        for (JsonNode identificador : identificadores) {
            String tipo = identificador.path("type").asText("");
            String codigo = Textos.isbn(identificador.path("identifier").asText(null));
            if (codigo == null) {
                continue;
            }
            if ("ISBN_13".equals(tipo) || codigo.length() == 13) {
                return codigo;
            }
            if (isbn10 == null) {
                isbn10 = codigo;
            }
        }
        return isbn10;
    }

    private List<String> capasDe(String id, JsonNode imageLinks) {
        List<String> capas = new ArrayList<>();
        if (id != null && !imageLinks.isMissingNode() && !imageLinks.isEmpty()) {
            String volume = URLEncoder.encode(id, StandardCharsets.UTF_8);
            capas.add("https://books.google.com/books/content?id=" + volume
                    + "&printsec=frontcover&img=1&zoom=3");
            capas.add("https://books.google.com/books/content?id=" + volume
                    + "&printsec=frontcover&img=1&zoom=2");
        }
        return capas;
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
