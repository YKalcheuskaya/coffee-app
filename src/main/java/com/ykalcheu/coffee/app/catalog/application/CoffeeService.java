package com.ykalcheu.coffee.app.catalog.application;

import com.ykalcheu.coffee.app.catalog.api.CoffeeResponse;
import com.ykalcheu.coffee.app.catalog.api.CreateCoffeeRequest;
import com.ykalcheu.coffee.app.catalog.api.UpdateCoffeeRequest;
import com.ykalcheu.coffee.app.catalog.domain.Coffee;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeHasActiveOrdersException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNameAlreadyExistsException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNotFoundException;
import com.ykalcheu.coffee.app.catalog.persistence.CoffeeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implements catalog use cases and keeps catalog mutations compatible with inventory transactions.
 * A coffee cannot be deleted while a placed order still references it.
 */
@Service
@Transactional(readOnly = true)
public class CoffeeService {

    private final CoffeeRepository coffeeRepository;
    private final ActiveOrderChecker activeOrderChecker;

    public CoffeeService(CoffeeRepository coffeeRepository, ActiveOrderChecker activeOrderChecker) {
        this.coffeeRepository = coffeeRepository;
        this.activeOrderChecker = activeOrderChecker;
    }

    public List<CoffeeResponse> findAll() {
        return coffeeRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(CoffeeResponse::from)
                .toList();
    }

    public CoffeeResponse findById(long coffeeId) {
        return CoffeeResponse.from(findCoffee(coffeeId));
    }

    @Transactional
    public CoffeeResponse create(CreateCoffeeRequest request) {
        assertNameAvailable(request.name(), null);
        Coffee coffee = Coffee.create(
                request.name(), request.origin(), request.roastLevel(), request.unitPrice(), request.availableQuantity()
        );
        return CoffeeResponse.from(coffeeRepository.save(coffee));
    }

    @Transactional
    public CoffeeResponse update(long coffeeId, UpdateCoffeeRequest request) {
        Coffee coffee = coffeeRepository.findByIdForUpdate(coffeeId)
                .orElseThrow(() -> new CoffeeNotFoundException(coffeeId));
        assertNameAvailable(request.name(), coffeeId);
        coffee.update(request.name(), request.origin(), request.roastLevel(), request.unitPrice(), request.availableQuantity());
        return CoffeeResponse.from(coffee);
    }

    @Transactional
    public void delete(long coffeeId) {
        Coffee coffee = coffeeRepository.findByIdForUpdate(coffeeId)
                .orElseThrow(() -> new CoffeeNotFoundException(coffeeId));
        if (activeOrderChecker.hasActiveOrdersForCoffee(coffeeId)) {
            throw new CoffeeHasActiveOrdersException(coffeeId);
        }
        coffeeRepository.delete(coffee);
    }

    private Coffee findCoffee(long coffeeId) {
        return coffeeRepository.findById(coffeeId)
                .orElseThrow(() -> new CoffeeNotFoundException(coffeeId));
    }

    private void assertNameAvailable(String name, Long existingCoffeeId) {
        String normalizedName = Coffee.normalizeName(name);
        boolean exists = existingCoffeeId == null
                ? coffeeRepository.existsByNormalizedName(normalizedName)
                : coffeeRepository.existsByNormalizedNameAndIdNot(normalizedName, existingCoffeeId);
        if (exists) {
            throw new CoffeeNameAlreadyExistsException();
        }
    }
}
