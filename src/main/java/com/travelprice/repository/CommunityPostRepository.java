package com.travelprice.repository;
import com.travelprice.domain.CommunityPost;
import org.springframework.data.jpa.repository.*;
public interface CommunityPostRepository extends JpaRepository<CommunityPost,Long>,JpaSpecificationExecutor<CommunityPost>{
    boolean existsByImageKeyAndHiddenFalse(String imageKey);
    boolean existsByImageKey(String imageKey);
}
