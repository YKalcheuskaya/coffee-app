package com.ykalcheu.coffee.app.shared.error;

import com.ykalcheu.coffee.app.catalog.domain.CoffeeNameAlreadyExistsException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNotFoundException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeHasActiveOrdersException;
import com.ykalcheu.coffee.app.order.domain.DuplicateCoffeeItemException;
import com.ykalcheu.coffee.app.order.domain.InsufficientInventoryException;
import com.ykalcheu.coffee.app.order.domain.OrderAlreadyCancelledException;
import com.ykalcheu.coffee.app.order.domain.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Translates validation and domain failures into one stable public error contract.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(CoffeeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleCoffeeNotFound(CoffeeNotFoundException exception) {
        return ApiError.of(
                "COFFEE_NOT_FOUND",
                exception.getMessage(),
                Map.of("coffeeId", exception.getCoffeeId())
        );
    }

    @ExceptionHandler(CoffeeNameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleCoffeeNameAlreadyExists(CoffeeNameAlreadyExistsException exception) {
        return ApiError.of("COFFEE_NAME_ALREADY_EXISTS", exception.getMessage(), null);
    }

    @ExceptionHandler(CoffeeHasActiveOrdersException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleCoffeeHasActiveOrders(CoffeeHasActiveOrdersException exception) {
        return ApiError.of("COFFEE_HAS_ACTIVE_ORDERS", exception.getMessage(), Map.of("coffeeId", exception.getCoffeeId()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleOrderNotFound(OrderNotFoundException exception) {
        return ApiError.of("ORDER_NOT_FOUND", exception.getMessage(), Map.of("orderId", exception.getOrderId()));
    }

    @ExceptionHandler(DuplicateCoffeeItemException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleDuplicateCoffeeItem(DuplicateCoffeeItemException exception) {
        return ApiError.of("DUPLICATE_COFFEE_ITEM", exception.getMessage(), Map.of("coffeeId", exception.getCoffeeId()));
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleInsufficientInventory(InsufficientInventoryException exception) {
        return ApiError.of(
                "INSUFFICIENT_INVENTORY",
                exception.getMessage(),
                Map.of(
                        "coffeeId", exception.getCoffeeId(),
                        "requestedQuantity", exception.getRequestedQuantity(),
                        "availableQuantity", exception.getAvailableQuantity()
                )
        );
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleOrderAlreadyCancelled(OrderAlreadyCancelledException exception) {
        return ApiError.of("ORDER_ALREADY_CANCELLED", exception.getMessage(), Map.of("orderId", exception.getOrderId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        return ApiError.validation(fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleMalformedJson() {
        return ApiError.validation(List.of());
    }
}
