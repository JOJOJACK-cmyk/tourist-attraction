package com.travelprice.repository;
import com.travelprice.domain.SupportOrder;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface SupportOrderRepository extends JpaRepository<SupportOrder,String>{
    Optional<SupportOrder> findByOwnerKeyAndRequestKey(String owner,String request);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from SupportOrder o where o.id=:id")Optional<SupportOrder> lock(@Param("id")String id);
}
