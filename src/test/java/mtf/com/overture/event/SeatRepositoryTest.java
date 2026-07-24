package mtf.com.overture.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SeatRepositoryTest {

    @Autowired
    private SeatRepository seatRepository;

    private Seat buildSeat(Long gradeId, Integer col) {
        return Seat.builder()
                .gradeId(gradeId)
                .row(1)
                .col(col)
                .status(SeatStatus.AVAILABLE)
                .build();
    }

    @Test
    void save_persists_and_reloads_all_fields() {
        Seat saved = seatRepository.save(buildSeat(1L, 1));

        Seat found = seatRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getGradeId()).isEqualTo(1L);
        assertThat(found.getRow()).isEqualTo(1);
        assertThat(found.getCol()).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void findByGradeId_returns_only_seats_for_that_grade() {
        seatRepository.save(buildSeat(1L, 1));
        seatRepository.save(buildSeat(1L, 2));
        seatRepository.save(buildSeat(2L, 1));

        List<Seat> seats = seatRepository.findByGradeId(1L);

        assertThat(seats).hasSize(2);
        assertThat(seats).allMatch(seat -> seat.getGradeId().equals(1L));
    }

    @Test
    void findByGradeIdIn_returns_seats_for_all_given_grades() {
        seatRepository.save(buildSeat(1L, 1));
        seatRepository.save(buildSeat(2L, 1));
        seatRepository.save(buildSeat(3L, 1));

        List<Seat> seats = seatRepository.findByGradeIdIn(List.of(1L, 2L));

        assertThat(seats).hasSize(2);
    }
}
