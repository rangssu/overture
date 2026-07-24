package mtf.com.overture.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByGradeId(Long gradeId);

    List<Seat> findByGradeIdIn(List<Long> gradeIds);
}
