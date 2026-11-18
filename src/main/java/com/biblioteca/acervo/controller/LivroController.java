package com.biblioteca.acervo.controller;

import com.biblioteca.acervo.dto.LivroBuscaDTO;
import com.biblioteca.acervo.dto.LivroRequestDTO;
import com.biblioteca.acervo.dto.LivroResponseDTO;
import com.biblioteca.acervo.dto.PaginaBuscaDTO;
import com.biblioteca.acervo.service.BuscaLivrosService;
import com.biblioteca.acervo.service.CapaArquivo;
import com.biblioteca.acervo.service.LivroService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/livros")
public class LivroController {

    private final LivroService livroService;
    private final BuscaLivrosService buscaLivrosService;

    public LivroController(LivroService livroService, BuscaLivrosService buscaLivrosService) {
        this.livroService = livroService;
        this.buscaLivrosService = buscaLivrosService;
    }

    @GetMapping
    public ResponseEntity<List<LivroResponseDTO>> listar(@RequestParam(required = false) String termo) {
        return ResponseEntity.ok(livroService.listar(termo));
    }

    @GetMapping("/busca")
    public ResponseEntity<PaginaBuscaDTO> buscar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer limite) {
        return ResponseEntity.ok(buscaLivrosService.buscar(titulo, pagina, limite));
    }

    @GetMapping("/busca/complemento")
    public ResponseEntity<LivroBuscaDTO> complementar(@RequestParam(required = false) String isbn) {
        return ResponseEntity.ok(buscaLivrosService.complementar(isbn));
    }

    @GetMapping("/{id}/capa")
    public ResponseEntity<byte[]> capa(@PathVariable Long id) {
        CapaArquivo capa = livroService.obterCapa(id);
        return ResponseEntity.ok()
                .contentType(tipoDaCapa(capa.tipo()))
                .cacheControl(CacheControl.noCache())
                .body(capa.conteudo());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LivroResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(livroService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<LivroResponseDTO> criar(@Valid @RequestBody LivroRequestDTO dto) {
        LivroResponseDTO criado = livroService.criar(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criado.id())
                .toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LivroResponseDTO> atualizar(@PathVariable Long id,
                                                       @Valid @RequestBody LivroRequestDTO dto) {
        return ResponseEntity.ok(livroService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        livroService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private MediaType tipoDaCapa(String tipo) {
        try {
            return MediaType.parseMediaType(tipo);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.IMAGE_JPEG;
        }
    }
}
