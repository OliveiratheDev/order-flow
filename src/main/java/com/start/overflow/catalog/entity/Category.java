package com.start.overflow.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.text.Normalizer;
import java.time.LocalDateTime;

@Entity
@Getter
@Table (name = "category")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Boolean active;
    @Version
    private Long version;
    private LocalDateTime createdAt;

    protected  Category() {}
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
        this.slug = gerarSlug(name) ;
        this.active = true;
        this.createdAt = LocalDateTime.now();

    }

    private String gerarSlug(String texto) {

        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD);

        semAcento = semAcento.replaceAll("\\p{M}", "");

        String minusculo = semAcento.toLowerCase();

        String comHifen = minusculo.replaceAll("[^a-z0-9]+", "-");

        String resultado = comHifen.replaceAll("^-+|-+$", "");

        return resultado;
    }

    public void activate() {
        this.active = true;
    }
    public void deactivate() {
        this.active = false;
    }
    public void rename(String newName) {
        this.name = newName;
        this.slug = gerarSlug(newName);
    }
}
