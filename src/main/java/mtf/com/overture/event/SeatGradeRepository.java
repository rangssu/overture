package mtf.com.overture.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatGradeRepository extends JpaRepository<SeatGrade, Long> {
    List<SeatGrade> findByEventId(Long eventId);
}
