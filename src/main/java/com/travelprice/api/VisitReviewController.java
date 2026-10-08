package com.travelprice.api;
import com.travelprice.api.VisitReviewModels.*;
import com.travelprice.domain.VisitReview.Status;
import com.travelprice.service.VisitReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class VisitReviewController {
    private final VisitReviewService reviews;
    public VisitReviewController(VisitReviewService reviews){this.reviews=reviews;}
    @GetMapping("/api/visit-reviews") public ReviewPage list(@RequestParam(required=false)String destination,@RequestParam(required=false)Integer year,@RequestParam(defaultValue="0")int page,Authentication auth){return reviews.list(destination,year,page,auth);}
    @GetMapping("/api/visit-reviews/summary") public Summary summary(@RequestParam String destination,@RequestParam(required=false)Integer year){return reviews.summary(destination,year);}
    @GetMapping("/api/visit-reviews/mine") public ReviewPage mine(@RequestParam(required=false)String destination,@RequestParam(defaultValue="0")int page,Authentication auth){return reviews.mine(destination,page,auth);}
    @GetMapping("/api/visit-reviews/{id}") public View detail(@PathVariable Long id,Authentication auth){return reviews.detail(id,auth);}
    @PostMapping("/api/visit-reviews") @ResponseStatus(HttpStatus.CREATED) public View create(@Valid @RequestBody Input input,Authentication auth){return reviews.create(input,auth);}
    @PutMapping("/api/visit-reviews/{id}") public View update(@PathVariable Long id,@Valid @RequestBody Input input,Authentication auth){return reviews.update(id,input,auth);}
    @DeleteMapping("/api/visit-reviews/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,@RequestParam Long version,Authentication auth){reviews.delete(id,version,auth);}
    @GetMapping("/api/admin/visit-reviews") public ReviewPage queue(@RequestParam(required=false)String destination,@RequestParam(defaultValue="PENDING")Status status,@RequestParam(defaultValue="0")int page,Authentication auth){return reviews.queue(destination,status,page,auth);}
    @PatchMapping("/api/admin/visit-reviews/{id}") public View moderate(@PathVariable Long id,@Valid @RequestBody Decision input,Authentication auth){return reviews.moderate(id,input,auth);}
}
