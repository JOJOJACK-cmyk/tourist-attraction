package com.travelprice.api;
import com.travelprice.domain.SupportOrder.Status;
import jakarta.validation.constraints.*;
import java.time.Instant;
public final class SupportModels {
    private SupportModels(){}
    public record CreateInput(@NotNull Integer amount,@NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")String requestId){}
    public record ConfirmInput(@NotBlank @Size(max=1000) String pgToken){}
    public record ReadyInput(boolean mobile){}
    public record ReadyView(String orderId,String redirectUrl,String mode){}
    public record OrderView(String id,int amount,Status status,String currency,String mode,String provider,boolean confirming,Instant createdAt,Instant completedAt){}
    public record ConfigView(boolean available,String provider,String mode){}
}
