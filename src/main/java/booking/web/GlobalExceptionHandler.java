package booking.web;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to a small JSON body {status, error, message, requestId}.
 * No stack trace ever reaches the client; unexpected errors are logged server-side with the request id.
 * Unknown database errors are reported as real failures (500), never as "seat taken".
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(int status, String error, String message, String requestId) {}

    private static ResponseEntity<ErrorBody> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorBody(status.value(), error, message, MDC.get(RequestIdFilter.MDC_KEY)));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
                       MethodArgumentTypeMismatchException.class,
                       HttpMessageNotReadableException.class,
                       IllegalArgumentException.class})
    public ResponseEntity<ErrorBody> badRequest(Exception e) {
        log.info("event=VALIDATION_FAILED type={}", e.getClass().getSimpleName());
        return body(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is missing a value or has a wrong value.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> notFound(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such page or endpoint.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "This method is not allowed here.");
    }

    /** Pool exhausted: no connection within the bounded connection-timeout (3000 ms). */
    @ExceptionHandler(SQLTransientConnectionException.class)
    public ResponseEntity<ErrorBody> poolTimeout(SQLTransientConnectionException e) {
        log.warn("event=POOL_TIMEOUT");
        return body(HttpStatus.SERVICE_UNAVAILABLE, "BUSY", "The system is busy. Please try again.");
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorBody> sql(SQLException e) {
        String state = e.getSQLState();
        if ("55P03".equals(state) || "40P01".equals(state) || "57014".equals(state) || "53300".equals(state)) {
            log.warn("event=DB_BUSY sqlstate={}", state);
            return body(HttpStatus.SERVICE_UNAVAILABLE, "BUSY", "The system is busy. Please try again.");
        }
        log.error("event=DB_ERROR sqlstate={}", state, e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error. Quote the request id.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unexpected(Exception e) {
        log.error("event=UNEXPECTED_ERROR type={}", e.getClass().getSimpleName(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error. Quote the request id.");
    }
}
