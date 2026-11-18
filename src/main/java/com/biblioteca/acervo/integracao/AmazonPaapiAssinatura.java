package com.biblioteca.acervo.integracao;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class AmazonPaapiAssinatura {

    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AMZ_DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private AmazonPaapiAssinatura() {
    }

    static Map<String, String> assinar(
            String metodo,
            String host,
            String caminho,
            String alvo,
            String corpo,
            String accessKey,
            String secretKey,
            String region,
            String servico,
            Instant agora) {
        try {
            String amzDate = AMZ_DATE.format(agora);
            String dia = AMZ_DAY.format(agora);
            String hashCorpo = sha256Hex(corpo);

            TreeMap<String, String> cabecalhos = new TreeMap<>();
            cabecalhos.put("content-encoding", "amz-1.0");
            cabecalhos.put("content-type", "application/json; charset=utf-8");
            cabecalhos.put("host", host);
            cabecalhos.put("x-amz-date", amzDate);
            cabecalhos.put("x-amz-target", alvo);

            StringBuilder canonicos = new StringBuilder();
            StringBuilder assinados = new StringBuilder();
            for (Map.Entry<String, String> entrada : cabecalhos.entrySet()) {
                canonicos.append(entrada.getKey()).append(':').append(entrada.getValue().trim()).append('\n');
                if (!assinados.isEmpty()) {
                    assinados.append(';');
                }
                assinados.append(entrada.getKey());
            }

            String requisicaoCanonica = metodo + '\n'
                    + caminho + '\n'
                    + '\n'
                    + canonicos
                    + '\n'
                    + assinados + '\n'
                    + hashCorpo;

            String escopo = dia + '/' + region + '/' + servico + "/aws4_request";
            String paraAssinar = "AWS4-HMAC-SHA256\n"
                    + amzDate + '\n'
                    + escopo + '\n'
                    + sha256Hex(requisicaoCanonica);

            byte[] chaveData = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dia);
            byte[] chaveRegiao = hmac(chaveData, region);
            byte[] chaveServico = hmac(chaveRegiao, servico);
            byte[] chaveAssinatura = hmac(chaveServico, "aws4_request");
            String assinatura = HexFormat.of().formatHex(hmac(chaveAssinatura, paraAssinar));

            String autorizacao = "AWS4-HMAC-SHA256 Credential=" + accessKey + '/' + escopo
                    + ", SignedHeaders=" + assinados
                    + ", Signature=" + assinatura;

            Map<String, String> resultado = new TreeMap<>();
            resultado.putAll(cabecalhos);
            resultado.put("Authorization", autorizacao);
            return resultado;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao assinar a requisição da Amazon PA-API.", ex);
        }
    }

    private static String sha256Hex(String texto) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] chave, String dados) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return mac.doFinal(dados.getBytes(StandardCharsets.UTF_8));
    }
}
