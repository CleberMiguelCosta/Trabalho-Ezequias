package com.biblioteca.acervo.dto;

public record AutorWikiDTO(
        String nomeConsultado,
        String titulo,
        String descricao,
        String extrato,
        String imagemUrl,
        String paginaUrl,
        boolean encontrado
) {
    public AutorWikiDTO comNome(String nome) {
        return new AutorWikiDTO(nome, titulo, descricao, extrato, imagemUrl, paginaUrl, encontrado);
    }
}
