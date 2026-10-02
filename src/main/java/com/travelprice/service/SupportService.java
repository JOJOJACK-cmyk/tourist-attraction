package com.travelprice.service;
import com.travelprice.api.*;
import com.travelprice.api.SupportModels.*;
import com.travelprice.domain.SupportOrder;
import com.travelprice.domain.SupportOrder.Status;
import com.travelprice.repository.SupportOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.Set;
/** Portfolio demo only. There is deliberately no payment gateway or money transfer. */
@Service
public class SupportService {
    private final SupportOrderRepository orders;private final TransactionTemplate transactions;
    public SupportService(SupportOrderRepository orders,PlatformTransactionManager manager){this.orders=orders;transactions=new TransactionTemplate(manager);}
    public OrderView create(CreateInput input,String owner){
        if(!Set.of(1000,3000,5000).contains(input.amount()))throw new ApiException(400,"준비된 테스트 금액을 선택해주세요.");
        try{return transactions.execute(status->{var existing=orders.findByOwnerKeyAndRequestKey(owner,input.requestId());if(existing.isPresent())return reuse(existing.get(),input.amount());return view(orders.saveAndFlush(new SupportOrder(owner,input.requestId(),input.amount(),Instant.now())));});}
        catch(DataIntegrityViolationException concurrent){return orders.findByOwnerKeyAndRequestKey(owner,input.requestId()).map(order->reuse(order,input.amount())).orElseThrow(()->concurrent);}
    }
    @Transactional public OrderView detail(String id,String owner){var order=owned(id,owner);order.expire(Instant.now());return view(order);}
    @Transactional public OrderView result(String id,ResultInput input,String owner){
        var order=owned(id,owner);order.expire(Instant.now());
        var next=switch(input.outcome()){case SUCCESS->Status.SUCCEEDED;case CANCEL->Status.CANCELLED;case FAIL->Status.FAILED;};
        if(order.getStatus()==next)return view(order);
        if(order.getStatus()!=Status.READY)throw new ApiException(409,"이미 종료되었거나 만료된 모의 후원이에요.");
        order.finish(next,Instant.now());return view(order);
    }
    private SupportOrder owned(String id,String owner){return orders.lock(id).filter(order->owner!=null&&owner.equals(order.getOwnerKey())).orElseThrow(()->new ApiException(404,"모의 후원 내역을 확인할 수 없어요."));}
    private OrderView reuse(SupportOrder order,int amount){if(order.getAmount()!=amount)throw new ApiException(409,"금액이 변경되었어요. 새로 시작해주세요.");return view(order);}
    private OrderView view(SupportOrder order){return new OrderView(order.getId(),order.getAmount(),order.getStatus(),"KRW","DEMO",order.getCreatedAt(),order.getCompletedAt());}
}
