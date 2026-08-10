package com.start.overflow.catalog.entity;

import com.start.overflow.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTest {
    @Test
    void generatesNormalizedSlug() {
        Category category = new Category("  Eletrônicos   de Áudio!  ", "Descrição");

        assertThat(category.getName()).isEqualTo("Eletrônicos   de Áudio!");
        assertThat(category.getSlug()).isEqualTo("eletronicos-de-audio");
        assertThat(category.getActive()).isTrue();
    }

    @Test
    void renameUpdatesNameAndSlug() {
        Category category = new Category("Casa", null);

        category.rename("Áudio & Vídeo");

        assertThat(category.getName()).isEqualTo("Áudio & Vídeo");
        assertThat(category.getSlug()).isEqualTo("audio-video");
    }

    @Test
    void rejectsNameWithoutLetterOrNumber() {
        assertThatThrownBy(() -> new Category("!!!", null))
                .isInstanceOf(ValidationException.class);
    }
}
