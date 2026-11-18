package com.biblioteca.acervo.service;

import com.biblioteca.acervo.exception.DadosInvalidosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
public class CapaService {

    static final int MAX_BYTES = 2_000_000;

    private static final Logger log = LoggerFactory.getLogger(CapaService.class);
    private static final String AGENTE = "AcervoBiblioteca/1.0 (catalogo pessoal)";

    private final HttpClient httpClient;

    public CapaService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    CapaService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Optional<CapaArquivo> importar(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        URI uri = normalizar(url.trim());
        try {
            Optional<CapaArquivo> capa = baixar(uri, 3);
            if (capa.isEmpty() && uri.getRawQuery() != null && uri.getRawQuery().contains("zoom=3")) {
                URI alternativa = URI.create(uri.toString().replace("zoom=3", "zoom=1"));
                capa = baixar(alternativa, 3);
            }
            return capa;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Importação da capa interrompida em {}", uri.getHost());
            return Optional.empty();
        } catch (IOException ex) {
            log.warn("Não foi possível importar a capa de {}", uri.getHost());
            return Optional.empty();
        }
    }

    public URI normalizar(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new DadosInvalidosException("A origem da capa é inválida.");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new DadosInvalidosException("A origem da capa é inválida.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("http".equalsIgnoreCase(uri.getScheme()) && permitida(host)) {
            String consulta = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            uri = URI.create("https://" + uri.getRawAuthority() + uri.getRawPath() + consulta);
            host = uri.getHost().toLowerCase(Locale.ROOT);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !permitida(host)) {
            throw new DadosInvalidosException("A origem da capa não é permitida.");
        }
        return uri;
    }

    private Optional<CapaArquivo> baixar(URI uri, int redirecionamentos) throws IOException, InterruptedException {
        HttpRequest requisicao = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header(HttpHeaders.USER_AGENT, AGENTE)
                .header(HttpHeaders.ACCEPT, "image/avif,image/webp,image/png,image/jpeg,image/gif")
                .GET()
                .build();
        HttpResponse<InputStream> resposta = httpClient.send(requisicao, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream corpo = resposta.body()) {
            int status = resposta.statusCode();
            if (redirecionamentos > 0 && status >= 300 && status < 400) {
                Optional<String> destino = resposta.headers().firstValue(HttpHeaders.LOCATION);
                if (destino.isEmpty()) {
                    return Optional.empty();
                }
                try {
                    return baixar(normalizar(uri.resolve(destino.get()).toString()), redirecionamentos - 1);
                } catch (DadosInvalidosException ex) {
                    return Optional.empty();
                }
            }
            if (status != 200) {
                return Optional.empty();
            }
            byte[] dados = corpo.readNBytes(MAX_BYTES + 1);
            if (dados.length == 0 || dados.length > MAX_BYTES || !imagemUtil(dados)) {
                return Optional.empty();
            }
            String tipo = tipoDe(dados);
            if (tipo == null) {
                return Optional.empty();
            }
            return Optional.of(new CapaArquivo(dados, tipo));
        }
    }

    private boolean imagemUtil(byte[] dados) {
        try {
            BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(dados));
            return imagem != null && imagem.getWidth() >= 40 && imagem.getHeight() >= 60;
        } catch (IOException ex) {
            return false;
        }
    }

    private String tipoDe(byte[] dados) {
        if (dados.length >= 3 && (dados[0] & 0xFF) == 0xFF && (dados[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (dados.length >= 8
                && dados[0] == (byte) 0x89
                && dados[1] == 0x50
                && dados[2] == 0x4E
                && dados[3] == 0x47) {
            return "image/png";
        }
        if (dados.length >= 6
                && dados[0] == 'G'
                && dados[1] == 'I'
                && dados[2] == 'F') {
            return "image/gif";
        }
        if (dados.length >= 12
                && dados[0] == 'R'
                && dados[1] == 'I'
                && dados[2] == 'F'
                && dados[3] == 'F'
                && dados[8] == 'W'
                && dados[9] == 'E'
                && dados[10] == 'B'
                && dados[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private boolean permitida(String host) {
        return "books.google.com".equals(host)
                || "covers.openlibrary.org".equals(host)
                || "books.googleusercontent.com".equals(host)
                || host.endsWith(".googleusercontent.com")
                || "archive.org".equals(host)
                || host.endsWith(".archive.org")
                || "m.media-amazon.com".equals(host)
                || host.endsWith(".media-amazon.com")
                || host.endsWith(".ssl-images-amazon.com")
                || "images-na.ssl-images-amazon.com".equals(host)
                || "images-eu.ssl-images-amazon.com".equals(host);
    }
}
