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
    protected SupportOrder(){}
    public SupportOrder(String owner,String request,int amount,Instant now){id=UUID.randomUUID().toString();ownerKey=owner;requestKey=request;this.amount=amount;status=Status.READY;createdAt=now;expiresAt=now.plus(Duration.ofMinutes(20));}
    public String getId(){return id;}public String getOwnerKey(){return ownerKey;}public int getAmount(){return amount;}public Status getStatus(){return status;}public Instant getCreatedAt(){return createdAt;}public Instant getCompletedAt(){return completedAt;}
    public void expire(Instant now){if(status==Status.READY&&!now.isBefore(expiresAt)){status=Status.EXPIRED;completedAt=now;}}
    public void finish(Status status,Instant now){this.status=status;completedAt=now;}
}
