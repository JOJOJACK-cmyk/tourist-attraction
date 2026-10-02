package com.travelprice.repository;
import com.travelprice.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MemberRepository extends JpaRepository<Member,Long>{Optional<Member> findByLoginId(String loginId);boolean existsByLoginId(String loginId);Optional<Member> findBySocialProviderAndSocialSubject(String provider,String subject);}
