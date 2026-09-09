package com.ykalcheu.coffee.app.catalog.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoffeeRepositoryTest {

    @Autowired
    private CoffeeRepository coffeeRepository;

    @Test
    void shouldLoadFlywaySeedDataAndLockRequestedRowsInIdentifierOrder() {
        assertThat(coffeeRepository.existsByNormalizedName("espresso")).isTrue();

        assertThat(coffeeRepository.findAllByIdInForUpdate(List.of(3L, 1L)))
                .extracting(coffee -> coffee.getId())
                .containsExactly(1L, 3L);
    }
}
