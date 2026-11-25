package com.biblioteca.acervo.integracao;

import com.biblioteca.acervo.LimitesLivro;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Textos {

    public static final String AGENTE = "AcervoBiblioteca/1.0 (catalogo pessoal)";

    private static final Pattern ANO = Pattern.compile("(\\d{4})");
    private static final Pattern ISBN = Pattern.compile("\\d{9}[\\dX]|\\d{13}");

    private Textos() {
    }

    public static String autor(String valor) {
        String texto = limpar(valor);
        if (texto == null) {
            return null;
        }
        return limpar(texto.replaceAll("(?i)(\\p{L}+(?:\\s+\\p{L}+){0,3})\\s+\\1\\b", "$1"));
    }

    public static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.replaceAll("\\s+", " ").trim();
        return texto.isEmpty() ? null : texto;
    }

    public static String limparHtml(String valor) {
        if (valor == null) {
            return null;
        }
        String semTags = valor
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        semTags = semTags.replaceAll("\\s+([.,;:!?])", "$1");
        return limpar(semTags);
    }

    public static String cortar(String valor, int maximo) {
        String texto = limpar(valor);
        if (texto == null || texto.length() <= maximo) {
            return texto;
        }
        int corte = texto.lastIndexOf(' ', maximo);
        if (corte < (int) (maximo * 0.6)) {
            return texto.substring(0, maximo).trim();
        }
        return texto.substring(0, corte).trim();
    }

    public static Integer ano(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher matcher = ANO.matcher(texto);
        if (!matcher.find()) {
            return null;
        }
        int valor = Integer.parseInt(matcher.group(1));
        int atual = Year.now().getValue();
        if (valor < 1450 || valor > atual) {
            return null;
        }
        return valor;
    }

    public static Integer paginas(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.canConvertToInt()) {
            return null;
        }
        int valor = node.asInt();
        if (valor < 1 || valor > LimitesLivro.PAGINAS_MAX) {
            return null;
        }
        return valor;
    }

    public static String isbn(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!ISBN.matcher(normalizado).matches()) {
            return null;
        }
        return normalizado;
    }

    public static String juntar(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> partes = new ArrayList<>();
        for (JsonNode item : node) {
            String texto = limpar(item.asText(null));
            if (texto != null) {
                partes.add(texto);
            }
        }
        if (partes.isEmpty()) {
            return null;
        }
        return String.join(", ", partes);
    }

    public static String primeiroTexto(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return limpar(node.asText());
        }
        if (!node.isArray() || node.isEmpty()) {
            return null;
        }
        return limpar(node.get(0).asText(null));
    }

    public static String descricao(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return limparHtml(node.asText());
        }
        if (node.isObject() && node.hasNonNull("value")) {
            return limparHtml(node.get("value").asText());
        }
        return null;
    }

    public static String escolherIsbn(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        String isbn10 = null;
        for (JsonNode item : node) {
            String candidato = isbn(item.asText(null));
            if (candidato == null) {
                continue;
            }
            if (candidato.length() == 13) {
                return candidato;
            }
            if (isbn10 == null) {
                isbn10 = candidato;
            }
        }
        return isbn10;
    }

    public static ObraExterna ajustar(ObraExterna obra) {
        List<String> capas = new ArrayList<>();
        if (obra.capas() != null) {
            for (String capa : obra.capas()) {
                String cortada = cortar(capa, LimitesLivro.CAPA_ORIGEM);
                if (cortada != null) {
                    capas.add(cortada);
                }
            }
        }
        return new ObraExterna(
                cortar(obra.titulo(), LimitesLivro.TITULO),
                cortar(autor(obra.autor()), LimitesLivro.AUTOR),
                isbn(obra.isbn()),
                cortar(obra.editora(), LimitesLivro.EDITORA),
                obra.anoPublicacao(),
                cortar(obra.edicao(), LimitesLivro.EDICAO),
                obra.numeroPaginas(),
                cortar(obra.descricao(), LimitesLivro.DESCRICAO),
                cortar(obra.genero(), LimitesLivro.GENERO),
                cortar(obra.capaUrl(), LimitesLivro.CAPA_ORIGEM),
                capas,
                obra.popularidade(),
                limpar(obra.origem())
        );
    }

    public static String normalizarBusca(String valor) {
        if (valor == null) {
            return "";
        }
        String semAcento = java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
