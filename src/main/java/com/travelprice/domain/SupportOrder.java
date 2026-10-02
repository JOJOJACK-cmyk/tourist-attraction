package com.travelprice.domain;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="support_demo_order",uniqueConstraints=@UniqueConstraint(name="uk_support_owner_request",columnNames={"owner_key","request_key"}))
public class SupportOrder {
    public enum Status { READY, SUCCEEDED, CANCELLED, FAILED, EXPIRED }
    @Id @Column(length=36) private String id;
    @Column(name="owner_key",nullable=false,length=36) private String ownerKey;
    @Column(name="request_key",nullable=false,length=36) private String requestKey;
    @Column(nullable=false) private int amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private Status status;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant expiresAt;
    private Instant completedAt;
    @Column(length=8) private String paymentMode;
    @Column(length=200) private String paymentKey;
    private Instant confirmationStartedAt;
    public String getPaymentMode(){return paymentMode;}
    public String getPaymentKey(){return paymentKey;}
    public boolean isConfirming(){return confirmationStartedAt!=null&&status==Status.READY;}
    public void configure(String mode){paymentMode=mode;}
    @Column(length=16) private String paymentProvider;
    @Column(length=20) private String merchantCid;
    @Column(length=64) private String approvalTokenHash;
    @Column(length=2048) private String redirectPcUrl;
    @Column(length=2048) private String redirectMobileUrl;
    public String getPaymentProvider(){return paymentProvider;}
    public String getMerchantCid(){return merchantCid;}
    public String getApprovalTokenHash(){return approvalTokenHash;}
    public String getRedirectPcUrl(){return redirectPcUrl;}
    public String getRedirectMobileUrl(){return redirectMobileUrl;}
    public void configureKakao(String mode,String cid){configure(mode);paymentProvider="KAKAOPAY";merchantCid=cid;}
    public void ready(String tid,String pc,String mobile){paymentKey=tid;redirectPcUrl=pc;redirectMobileUrl=mobile;}
    public void beginConfirmation(String hash){approvalTokenHash=hash;confirmationStartedAt=Instant.now();}
    protected SupportOrder(){}
    public SupportOrder(String owner,String request,int amount,Instant now){id=UUID.randomUUID().toString();ownerKey=owner;requestKey=request;this.amount=amount;status=Status.READY;createdAt=now;expiresAt=now.plus(Duration.ofMinutes(20));}
    public String getId(){return id;}public String getOwnerKey(){return ownerKey;}public int getAmount(){return amount;}public Status getStatus(){return status;}public Instant getCreatedAt(){return createdAt;}public Instant getCompletedAt(){return completedAt;}
    public void expire(Instant now){if(status==Status.READY&&confirmationStartedAt==null&&!now.isBefore(expiresAt)){status=Status.EXPIRED;completedAt=now;}}
    public void finish(Status status,Instant now){this.status=status;completedAt=now;}
}
