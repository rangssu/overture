package mtf.com.overture.event.dto;

import mtf.com.overture.event.Seat;
import mtf.com.overture.event.SeatStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeatResponseTest {

    @Test
    void from_maps_col_as_the_seat_number() {
        Seat seat = Seat.builder()
                .gradeId(1L)
                .row(1)
                .col(3)
                .status(SeatStatus.AVAILABLE)
                .build();

        SeatResponse response = SeatResponse.from(seat);

        assertThat(response.seatId()).isNull();
        assertThat(response.seatNumber()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("AVAILABLE");
    }
}
