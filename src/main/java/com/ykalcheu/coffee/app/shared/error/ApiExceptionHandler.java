package com.ykalcheu.coffee.app.shared.error;

import com.ykalcheu.coffee.app.catalog.domain.CoffeeNameAlreadyExistsException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

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
