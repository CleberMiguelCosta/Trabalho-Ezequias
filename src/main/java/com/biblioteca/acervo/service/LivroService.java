package com.biblioteca.acervo.service;

import com.biblioteca.acervo.dto.LivroRequestDTO;
import com.biblioteca.acervo.dto.LivroResponseDTO;
import com.biblioteca.acervo.exception.DadosInvalidosException;
import com.biblioteca.acervo.exception.RecursoNaoEncontradoException;
import com.biblioteca.acervo.exception.RegraNegocioException;
import com.biblioteca.acervo.model.Livro;
import com.biblioteca.acervo.repository.LivroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Locale;

@Service
public class LivroService {

    private final LivroRepository livroRepository;
    private final CapaService capaService;

    public LivroService(LivroRepository livroRepository, CapaService capaService) {
        this.livroRepository = livroRepository;
        this.capaService = capaService;
    }

    @Transactional(readOnly = true)
    public List<LivroResponseDTO> listar(String termo) {
        if (termo == null || termo.isBlank()) {
            return livroRepository.findAllByOrderByTituloAsc()
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
        String consulta = termo.trim().replaceAll("\\s+", " ");
        if (consulta.length() > 150) {
            throw new DadosInvalidosException("A busca no acervo deve ter no máximo 150 caracteres.");
        }
        String like = "%" + escaparLike(consulta.toLowerCase(Locale.ROOT)) + "%";
        String isbn = "%" + escaparLike(consulta.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT)) + "%";
        return livroRepository.buscarNoAcervo(like, isbn)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LivroResponseDTO buscarPorId(Long id) {
        return toResponse(buscarEntidade(id));
    }

    @Transactional
    public LivroResponseDTO criar(LivroRequestDTO dto) {
        String isbn = normalizarIsbn(dto.isbn());
        if (isbn != null) {
            garantirIsbnDisponivel(isbn, null);
        }
        if (dto.anoPublicacao() != null) {
            validarAno(dto.anoPublicacao());
        }

        Livro livro = new Livro();
        aplicar(livro, dto, isbn);
        return toResponse(livroRepository.save(livro));
    }

    @Transactional
    public LivroResponseDTO atualizar(Long id, LivroRequestDTO dto) {
        Livro livro = buscarEntidade(id);
        String isbn = normalizarIsbn(dto.isbn());
        if (isbn != null) {
            garantirIsbnDisponivel(isbn, id);
        }
        if (dto.anoPublicacao() != null) {
            validarAno(dto.anoPublicacao());
        }
        aplicar(livro, dto, isbn);
        return toResponse(livroRepository.save(livro));
    }

    @Transactional
    public void excluir(Long id) {
        Livro livro = buscarEntidade(id);
        livroRepository.delete(livro);
    }

    @Transactional(readOnly = true)
    public CapaArquivo obterCapa(Long id) {
        Livro livro = buscarEntidade(id);
        if (livro.getCapa() == null || livro.getCapa().length == 0) {
            throw new RecursoNaoEncontradoException("Este livro não possui capa.");
        }
        String tipo = livro.getCapaTipo() == null ? "image/jpeg" : livro.getCapaTipo();
        return new CapaArquivo(livro.getCapa(), tipo);
    }

    private Livro buscarEntidade(Long id) {
        return livroRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Livro não encontrado para o id " + id + "."));
    }

    private void garantirIsbnDisponivel(String isbn, Long idAtual) {
        livroRepository.findByIsbn(isbn).ifPresent(existente -> {
            if (idAtual == null || !existente.getId().equals(idAtual)) {
                throw new RegraNegocioException(
                        "Já existe um livro cadastrado com o ISBN " + isbn + ".");
            }
        });
    }

    private void validarAno(Integer ano) {
        int limite = Year.now().getValue();
        if (ano > limite) {
            throw new DadosInvalidosException(
                    "O ano de publicação não pode ser posterior a " + limite + ".");
        }
    }

    private String normalizarIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        String normalizado = isbn.replaceAll("[\\s-]", "").toUpperCase();
        if (!normalizado.matches("\\d{9}[\\dX]|\\d{13}")) {
            throw new DadosInvalidosException(
                    "Informe um ISBN com 10 ou 13 dígitos. Hífens são opcionais.");
        }
        return normalizado;
    }

    private void aplicar(Livro livro, LivroRequestDTO dto, String isbn) {
        livro.setTitulo(dto.titulo().trim());
        livro.setAutor(limparOpcional(dto.autor()));
        livro.setIsbn(isbn);
        livro.setAnoPublicacao(dto.anoPublicacao());
        livro.setGenero(limparOpcional(dto.genero()));
        livro.setEditora(limparOpcional(dto.editora()));
        if (dto.quantidade() != null) {
            livro.setQuantidade(dto.quantidade());
        } else if (livro.getQuantidade() == null) {
            livro.setQuantidade(1);
        }
        livro.setEdicao(limparOpcional(dto.edicao()));
        livro.setNumeroPaginas(dto.numeroPaginas());
        livro.setDescricao(limparOpcional(dto.descricao()));
        capaService.importar(dto.capaOrigem()).ifPresent(capa -> {
            livro.setCapa(capa.conteudo());
            livro.setCapaTipo(capa.tipo());
        });
    }

    private String escaparLike(String valor) {
        return valor.replace("\\", "").replace("%", "").replace("_", "");
    }

    private String limparOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private LivroResponseDTO toResponse(Livro livro) {
        return new LivroResponseDTO(
                livro.getId(),
                livro.getTitulo(),
                livro.getAutor(),
                livro.getIsbn(),
                livro.getAnoPublicacao(),
                livro.getGenero(),
                livro.getEditora(),
                livro.getQuantidade(),
                livro.getEdicao(),
                livro.getNumeroPaginas(),
                livro.getDescricao(),
                livro.getCapa() != null && livro.getCapa().length > 0
        );
    }
}
