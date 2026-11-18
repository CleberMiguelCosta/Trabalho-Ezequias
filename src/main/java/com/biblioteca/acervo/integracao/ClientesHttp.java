package com.biblioteca.acervo.integracao;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

final class ClientesHttp {

    private ClientesHttp() {
    }

    static RestClient criar(String baseUrl) {
        return criar(baseUrl, 5, 8);
    }

    static RestClient criar(String baseUrl, int connectSegundos, int readSegundos) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectSegundos));
        factory.setReadTimeout(Duration.ofSeconds(readSegundos));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, Textos.AGENTE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
