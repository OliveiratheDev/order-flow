package com.start.overflow.catalog.entity;

import com.start.overflow.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTest {
    @ParameterizedTest
    @CsvSource(value = {
            "Eletrônicos de Áudio|eletronicos-de-audio",
            "NOME EM CAIXA ALTA|nome-em-caixa-alta",
            "espaço   múltiplo|espaco-multiplo",
            "produto@especial#|produto-especial",
            "'  pontas  '|pontas"
    }, delimiter = '|')
    void deveGerarSlugNormalizado_quandoNomeForValido(String name, String expectedSlug) {
        assertThat(Category.generateSlug(name)).isEqualTo(expectedSlug);
    }

    @Test
    void deveAtualizarNomeESlug_quandoRenomearCategoria() {
        Category category = new Category("Casa", null);

        category.rename("Áudio & Vídeo");

        assertThat(category.getName()).isEqualTo("Áudio & Vídeo");
        assertThat(category.getSlug()).isEqualTo("audio-video");
    }

    @Test
    void deveRecusarNome_quandoNaoHouverLetraOuNumero() {
        assertThatThrownBy(() -> new Category("!!!", null))
                .isInstanceOf(ValidationException.class);
    }
}
