package com.biblioteca.acervo.repository;

import com.biblioteca.acervo.model.Livro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LivroRepository extends JpaRepository<Livro, Long> {

    Optional<Livro> findByIsbn(String isbn);

    List<Livro> findAllByOrderByTituloAsc();

    @Query("""
            SELECT l FROM Livro l
            WHERE LOWER(l.titulo) LIKE :termo
               OR LOWER(COALESCE(l.autor, '')) LIKE :termo
               OR LOWER(COALESCE(l.editora, '')) LIKE :termo
               OR LOWER(COALESCE(l.isbn, '')) LIKE :isbn
            ORDER BY l.titulo ASC
            """)
    List<Livro> buscarNoAcervo(@Param("termo") String termo, @Param("isbn") String isbn);
}
