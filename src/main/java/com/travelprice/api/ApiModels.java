package com.travelprice.api;

import jakarta.validation.constraints.*;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}
    public record DestinationDto(String slug,String name,String region,String categories) {}
    public record AnalysisRequest(@NotBlank String destinationId,@Min(2020) @Max(2100) int year,@Pattern(regexp="sample|live") @NotNull String mode) {}
    public record Source(long id,String title,String type,String date,String url) {}
    public record Price(String item,String unit,long amount,long sourceId,String date) {}
    public record Finding(String text,List<Long> sourceIds) {}
    public record AnalysisResult(String kind,String destinationId,int year,String title,String summary,String confidence,
                                 List<Price> prices,List<Finding> findings,List<Source> sources,String limitation,String reviewedAt,String searchSuggestions) {}
}
