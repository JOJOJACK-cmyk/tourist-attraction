package com.travelprice.repository;
import com.travelprice.domain.VisitReview;
import org.springframework.data.jpa.repository.*;

public interface VisitReviewRepository extends JpaRepository<VisitReview,Long>,JpaSpecificationExecutor<VisitReview> {
    boolean existsByImageKey(String key);
    boolean existsByImageKeyAndStatus(String key,VisitReview.Status status);
}
