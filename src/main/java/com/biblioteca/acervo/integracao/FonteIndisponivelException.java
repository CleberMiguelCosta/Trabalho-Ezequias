package com.biblioteca.acervo.integracao;

public class FonteIndisponivelException extends RuntimeException {

    public FonteIndisponivelException(String fonte) {
        super(fonte);
    }

    public FonteIndisponivelException(String fonte, Throwable causa) {
        super(fonte, causa);
    }
}
