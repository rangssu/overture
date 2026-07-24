package mtf.com.overture.queue.dto;

public record QueueStatusResponse(long position, long totalWaiting, boolean admitted) {
}
