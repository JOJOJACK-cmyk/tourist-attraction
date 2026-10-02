package com.travelprice.api;
import com.travelprice.domain.CommunityPost.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class CommunityModels {
    private CommunityModels(){}
    public record PostInput(@Size(max=60) String destinationId,@NotNull Kind kind,@NotNull Category category,
        @NotBlank @Size(min=2,max=120) String title,@NotBlank @Size(min=5,max=10000) String body,
        LocalDate visitedAt,@Pattern(regexp="[a-f0-9-]{36}") String imageKey){}
    public record CommentInput(@NotBlank @Size(max=2000) String body){}
    public record VisibilityInput(boolean hidden){}
    public record PostView(Long id,String destinationId,String destination,Kind kind,Category category,
        String title,String body,LocalDate visitedAt,String imageUrl,String author,boolean mine,boolean hidden,
        boolean admin,LocalDateTime createdAt,LocalDateTime updatedAt){}
    public record PostPage(List<PostView> content,int page,int totalPages,long totalElements){}
    public record CommentView(Long id,String body,String author,boolean canDelete,LocalDateTime createdAt){}
}
