package com.travelprice.api;
import com.travelprice.domain.VisitReview.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class VisitReviewModels {
    private VisitReviewModels(){}
    public record Input(@NotBlank @Size(max=40) String destinationId,@NotNull LocalDate visitedAt,
        @NotBlank @Size(min=2,max=120) String title,@Size(max=120) String item,
        @Min(0) @Max(100000000) Long spentWon,@Min(0) @Max(1440) Integer waitingMinutes,
        @NotNull Feeling foodCost,@NotNull Feeling lodgingCost,@NotNull Feeling service,@NotNull Feeling crowding,
        @Size(max=2000) String goodPoints,@Size(max=2000) String badPoints,
        @Pattern(regexp="[a-f0-9-]{36}") String imageKey,@AssertTrue boolean firsthand, @PositiveOrZero Long version){}
    public record Decision(@NotNull Status status,@Size(max=500) String note,@NotNull @PositiveOrZero Long version){}
    public record View(Long id,Long version,String destinationId,String destination,LocalDate visitedAt,String title,String item,
        Long spentWon,Integer waitingMinutes,Feeling foodCost,Feeling lodgingCost,Feeling service,Feeling crowding,
        String goodPoints,String badPoints,String imageUrl,String author,boolean mine,Status status,String reviewNote,
        LocalDateTime createdAt,LocalDateTime updatedAt,LocalDateTime reviewedAt){}
    public record ReviewPage(List<View> content,int page,int totalPages,long totalElements){}
    public record Aspect(String key,String label,long total,long good,long bad,long neutral){}
    public record Summary(String destinationId,Integer year,long total,long praiseCount,long complaintCount,List<Aspect> aspects){}
}
