package com.biblioteca.acervo.controller;

import com.biblioteca.acervo.dto.AutorAcervoDTO;
import com.biblioteca.acervo.dto.AutorWikiDTO;
import com.biblioteca.acervo.exception.DadosInvalidosException;
import com.biblioteca.acervo.integracao.WikipediaAutorCliente;
import com.biblioteca.acervo.service.LivroService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/autores")
public class AutorController {

    private final LivroService livroService;
    private final WikipediaAutorCliente wikipediaAutorCliente;

    public AutorController(LivroService livroService, WikipediaAutorCliente wikipediaAutorCliente) {
        this.livroService = livroService;
        this.wikipediaAutorCliente = wikipediaAutorCliente;
    }

    @GetMapping
    public ResponseEntity<List<AutorAcervoDTO>> listar() {
        return ResponseEntity.ok(livroService.listarAutores());
    }

    @GetMapping("/wiki")
    public ResponseEntity<AutorWikiDTO> wikipedia(@RequestParam(required = false) String nome) {
        if (nome == null || nome.isBlank()) {
            throw new DadosInvalidosException("Informe o nome do autor para consultar a Wikipedia.");
        }
        if (nome.trim().length() < 2) {
            throw new DadosInvalidosException("Informe ao menos 2 caracteres do nome do autor.");
        }
        if (nome.trim().length() > 150) {
            throw new DadosInvalidosException("O nome do autor deve ter no máximo 150 caracteres.");
        }
        return ResponseEntity.ok(wikipediaAutorCliente.buscarBiografia(nome));
    }
}
