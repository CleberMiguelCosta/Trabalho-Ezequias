package com.biblioteca.acervo.model;

import com.biblioteca.acervo.exception.DadosInvalidosException;

public enum StatusLeitura {
    QUERO_LER("quero_ler", "Quero ler"),
    LENDO("lendo", "Lendo"),
    LIDO("lido", "Lido");

    private final String codigo;
    private final String rotulo;

    StatusLeitura(String codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public static StatusLeitura de(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        for (StatusLeitura status : values()) {
            if (status.name().equalsIgnoreCase(normalizado)
                    || status.codigo.equalsIgnoreCase(normalizado)
                    || status.rotulo.equalsIgnoreCase(valor.trim())) {
                return status;
            }
        }
        throw new DadosInvalidosException(
                "Status de leitura inválido. Use quero_ler, lendo ou lido.");
    }
}
