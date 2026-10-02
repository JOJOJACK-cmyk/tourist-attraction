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
@Service
public class SupportService {
    private final SupportOrderRepository orders;private final TransactionTemplate transactions;private final TossPaymentClient payments;
    public SupportService(SupportOrderRepository orders,PlatformTransactionManager manager,TossPaymentClient payments){this.orders=orders;this.payments=payments;transactions=new TransactionTemplate(manager);}
    public ConfigView config(){return new ConfigView(payments.available(),payments.clientKey(),payments.mode());}
    public OrderView create(CreateInput input,String owner){
        payments.requireAvailable();
        if(!Set.of(1000,3000,5000).contains(input.amount()))throw new ApiException(400,"준비된 금액을 선택해주세요.");
        try{return transactions.execute(status->{var existing=orders.findByOwnerKeyAndRequestKey(owner,input.requestId());if(existing.isPresent())return reuse(existing.get(),input.amount());var order=new SupportOrder(owner,input.requestId(),input.amount(),Instant.now());order.configure(payments.mode());return view(orders.saveAndFlush(order));});}
        catch(DataIntegrityViolationException concurrent){return orders.findByOwnerKeyAndRequestKey(owner,input.requestId()).map(order->reuse(order,input.amount())).orElseThrow(()->concurrent);}
    }
    @Transactional public OrderView detail(String id,String owner){var order=owned(id,owner);order.expire(Instant.now());return view(order);}
    public OrderView confirm(String id,ConfirmInput input,String owner){
        payments.requireAvailable();
        // Persist the attempt before calling the gateway. Unknown responses remain retryable.
        var before=transactions.execute(tx->{var order=owned(id,owner);order.expire(Instant.now());
            if(!payments.mode().equals(order.getPaymentMode()))throw new ApiException(409,"현재 결제 설정과 다른 주문이에요.");
            if(input.amount()!=order.getAmount())throw new ApiException(400,"주문 금액이 일치하지 않아요.");
            if(order.getPaymentKey()!=null&&!input.paymentKey().equals(order.getPaymentKey()))throw new ApiException(409,"다른 결제 요청으로 변경할 수 없어요.");
            if(order.getStatus()==Status.SUCCEEDED)return view(order);
            if(order.getStatus()!=Status.READY)throw new ApiException(409,"만료되었거나 종료된 주문이에요.");
            order.beginConfirmation(input.paymentKey());return view(order);});
        if(before.status()==Status.SUCCEEDED)return before;
        var result=payments.confirm(id,input.paymentKey(),before.amount());
        if(!"DONE".equals(result.path("status").asText())||!id.equals(result.path("orderId").asText())
            ||!input.paymentKey().equals(result.path("paymentKey").asText())||!result.path("totalAmount").isIntegralNumber()
            ||result.path("totalAmount").asLong()!=before.amount()||!"KRW".equals(result.path("currency").asText()))
            throw new ApiException(502,"결제사 승인 정보를 확인할 수 없어요. 다시 확인해주세요.");
        return transactions.execute(tx->{var order=owned(id,owner);if(order.getStatus()==Status.READY)order.finish(Status.SUCCEEDED,Instant.now());return view(order);});
    }
    private SupportOrder owned(String id,String owner){return orders.lock(id).filter(order->owner!=null&&owner.equals(order.getOwnerKey())).orElseThrow(()->new ApiException(404,"후원 내역을 확인할 수 없어요."));}
    private OrderView reuse(SupportOrder order,int amount){if(order.getAmount()!=amount||!payments.mode().equals(order.getPaymentMode()))throw new ApiException(409,"주문 정보가 변경되었어요. 새로 시작해주세요.");return view(order);}
    private OrderView view(SupportOrder order){return new OrderView(order.getId(),order.getAmount(),order.getStatus(),"KRW",order.getPaymentMode()==null?"DEMO":order.getPaymentMode(),order.isConfirming(),order.getCreatedAt(),order.getCompletedAt());}
}
