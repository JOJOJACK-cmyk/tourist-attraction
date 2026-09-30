package com.travelprice.api;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) { return ResponseEntity.status(e.getStatus()).body(Map.of("error",e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> validation(Exception e) { return ResponseEntity.badRequest().body(Map.of("error","입력 내용을 확인해주세요.")); }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate(Exception e) { return ResponseEntity.status(409).body(Map.of("error","이미 사용 중인 정보예요. 입력 내용을 확인해주세요.")); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> uploadSize(Exception e) { return ResponseEntity.badRequest().body(Map.of("error","사진은 5MB 이하로 올려주세요.")); }
}
