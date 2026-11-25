package com.biblioteca.acervo.integracao;

import com.biblioteca.acervo.dto.AutorWikiDTO;
import com.biblioteca.acervo.exception.ConsultaExternaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class WikipediaAutorCliente {

    private static final String FALHA =
            "Não foi possível consultar a Wikipedia agora. Tente novamente em instantes.";

    private final RestClient rest;
    private final RestClient api;
    private final ObjectMapper mapper;

    public WikipediaAutorCliente(ObjectMapper mapper) {
        this.mapper = mapper;
        this.rest = ClientesHttp.criar("https://pt.wikipedia.org", 4, 8);
        this.api = ClientesHttp.criar("https://pt.wikipedia.org/api/rest_v1", 4, 8);
    }

    public AutorWikiDTO buscarBiografia(String nome) {
        String consulta = Textos.limpar(nome);
        if (consulta == null) {
            return naoEncontrado(null);
        }
        try {
            List<String> candidatos = buscarTitulos(consulta);
            if (candidatos.isEmpty()) {
                AutorWikiDTO direto = resumo(consulta.replace(' ', '_'));
                return direto.encontrado() ? direto.comNome(consulta) : naoEncontrado(consulta);
            }
            AutorWikiDTO melhor = null;
            for (String titulo : candidatos) {
                AutorWikiDTO atual = resumo(titulo);
                if (!atual.encontrado()) {
                    continue;
                }
                if (melhor == null || pontuar(atual, consulta) > pontuar(melhor, consulta)) {
                    melhor = atual;
                }
            }
            return melhor == null ? naoEncontrado(consulta) : melhor.comNome(consulta);
        } catch (FonteIndisponivelException ex) {
            throw new ConsultaExternaException(FALHA);
        }
    }

    private List<String> buscarTitulos(String consulta) {
        try {
            String corpo = rest.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/w/rest.php/v1/search/page")
                            .queryParam("q", consulta)
                            .queryParam("limit", 8)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new FonteIndisponivelException("Wikipedia");
                    })
                    .body(String.class);
            JsonNode pages = mapper.readTree(corpo == null ? "{}" : corpo).path("pages");
            List<String> titulos = new ArrayList<>();
            if (!pages.isArray()) {
                return titulos;
            }
            for (JsonNode page : pages) {
                String key = Textos.limpar(page.path("key").asText(null));
                String title = Textos.limpar(page.path("title").asText(null));
                String candidato = key != null ? key : (title == null ? null : title.replace(' ', '_'));
                if (candidato != null && !titulos.contains(candidato)) {
                    titulos.add(candidato);
                }
            }
            return titulos;
        } catch (RestClientException | IOException ex) {
            throw new FonteIndisponivelException("Wikipedia", ex);
        }
    }

    private AutorWikiDTO resumo(String titulo) {
        try {
            String caminho = "/page/summary/" + URLEncoder.encode(titulo, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String corpo = api.get()
                    .uri(caminho)
                    .retrieve()
                    .body(String.class);
            return interpretarResumo(corpo, titulo);
        } catch (HttpClientErrorException.NotFound ex) {
            return naoEncontrado(titulo.replace('_', ' '));
        } catch (RestClientException | IOException ex) {
            throw new FonteIndisponivelException("Wikipedia", ex);
        }
    }

    private AutorWikiDTO interpretarResumo(String corpo, String tituloFallback) throws IOException {
        JsonNode root = mapper.readTree(corpo == null ? "{}" : corpo);
        String tipo = root.path("type").asText("");
        if ("disambiguation".equalsIgnoreCase(tipo)) {
            return naoEncontrado(tituloFallback.replace('_', ' '));
        }
        String titulo = Textos.limpar(root.path("title").asText(null));
        String descricao = Textos.limpar(root.path("description").asText(null));
        String extrato = Textos.limpar(root.path("extract").asText(null));
        if (extrato == null) {
            return naoEncontrado(tituloFallback.replace('_', ' '));
        }
        String imagem = Textos.limpar(root.path("thumbnail").path("source").asText(null));
        if (imagem == null) {
            imagem = Textos.limpar(root.path("originalimage").path("source").asText(null));
        }
        String pagina = Textos.limpar(root.path("content_urls").path("desktop").path("page").asText(null));
        if (pagina == null && titulo != null) {
            pagina = "https://pt.wikipedia.org/wiki/" + URLEncoder.encode(titulo.replace(' ', '_'), StandardCharsets.UTF_8)
                    .replace("+", "%20");
        }
        return new AutorWikiDTO(
                tituloFallback.replace('_', ' '),
                titulo,
                descricao,
                Textos.cortar(extrato, 4000),
                imagem,
                pagina,
                true
        );
    }

    private int pontuar(AutorWikiDTO bio, String consulta) {
        int pontos = 0;
        String titulo = normalizar(bio.titulo());
        String descricao = normalizar(bio.descricao());
        String termo = normalizar(consulta);
        if (titulo != null && titulo.equals(termo)) {
            pontos += 80;
        } else if (titulo != null && titulo.contains(termo)) {
            pontos += 40;
        }
        if (descricao != null) {
            if (descricao.contains("escritor") || descricao.contains("escritora")
                    || descricao.contains("autor") || descricao.contains("autora")
                    || descricao.contains("poeta") || descricao.contains("romancista")
                    || descricao.contains("novelist") || descricao.contains("writer")) {
                pontos += 50;
            }
            if (descricao.contains("livro") || descricao.contains("obra")) {
                pontos += 10;
            }
        }
        if (bio.imagemUrl() != null) {
            pontos += 5;
        }
        if (bio.extrato() != null) {
            pontos += Math.min(20, bio.extrato().length() / 80);
        }
        return pontos;
    }

    private String normalizar(String valor) {
        String limpo = Textos.limpar(valor);
        return limpo == null ? null : limpo.toLowerCase(Locale.ROOT);
    }

    private AutorWikiDTO naoEncontrado(String nome) {
        return new AutorWikiDTO(nome, null, null, null, null, null, false);
    }
}
