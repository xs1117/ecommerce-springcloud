package org.example.inventory.controller;

import org.example.inventory.service.InventoryLockAcquisitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(InventoryLockAcquisitionException.class)
    public ResponseEntity<Map<String, Object>> inventoryLock(InventoryLockAcquisitionException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(Map.of("ok", false, "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() == null
                ? "invalid request"
                : ex.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("ok", false, "message", message == null ? "invalid request" : message));
    }
}

