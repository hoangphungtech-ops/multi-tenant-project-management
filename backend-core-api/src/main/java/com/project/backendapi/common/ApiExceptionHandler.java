package com.project.backendapi.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(Map.of("error","NOT_FOUND","message",ex.getMessage()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("error","FORBIDDEN","message",ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error","VALIDATION_ERROR","message","Request validation failed"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> general(Exception ex) {
        return ResponseEntity.status(500).body(Map.of("error","INTERNAL_ERROR","message","Unexpected server error"));
    }
}
