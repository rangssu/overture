package mtf.com.overture.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SeatGradeRepositoryTest {

    @Autowired
    private SeatGradeRepository seatGradeRepository;

    private SeatGrade buildGrade(Long eventId, String name) {
        return SeatGrade.builder()
                .eventId(eventId)
                .name(name)
                .price(150000)
                .totalCount(50)
                .remainCount(50)
                .build();
    }

    @Test
    void save_persists_and_reloads_all_fields() {
        SeatGrade saved = seatGradeRepository.save(buildGrade(1L, "VIP"));

        SeatGrade found = seatGradeRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getEventId()).isEqualTo(1L);
        assertThat(found.getName()).isEqualTo("VIP");
        assertThat(found.getTotalCount()).isEqualTo(50);
        assertThat(found.getRemainCount()).isEqualTo(50);
    }

    @Test
    void findByEventId_returns_only_grades_for_that_event() {
        seatGradeRepository.save(buildGrade(1L, "VIP"));
        seatGradeRepository.save(buildGrade(1L, "R"));
        seatGradeRepository.save(buildGrade(2L, "VIP"));

        List<SeatGrade> grades = seatGradeRepository.findByEventId(1L);

        assertThat(grades).hasSize(2);
        assertThat(grades).allMatch(grade -> grade.getEventId().equals(1L));
    }

    @Test
    void update_changes_name_and_price_only() {
        SeatGrade grade = seatGradeRepository.saveAndFlush(buildGrade(1L, "VIP"));
        Integer originalTotalCount = grade.getTotalCount();

        grade.update("VVIP", 200000);

        assertThat(grade.getName()).isEqualTo("VVIP");
        assertThat(grade.getPrice()).isEqualTo(200000);
        assertThat(grade.getTotalCount()).isEqualTo(originalTotalCount);
    }

    @Test
    void update_with_null_price_keeps_the_existing_price() {
        SeatGrade grade = seatGradeRepository.saveAndFlush(buildGrade(1L, "VIP"));

        grade.update(null, 180000);

        assertThat(grade.getName()).isEqualTo("VIP");
        assertThat(grade.getPrice()).isEqualTo(180000);
    }
}
