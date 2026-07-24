package mtf.com.overture.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade_id", nullable = false)
    private Long gradeId;

    // 실제 공연장 배치도가 아니다 - 이번 스코프는 row=1 고정, col이 1..totalCount 순번을 나타낸다.
    // 컬럼명은 "seat_row"로 매핑한다 - MySQL 8.0.2+에서 ROW가 예약어라 그대로 쓰면 DDL이 깨진다.
    @Column(name = "seat_row", nullable = false)
    private Integer row;

    @Column(name = "col", nullable = false)
    private Integer col;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Builder
    public Seat(Long gradeId, Integer row, Integer col, SeatStatus status) {
        this.gradeId = gradeId;
        this.row = row;
        this.col = col;
        this.status = status;
    }
}
