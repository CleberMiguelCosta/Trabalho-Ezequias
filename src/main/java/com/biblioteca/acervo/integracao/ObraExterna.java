package com.biblioteca.acervo.integracao;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record ObraExterna(
        String titulo,
        String autor,
        String isbn,
        String editora,
        Integer anoPublicacao,
        String edicao,
        Integer numeroPaginas,
        String descricao,
        String genero,
        String capaUrl,
        List<String> capas,
        Integer popularidade,
        String origem
) {

    public ObraExterna {
        capas = normalizarCapas(capaUrl, capas);
        if (capaUrl == null && !capas.isEmpty()) {
            capaUrl = capas.get(0);
        }
    }

    public static ObraExterna de(
            String titulo,
            String autor,
            String isbn,
            String editora,
            Integer anoPublicacao,
            String edicao,
            Integer numeroPaginas,
            String descricao,
            String genero,
            String capaUrl,
            String origem) {
        return new ObraExterna(
                titulo, autor, isbn, editora, anoPublicacao, edicao,
                numeroPaginas, descricao, genero, capaUrl, List.of(), null, origem);
    }

    public ObraExterna completarCom(ObraExterna outra) {
        if (outra == null) {
            return this;
        }
        String capa = primeiro(capaUrl, outra.capaUrl);
        List<String> todasCapas = unirCapas(capas, outra.capas, capa, outra.capaUrl);
        Integer pop = popularidade == null ? outra.popularidade
                : outra.popularidade == null ? popularidade
                : Math.max(popularidade, outra.popularidade);
        ObraExterna mesclada = new ObraExterna(
                primeiro(titulo, outra.titulo),
                primeiro(autor, outra.autor),
                primeiro(isbn, outra.isbn),
                primeiro(editora, outra.editora),
                anoPublicacao != null ? anoPublicacao : outra.anoPublicacao,
                primeiro(edicao, outra.edicao),
                numeroPaginas != null ? numeroPaginas : outra.numeroPaginas,
                melhorDescricao(descricao, outra.descricao),
                primeiro(genero, outra.genero),
                capa,
                todasCapas,
                pop,
                origem
        );
        if (mesclada.mesmosDados(this) || outra.origem == null || outra.origem.isBlank()) {
            return mesclada;
        }
        String combinada = origem == null || origem.isBlank() || origem.contains(outra.origem)
                ? (origem == null || origem.isBlank() ? outra.origem : origem)
                : origem + " e " + outra.origem;
        return mesclada.comOrigem(combinada);
    }

    public ObraExterna comCapas(List<String> novasCapas) {
        List<String> unidas = unirCapas(capas, novasCapas, capaUrl, null);
        String capa = capaUrl != null ? capaUrl : (unidas.isEmpty() ? null : unidas.get(0));
        return new ObraExterna(
                titulo, autor, isbn, editora, anoPublicacao, edicao,
                numeroPaginas, descricao, genero, capa, unidas, popularidade, origem
        );
    }

    public boolean incompleta() {
        return vazio(autor)
                || vazio(isbn)
                || vazio(editora)
                || anoPublicacao == null
                || vazio(edicao)
                || numeroPaginas == null
                || vazio(descricao)
                || descricaoCurta(descricao)
                || vazio(genero)
                || vazio(capaUrl);
    }

    public int pontuacao() {
        int pontos = 0;
        if (!vazio(titulo)) pontos++;
        if (!vazio(autor)) pontos++;
        if (!vazio(isbn)) pontos++;
        if (!vazio(editora)) pontos++;
        if (anoPublicacao != null) pontos++;
        if (!vazio(edicao)) pontos++;
        if (numeroPaginas != null) pontos++;
        if (!vazio(descricao)) {
            pontos += descricaoCurta(descricao) ? 1 : 2;
        }
        if (!vazio(genero)) pontos++;
        if (!vazio(capaUrl)) pontos++;
        pontos += Math.min(2, capas == null ? 0 : capas.size() / 2);
        if (popularidade != null) {
            pontos += Math.min(3, popularidade / 20);
        }
        return pontos;
    }

    private ObraExterna comOrigem(String novaOrigem) {
        return new ObraExterna(
                titulo, autor, isbn, editora, anoPublicacao, edicao,
                numeroPaginas, descricao, genero, capaUrl, capas, popularidade, novaOrigem
        );
    }

    private boolean mesmosDados(ObraExterna outra) {
        return Objects.equals(titulo, outra.titulo)
                && Objects.equals(autor, outra.autor)
                && Objects.equals(isbn, outra.isbn)
                && Objects.equals(editora, outra.editora)
                && Objects.equals(anoPublicacao, outra.anoPublicacao)
                && Objects.equals(edicao, outra.edicao)
                && Objects.equals(numeroPaginas, outra.numeroPaginas)
                && Objects.equals(descricao, outra.descricao)
                && Objects.equals(genero, outra.genero)
                && Objects.equals(capaUrl, outra.capaUrl);
    }

    private static String melhorDescricao(String atual, String reserva) {
        if (vazio(atual)) {
            return reserva;
        }
        if (vazio(reserva)) {
            return atual;
        }
        return atual.length() >= reserva.length() ? atual : reserva;
    }

    private static boolean descricaoCurta(String valor) {
        return valor != null && valor.trim().length() < 80;
    }

    private static String primeiro(String atual, String reserva) {
        if (!vazio(atual)) {
            return atual;
        }
        if (!vazio(reserva)) {
            return reserva;
        }
        return null;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static List<String> normalizarCapas(String capaUrl, List<String> capas) {
        LinkedHashSet<String> unicas = new LinkedHashSet<>();
        if (capaUrl != null && !capaUrl.isBlank()) {
            unicas.add(capaUrl.trim());
        }
        if (capas != null) {
            for (String capa : capas) {
                if (capa != null && !capa.isBlank()) {
                    unicas.add(capa.trim());
                }
            }
        }
        return List.copyOf(unicas);
    }

    private static List<String> unirCapas(List<String> a, List<String> b, String... extras) {
        LinkedHashSet<String> unicas = new LinkedHashSet<>();
        if (a != null) {
            unicas.addAll(a);
        }
        if (b != null) {
            unicas.addAll(b);
        }
        if (extras != null) {
            for (String extra : extras) {
                if (extra != null && !extra.isBlank()) {
                    unicas.add(extra.trim());
                }
            }
        }
        return List.copyOf(unicas);
    }
}
