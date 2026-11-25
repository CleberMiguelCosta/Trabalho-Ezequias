package com.biblioteca.acervo.model;

import com.biblioteca.acervo.LimitesLivro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "livros",
        uniqueConstraints = @UniqueConstraint(name = "uk_livros_isbn", columnNames = "isbn")
)
public class Livro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = LimitesLivro.TITULO)
    private String titulo;

    @Column(length = LimitesLivro.AUTOR)
    private String autor;

    @Column(length = LimitesLivro.ISBN)
    private String isbn;

    @Column(name = "ano_publicacao")
    private Integer anoPublicacao;

    @Column(length = LimitesLivro.GENERO)
    private String genero;

    @Column(length = LimitesLivro.EDITORA)
    private String editora;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(length = LimitesLivro.EDICAO)
    private String edicao;

    @Column(name = "numero_paginas")
    private Integer numeroPaginas;

    @Column(length = LimitesLivro.DESCRICAO)
    private String descricao;

    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "capa")
    private byte[] capa;

    @Column(name = "capa_tipo", length = 40)
    private String capaTipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_leitura", length = 20)
    private StatusLeitura statusLeitura;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean favorito = false;

    @Column
    private Integer avaliacao;

    @Column(length = LimitesLivro.ANOTACAO)
    private String anotacao;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public Integer getAnoPublicacao() {
        return anoPublicacao;
    }

    public void setAnoPublicacao(Integer anoPublicacao) {
        this.anoPublicacao = anoPublicacao;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String genero) {
        this.genero = genero;
    }

    public String getEditora() {
        return editora;
    }

    public void setEditora(String editora) {
        this.editora = editora;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public String getEdicao() {
        return edicao;
    }

    public void setEdicao(String edicao) {
        this.edicao = edicao;
    }

    public Integer getNumeroPaginas() {
        return numeroPaginas;
    }

    public void setNumeroPaginas(Integer numeroPaginas) {
        this.numeroPaginas = numeroPaginas;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public byte[] getCapa() {
        return capa;
    }

    public void setCapa(byte[] capa) {
        this.capa = capa;
    }

    public String getCapaTipo() {
        return capaTipo;
    }

    public void setCapaTipo(String capaTipo) {
        this.capaTipo = capaTipo;
    }

    public StatusLeitura getStatusLeitura() {
        return statusLeitura;
    }

    public void setStatusLeitura(StatusLeitura statusLeitura) {
        this.statusLeitura = statusLeitura;
    }

    public boolean isFavorito() {
        return favorito;
    }

    public void setFavorito(boolean favorito) {
        this.favorito = favorito;
    }

    public Integer getAvaliacao() {
        return avaliacao;
    }

    public void setAvaliacao(Integer avaliacao) {
        this.avaliacao = avaliacao;
    }

    public String getAnotacao() {
        return anotacao;
    }

    public void setAnotacao(String anotacao) {
        this.anotacao = anotacao;
    }
}
