package mtf.com.overture.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String venue;

    private String description;

    private String posterUrl;

    @Column(nullable = false)
    private LocalDateTime saleStartAt;

    @Column(nullable = false)
    private LocalDateTime saleEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    // User를 JPA 연관관계로 참조하지 않는다 - 모듈 간 직접 Repository 접근 금지 원칙(설계 문서 MSA 전환 대비 원칙).
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Event(String title, String venue, String description, String posterUrl,
                 LocalDateTime saleStartAt, LocalDateTime saleEndAt, EventStatus status,
                 Long createdBy, LocalDateTime createdAt) {
        this.title = title;
        this.venue = venue;
        this.description = description;
        this.posterUrl = posterUrl;
        this.saleStartAt = saleStartAt;
        this.saleEndAt = saleEndAt;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void update(String title, String venue, String description, String posterUrl,
                        LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
        if (title != null) this.title = title;
        if (venue != null) this.venue = venue;
        if (description != null) this.description = description;
        if (posterUrl != null) this.posterUrl = posterUrl;
        if (saleStartAt != null) this.saleStartAt = saleStartAt;
        if (saleEndAt != null) this.saleEndAt = saleEndAt;
    }

    public void publish() {
        this.status = EventStatus.PUBLISHED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.createdBy.equals(userId);
    }

    public boolean isDraft() {
        return this.status == EventStatus.DRAFT;
    }
}
