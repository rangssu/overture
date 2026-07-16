package mtf.com.overture.queue;

import mtf.com.overture.queue.dto.QueueStatusResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/{eventId}/enter")
    public QueueStatusResponse enter(@PathVariable Long eventId, @AuthenticationPrincipal Long userId) {
        return queueService.enter(eventId, userId);
    }

    @GetMapping("/{eventId}/status")
    public QueueStatusResponse status(@PathVariable Long eventId, @AuthenticationPrincipal Long userId) {
        return queueService.getStatus(eventId, userId);
    }

    @DeleteMapping("/{eventId}")
    public void leave(@PathVariable Long eventId, @AuthenticationPrincipal Long userId) {
        queueService.leave(eventId, userId);
    }
}
