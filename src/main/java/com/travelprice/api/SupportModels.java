package com.travelprice.api;
import com.travelprice.domain.SupportOrder.Status;
import jakarta.validation.constraints.*;
import java.time.Instant;
public final class SupportModels {
    private SupportModels(){}
    public enum Outcome { SUCCESS, CANCEL, FAIL }
    public record CreateInput(@NotNull Integer amount,@NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")String requestId){}
    public record ResultInput(@NotNull Outcome outcome){}
    public record OrderView(String id,int amount,Status status,String currency,String mode,Instant createdAt,Instant completedAt){}
}
