package com.ykalcheu.coffee.app.catalog.persistence;

import com.ykalcheu.coffee.app.catalog.domain.Coffee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CoffeeRepository extends JpaRepository<Coffee, Long> {

    boolean existsByNormalizedName(String normalizedName);

    boolean existsByNormalizedNameAndIdNot(String normalizedName, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coffee from Coffee coffee where coffee.id = :id")
    Optional<Coffee> findByIdForUpdate(long id);
}
