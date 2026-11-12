package com.cleanfresh.ms_cleanfresh_reportes.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Un {@code range} que no existe es un error de quien llama (400), no del servicio. */
@RestControllerAdvice
public class ErroresHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail solicitudInvalida(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
