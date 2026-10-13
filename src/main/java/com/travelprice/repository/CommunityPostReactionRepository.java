package com.travelprice.repository;

import com.travelprice.domain.CommunityPostReaction;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CommunityPostReactionRepository extends JpaRepository<CommunityPostReaction,Long> {
    Optional<CommunityPostReaction> findByPostIdAndMemberId(Long postId,Long memberId);
    List<CommunityPostReaction> findByMemberIdAndPostIdIn(Long memberId,Collection<Long> ids);
    long countByPostIdAndHelpfulTrue(Long postId);
    void deleteByPostId(Long postId);
    interface HelpfulCount {Long getPostId();long getHelpfulCount();}
    @Query("select r.post.id as postId, count(r) as helpfulCount from CommunityPostReaction r where r.helpful=true and r.post.id in :ids group by r.post.id")
    List<HelpfulCount> helpfulCounts(@Param("ids") Collection<Long> ids);
}
