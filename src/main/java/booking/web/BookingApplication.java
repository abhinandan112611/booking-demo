package booking.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ADDS web application entry point.
 * Component scanning starts at package booking.web, so the engine package
 * (booking: Db, BookingService, scenarios) is NOT turned into Spring beans
 * and keeps running exactly as before with exec:java.
 */
@SpringBootApplication
public class BookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
