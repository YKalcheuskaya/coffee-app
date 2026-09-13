package com.ykalcheu.coffee.app.shared.error;

import com.ykalcheu.coffee.app.catalog.domain.CoffeeNameAlreadyExistsException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeNotFoundException;
import com.ykalcheu.coffee.app.catalog.domain.CoffeeHasActiveOrdersException;
import com.ykalcheu.coffee.app.order.domain.DuplicateCoffeeItemException;
import com.ykalcheu.coffee.app.order.domain.InsufficientInventoryException;
import com.ykalcheu.coffee.app.order.domain.OrderAlreadyCancelledException;
import com.ykalcheu.coffee.app.order.domain.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.ykalcheu.coffee.app.order.domain.CoffeeOrder;
import com.ykalcheu.coffee.app.catalog.domain.Coffee;
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


    @ExceptionHandler(CoffeeOrder.AmountLimitExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleAmountLimit(CoffeeOrder.AmountLimitExceededException exception) {
        return ApiError.of("ORDER_TOTAL_TOO_LARGE", exception.getMessage(), null);
    }

    @ExceptionHandler(Coffee.InventoryLimitExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError handleInventoryLimit(Coffee.InventoryLimitExceededException exception) {
        return ApiError.of("INVENTORY_LIMIT_EXCEEDED", exception.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDatabaseConstraint(DataIntegrityViolationException exception) {
        // Translate only this PostgreSQL unique constraint, including failures raised at commit.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "coffee_normalized_name_key".equals(violation.getConstraintName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiError.of("COFFEE_NAME_ALREADY_EXISTS", "A coffee with this name already exists.", null));
            }
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "The request could not be completed.", null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handlePathType(MethodArgumentTypeMismatchException exception) {
        return ApiError.validation(List.of(new FieldErrorResponse(exception.getName(), "Must be a valid integer identifier.")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(exception.getHeaders())
                .body(ApiError.of("METHOD_NOT_ALLOWED", "HTTP method is not supported for this resource.", null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    ApiError handleMediaType() {
        return ApiError.of("UNSUPPORTED_MEDIA_TYPE", "Use application/json for request bodies.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError handleUnknownResource() {
        return ApiError.of("RESOURCE_NOT_FOUND", "Resource not found.", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError handleMalformedJson() {
        return ApiError.validation(List.of());
    }
}
