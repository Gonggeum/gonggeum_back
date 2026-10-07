package com.gonggeumi.auth;

import com.gonggeumi.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PublicAuthController {
    private final TermService terms;
    private final AvailabilityService availability;
    private final PublicAuthRateLimiter limiter;

    public PublicAuthController(TermService terms, AvailabilityService availability, PublicAuthRateLimiter limiter) {
        this.terms = terms;
        this.availability = availability;
        this.limiter = limiter;
    }

    @GetMapping("/terms")
    public ApiResponse<TermService.TermPage> terms(@RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "20") int limit,
                                                 HttpServletRequest request) {
        limiter.check("terms", request.getRemoteAddr());
        return ApiResponse.of(terms.current(cursor, limit), request);
    }

    @PostMapping("/auth/availability")
    public ApiResponse<AvailabilityService.Availability> availability(@Valid @RequestBody AvailabilityInput input,
                                                                    HttpServletRequest request) {
        limiter.check("availability", request.getRemoteAddr());
        return ApiResponse.of(availability.check(input.field(), input.value()), request);
    }

    public record AvailabilityInput(
            @NotBlank @Pattern(regexp = "login_id|email") String field,
            @NotBlank @Size(max = 254) String value) {
        public AvailabilityInput { if (value != null) value = value.strip(); }
    }
}
