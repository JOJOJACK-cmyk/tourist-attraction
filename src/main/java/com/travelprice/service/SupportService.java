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
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
@Service
public class SupportService {
    private final SupportOrderRepository orders;private final TransactionTemplate transactions;private final KakaoPaymentClient payments;
    public SupportService(SupportOrderRepository orders,PlatformTransactionManager manager,KakaoPaymentClient payments){this.orders=orders;this.payments=payments;transactions=new TransactionTemplate(manager);}
    public ConfigView config(){return new ConfigView(payments.available(),"KAKAOPAY",payments.mode());}
    public OrderView create(CreateInput input,String owner){
        payments.requireAvailable();
        if(!Set.of(1000,3000,5000).contains(input.amount()))throw new ApiException(400,"준비된 금액을 선택해주세요.");
        try{return transactions.execute(status->{var existing=orders.findByOwnerKeyAndRequestKey(owner,input.requestId());if(existing.isPresent())return reuse(existing.get(),input.amount());var order=new SupportOrder(owner,input.requestId(),input.amount(),Instant.now());order.configureKakao(payments.mode(),payments.cid());return view(orders.saveAndFlush(order));});}
        catch(DataIntegrityViolationException concurrent){return orders.findByOwnerKeyAndRequestKey(owner,input.requestId()).map(order->reuse(order,input.amount())).orElseThrow(()->concurrent);}
    }
    @Transactional public OrderView detail(String id,String owner){var order=owned(id,owner);order.expire(Instant.now());return view(order);}
    @Transactional public ReadyView ready(String id,ReadyInput input,String owner){
        payments.requireAvailable();var order=owned(id,owner);checkConfig(order);order.expire(Instant.now());
        if(order.getStatus()!=Status.READY||order.isConfirming())throw new ApiException(409,"이미 진행되었거나 만료된 주문이에요.");
        // Serialize preparation so a double click never replaces the stored TID.
        if(order.getPaymentKey()==null){var result=payments.ready(order.getId(),order.getOwnerKey(),order.getAmount());
            if(!KakaoPaymentClient.validRedirect(result.pcUrl())||!KakaoPaymentClient.validRedirect(result.mobileUrl()))throw new ApiException(502,"결제 페이지를 확인할 수 없어요.");
            order.ready(result.tid(),result.pcUrl(),result.mobileUrl());}
        return new ReadyView(id,input.mobile()?order.getRedirectMobileUrl():order.getRedirectPcUrl(),order.getPaymentMode());
    }
    public OrderView confirm(String id,ConfirmInput input,String owner){
        payments.requireAvailable();String tokenHash=hash(input.pgToken());
        var before=transactions.execute(tx->{var order=owned(id,owner);checkConfig(order);order.expire(Instant.now());
            if(order.getPaymentKey()==null)throw new ApiException(409,"결제 준비를 먼저 진행해주세요.");
            if(order.getApprovalTokenHash()!=null&&!tokenHash.equals(order.getApprovalTokenHash()))throw new ApiException(409,"다른 승인 요청으로 변경할 수 없어요.");
            if(order.getStatus()==Status.SUCCEEDED)return view(order);
            if(order.getStatus()!=Status.READY)throw new ApiException(409,"만료되었거나 종료된 주문이에요.");
            order.beginConfirmation(tokenHash);return view(order);});
        if(before.status()==Status.SUCCEEDED)return before;
        // Keep the row lock during approval: concurrent callbacks cannot submit two approvals.
        // The previously persisted hash/attempt survives a timeout or a failed database commit.
        return transactions.execute(tx->{var order=owned(id,owner);checkConfig(order);
            if(order.getStatus()==Status.SUCCEEDED)return view(order);
            var result=payments.lookup(order.getPaymentKey());
            if(!result.approved())result=payments.approve(id,order.getOwnerKey(),order.getPaymentKey(),input.pgToken(),order.getAmount());
            verified(order,result);order.finish(Status.SUCCEEDED,Instant.now());return view(order);});
    }
    @Transactional public OrderView reconcile(String id,String owner){
        payments.requireAvailable();var order=owned(id,owner);checkConfig(order);
        if(order.getStatus()==Status.SUCCEEDED)return view(order);
        if(!order.isConfirming()||order.getPaymentKey()==null)throw new ApiException(409,"승인 확인 중인 주문이 아니에요.");
        var result=payments.lookup(order.getPaymentKey());verified(order,result);order.finish(Status.SUCCEEDED,Instant.now());return view(order);
    }
    private void verified(SupportOrder order,KakaoPaymentClient.Approval result){
        if(!result.approved()||!order.getId().equals(result.orderId())||!order.getPaymentKey().equals(result.tid())
            ||!order.getMerchantCid().equals(result.cid())||!order.getOwnerKey().equals(result.userId())||order.getAmount()!=result.amount())
            throw new ApiException(502,"결제사 승인 정보를 확인할 수 없어요. 새 결제를 시작하지 말고 다시 확인해주세요.");
    }
    private void checkConfig(SupportOrder order){if(!"KAKAOPAY".equals(order.getPaymentProvider())||!payments.mode().equals(order.getPaymentMode())||!payments.cid().equals(order.getMerchantCid()))throw new ApiException(409,"현재 카카오페이 설정과 다른 주문이에요. 새 후원을 시작해주세요.");}
    private SupportOrder owned(String id,String owner){return orders.lock(id).filter(order->owner!=null&&owner.equals(order.getOwnerKey())).orElseThrow(()->new ApiException(404,"후원 내역을 확인할 수 없어요."));}
    private OrderView reuse(SupportOrder order,int amount){checkConfig(order);if(order.getAmount()!=amount)throw new ApiException(409,"금액이 변경되었어요. 새로 시작해주세요.");return view(order);}
    private OrderView view(SupportOrder order){return new OrderView(order.getId(),order.getAmount(),order.getStatus(),"KRW",order.getPaymentMode()==null?"DEMO":order.getPaymentMode(),order.getPaymentProvider()==null?"LEGACY":order.getPaymentProvider(),order.isConfirming(),order.getCreatedAt(),order.getCompletedAt());}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
