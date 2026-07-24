package mtf.com.overture.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seat_grades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    // 예매 확정 시 감소한다 - module-booking(팀원 B)이 나중에 EventService를 통해서만 갱신한다
    // (모듈 간 직접 Repository 접근 금지). 이번 스코프에서는 생성 시 totalCount로 초기화만 한다.
    @Column(name = "remain_count", nullable = false)
    private Integer remainCount;

    @Version
    private Long version;

    @Builder
    public SeatGrade(Long eventId, String name, Integer price, Integer totalCount, Integer remainCount) {
        this.eventId = eventId;
        this.name = name;
        this.price = price;
        this.totalCount = totalCount;
        this.remainCount = remainCount;
    }

    public void update(String name, Integer price) {
        if (name != null) this.name = name;
        if (price != null) this.price = price;
    }
}
