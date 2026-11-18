package com.biblioteca.acervo.service;

import com.biblioteca.acervo.exception.DadosInvalidosException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapaServiceTest {

    private final CapaService capaService = new CapaService();

    @Test
    void aceitaHostsDasBasesERecusaOsDemais() {
        URI google = capaService.normalizar("http://books.google.com/books/content?id=abc&zoom=3");
        assertEquals("https", google.getScheme());
        assertEquals("books.google.com", google.getHost());

        assertEquals("covers.openlibrary.org",
                capaService.normalizar("https://covers.openlibrary.org/b/id/1-L.jpg").getHost());
        assertEquals("lh3.googleusercontent.com",
                capaService.normalizar("https://lh3.googleusercontent.com/capa.jpg").getHost());
        assertEquals("ia601900.us.archive.org",
                capaService.normalizar("https://ia601900.us.archive.org/view_archive.php?archive=1").getHost());

        assertThrows(DadosInvalidosException.class,
                () -> capaService.normalizar("https://example.com/capa.jpg"));
        assertThrows(DadosInvalidosException.class,
                () -> capaService.normalizar("http://127.0.0.1/capa.jpg"));
        assertThrows(DadosInvalidosException.class,
                () -> capaService.normalizar("https://books.google.com.evil.example/capa.jpg"));
        assertThrows(DadosInvalidosException.class,
                () -> capaService.normalizar("file:///tmp/capa.jpg"));
    }

    @Test
    void ignoraOrigemVazia() {
        assertTrue(capaService.importar(null).isEmpty());
        assertTrue(capaService.importar("   ").isEmpty());
    }
}
