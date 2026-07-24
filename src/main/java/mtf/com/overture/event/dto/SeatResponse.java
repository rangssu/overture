package mtf.com.overture.event.dto;

import mtf.com.overture.event.Seat;

public record SeatResponse(Long seatId, Integer seatNumber, String status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getCol(), seat.getStatus().name());
    }
}
