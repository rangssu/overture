package mtf.com.overture.event.dto;

import mtf.com.overture.event.SeatGrade;

public record SeatGradeResponse(Long id, Long eventId, String name, Integer price,
                                 Integer totalCount, Integer remainCount) {

    public static SeatGradeResponse from(SeatGrade grade) {
        return new SeatGradeResponse(grade.getId(), grade.getEventId(), grade.getName(),
                grade.getPrice(), grade.getTotalCount(), grade.getRemainCount());
    }
}
