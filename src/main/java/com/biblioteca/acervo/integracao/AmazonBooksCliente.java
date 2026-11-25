package com.biblioteca.acervo.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component("amazonBooksCliente")
public class AmazonBooksCliente implements FonteLivros {

    private static final int MAX_POR_PAGINA = 10;
    private static final String SERVICO = "ProductAdvertisingAPI";
    private static final String ALVO_BUSCA = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems";

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String accessKey;
    private final String secretKey;
    private final String partnerTag;
    private final String host;
    private final String region;
    private final String marketplace;
    private final boolean habilitado;

    public AmazonBooksCliente(
            ObjectMapper mapper,
            @Value("${amazon.paapi.access-key:}") String accessKey,
            @Value("${amazon.paapi.secret-key:}") String secretKey,
            @Value("${amazon.paapi.partner-tag:}") String partnerTag,
            @Value("${amazon.paapi.host:webservices.amazon.com.br}") String host,
            @Value("${amazon.paapi.region:us-east-1}") String region,
            @Value("${amazon.paapi.marketplace:www.amazon.com.br}") String marketplace) {
        this.mapper = mapper;
        this.accessKey = limpar(accessKey);
        this.secretKey = limpar(secretKey);
        this.partnerTag = limpar(partnerTag);
        this.host = limpar(host) == null ? "webservices.amazon.com.br" : limpar(host);
        this.region = limpar(region) == null ? "us-east-1" : limpar(region);
        this.marketplace = limpar(marketplace) == null ? "www.amazon.com.br" : limpar(marketplace);
        this.habilitado = this.accessKey != null && this.secretKey != null && this.partnerTag != null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean habilitado() {
        return habilitado;
    }

    @Override
    public String nome() {
        return "Amazon";
    }

    @Override
    public PaginaObras buscarPorTitulo(String consulta, int pagina, int limite) {
        if (!habilitado) {
            return PaginaObras.vazia();
        }
        int paginaSegura = Math.max(1, Math.min(10, pagina));
        int limiteSeguro = Math.min(MAX_POR_PAGINA, Math.max(1, limite));
        try {
            String corpo = montarBusca(consulta, paginaSegura, limiteSeguro);
            String resposta = postar("/paapi5/searchitems", ALVO_BUSCA, corpo);
            return interpretarPagina(resposta, limiteSeguro);
        } catch (FonteIndisponivelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FonteIndisponivelException(nome(), ex);
        }
    }

    @Override
    public Optional<ObraExterna> buscarPorIsbn(String isbn) {
        if (!habilitado) {
            return Optional.empty();
        }
        String normalizado = Textos.isbn(isbn);
        if (normalizado == null) {
            return Optional.empty();
        }
        try {
            String corpo = montarBusca(normalizado, 1, 3);
            String resposta = postar("/paapi5/searchitems", ALVO_BUSCA, corpo);
            return interpretarPagina(resposta, 3).obras().stream().findFirst();
        } catch (FonteIndisponivelException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FonteIndisponivelException(nome(), ex);
        }
    }

    public PaginaObras interpretarPagina(String json, int limite) throws IOException {
        if (json == null || json.isBlank()) {
            return PaginaObras.vazia();
        }
        JsonNode raiz = mapper.readTree(json);
        if (raiz.has("Errors") && raiz.path("SearchResult").isMissingNode()) {
            return PaginaObras.vazia();
        }
        JsonNode resultado = raiz.path("SearchResult");
        int total = Math.max(0, resultado.path("TotalResultCount").asInt(0));
        JsonNode items = resultado.path("Items");
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
        boolean temMais = items.size() >= limite && (total <= 0 || obras.size() < total);
        return new PaginaObras(obras, total, temMais);
    }

    public List<ObraExterna> interpretarBusca(String json) throws IOException {
        return interpretarPagina(json, MAX_POR_PAGINA).obras();
    }

    private ObraExterna mapearItem(JsonNode item) {
        JsonNode info = item.path("ItemInfo");
        String titulo = Textos.limpar(info.path("Title").path("DisplayValue").asText(null));
        if (titulo == null) {
            return null;
        }
        List<String> capas = capasDe(item.path("Images"));
        String isbn = isbnDe(info.path("ExternalIds"));
        Integer paginas = null;
        JsonNode pages = info.path("ContentInfo").path("PagesCount").path("DisplayValue");
        if (pages.canConvertToInt()) {
            paginas = Textos.paginas(pages);
        }
        Integer ano = Textos.ano(info.path("ContentInfo").path("PublicationDate").path("DisplayValue").asText(null));
        String edicao = Textos.limpar(info.path("ContentInfo").path("Edition").path("DisplayValue").asText(null));
        String editora = Textos.limpar(info.path("ByLineInfo").path("Brand").path("DisplayValue").asText(null));
        if (editora == null) {
            editora = Textos.limpar(info.path("ByLineInfo").path("Manufacturer").path("DisplayValue").asText(null));
        }
        String autor = autoresDe(info.path("ByLineInfo").path("Contributors"));
        String descricao = Textos.juntar(info.path("Features").path("DisplayValues"));
        if (descricao != null) {
            descricao = Textos.cortar(descricao, 2000);
        }
        Integer popularidade = item.path("CustomerReviews").path("Count").canConvertToInt()
                ? item.path("CustomerReviews").path("Count").asInt()
                : null;
        return new ObraExterna(
                titulo,
                autor,
                isbn,
                editora,
                ano,
                edicao,
                paginas,
                descricao,
                null,
                capas.isEmpty() ? null : capas.get(0),
                capas,
                popularidade,
                nome()
        );
    }

    private List<String> capasDe(JsonNode images) {
        LinkedHashSet<String> capas = new LinkedHashSet<>();
        adicionarCapa(capas, images.path("Primary").path("Large").path("URL").asText(null));
        adicionarCapa(capas, images.path("Primary").path("Medium").path("URL").asText(null));
        JsonNode variants = images.path("Variants");
        if (variants.isArray()) {
            for (JsonNode variant : variants) {
                adicionarCapa(capas, variant.path("Large").path("URL").asText(null));
                adicionarCapa(capas, variant.path("Medium").path("URL").asText(null));
            }
        }
        return List.copyOf(capas);
    }

    private void adicionarCapa(LinkedHashSet<String> capas, String url) {
        String limpa = Textos.limpar(url);
        if (limpa != null) {
            capas.add(limpa);
        }
    }

    private String isbnDe(JsonNode externalIds) {
        JsonNode isbns = externalIds.path("ISBNs").path("DisplayValues");
        if (isbns.isArray()) {
            String escolhido = Textos.escolherIsbn(isbns);
            if (escolhido != null) {
                return escolhido;
            }
        }
        JsonNode eans = externalIds.path("EANs").path("DisplayValues");
        if (eans.isArray()) {
            return Textos.escolherIsbn(eans);
        }
        return null;
    }

    private String autoresDe(JsonNode contributors) {
        if (!contributors.isArray() || contributors.isEmpty()) {
            return null;
        }
        List<String> nomes = new ArrayList<>();
        for (JsonNode contributor : contributors) {
            String papel = contributor.path("Role").asText("").toLowerCase(Locale.ROOT);
            if (!papel.isBlank() && !(papel.contains("author") || papel.contains("autor") || papel.contains("writer"))) {
                continue;
            }
            String nome = Textos.limpar(contributor.path("Name").asText(null));
            if (nome != null) {
                nomes.add(nome);
            }
        }
        if (nomes.isEmpty()) {
            String primeiro = Textos.limpar(contributors.get(0).path("Name").asText(null));
            return primeiro;
        }
        return String.join(", ", nomes);
    }

    private String montarBusca(String palavras, int pagina, int limite) throws IOException {
        ObjectNode raiz = mapper.createObjectNode();
        raiz.put("PartnerTag", partnerTag);
        raiz.put("PartnerType", "Associates");
        raiz.put("Marketplace", marketplace);
        raiz.put("Keywords", palavras);
        raiz.put("SearchIndex", "Books");
        raiz.put("ItemCount", limite);
        raiz.put("ItemPage", pagina);
        ArrayNode resources = raiz.putArray("Resources");
        resources.add("Images.Primary.Large");
        resources.add("Images.Primary.Medium");
        resources.add("Images.Variants.Large");
        resources.add("ItemInfo.Title");
        resources.add("ItemInfo.ByLineInfo");
        resources.add("ItemInfo.ContentInfo");
        resources.add("ItemInfo.ExternalIds");
        resources.add("ItemInfo.Features");
        resources.add("ItemInfo.Classifications");
        return mapper.writeValueAsString(raiz);
    }

    private String postar(String caminho, String alvo, String corpo) throws IOException, InterruptedException {
        Map<String, String> cabecalhos = AmazonPaapiAssinatura.assinar(
                "POST",
                host,
                caminho,
                alvo,
                corpo,
                accessKey,
                secretKey,
                region,
                SERVICO,
                Instant.now());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://" + host + caminho))
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
        for (Map.Entry<String, String> cabecalho : cabecalhos.entrySet()) {
            builder.header(cabecalho.getKey(), cabecalho.getValue());
        }
        HttpResponse<String> resposta = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resposta.statusCode() >= 400) {
            throw new FonteIndisponivelException(nome());
        }
        return resposta.body();
    }

    private static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
