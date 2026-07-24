package mtf.com.overture.event.dto;

import mtf.com.overture.event.SeatGrade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeatGradeResponseTest {

    @Test
    void from_maps_all_fields_from_the_seat_grade_entity() {
        SeatGrade grade = SeatGrade.builder()
                .eventId(1L)
                .name("VIP")
                .price(150000)
                .totalCount(50)
                .remainCount(50)
                .build();

        SeatGradeResponse response = SeatGradeResponse.from(grade);

        assertThat(response.id()).isNull();
        assertThat(response.eventId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("VIP");
        assertThat(response.price()).isEqualTo(150000);
        assertThat(response.totalCount()).isEqualTo(50);
        assertThat(response.remainCount()).isEqualTo(50);
    }
}
