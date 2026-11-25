package com.biblioteca.acervo.integracao;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Generos {

    private static final Map<String, String> TRADUCAO = Map.ofEntries(
            Map.entry("fiction", "Ficção"),
            Map.entry("romance", "Romance"),
            Map.entry("science fiction", "Ficção científica"),
            Map.entry("fantasy", "Fantasia"),
            Map.entry("poetry", "Poesia"),
            Map.entry("poems", "Poesia"),
            Map.entry("biography", "Biografia"),
            Map.entry("biography & autobiography", "Biografia"),
            Map.entry("history", "História"),
            Map.entry("drama", "Drama"),
            Map.entry("juvenile fiction", "Infantil"),
            Map.entry("children", "Infantil"),
            Map.entry("computers", "Técnico"),
            Map.entry("technology", "Técnico"),
            Map.entry("philosophy", "Filosofia"),
            Map.entry("religion", "Religião"),
            Map.entry("religious", "Religião"),
            Map.entry("romance literature", "Romance"),
            Map.entry("brazilian fiction", "Romance"),
            Map.entry("comics", "Quadrinhos"),
            Map.entry("comic books", "Quadrinhos"),
            Map.entry("mystery", "Mistério"),
            Map.entry("detective and mystery stories", "Mistério"),
            Map.entry("horror", "Terror"),
            Map.entry("adventure", "Aventura"),
            Map.entry("short stories", "Contos"),
            Map.entry("literary criticism", "Crítica literária"),
            Map.entry("brazilian literature", "Romance")
    );

    private static final Set<String> IGNORADOS = Set.of(
            "accessible book",
            "protected daisy",
            "in library",
            "lending library",
            "open library staff picks",
            "internet archive wishlist",
            "long now manual for civilization"
    );

    private Generos() {
    }

    public static String deLista(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        String primeiro = null;
        for (JsonNode item : node) {
            String bruto = item.asText(null);
            String genero = normalizar(bruto);
            if (genero == null) {
                continue;
            }
            if (conhecido(bruto)) {
                return genero;
            }
            if (primeiro == null) {
                primeiro = genero;
            }
        }
        return primeiro;
    }

    private static boolean conhecido(String bruto) {
        String texto = Textos.limpar(bruto);
        if (texto == null) {
            return false;
        }
        int barra = texto.lastIndexOf('/');
        if (barra >= 0 && barra < texto.length() - 1) {
            texto = texto.substring(barra + 1).trim();
        }
        return TRADUCAO.containsKey(texto.toLowerCase(Locale.ROOT));
    }

    public static String normalizar(String bruto) {
        String texto = Textos.limpar(bruto);
        if (texto == null) {
            return null;
        }
        int barra = texto.lastIndexOf('/');
        if (barra >= 0 && barra < texto.length() - 1) {
            texto = texto.substring(barra + 1).trim();
        }
        if (IGNORADOS.contains(texto.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String traduzido = TRADUCAO.get(texto.toLowerCase(Locale.ROOT));
        return traduzido != null ? traduzido : texto;
    }
}
