package mtf.com.overture.event;

import jakarta.validation.Valid;
import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.EventUpdateRequest;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import mtf.com.overture.event.dto.SeatGradeResponse;
import mtf.com.overture.event.dto.SeatGradeUpdateRequest;
import mtf.com.overture.event.dto.SeatResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public EventResponse create(Authentication authentication, @AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody EventCreateRequest request) {
        return eventService.createEvent(authentication, userId, request);
    }

    @GetMapping
    public Page<EventResponse> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return eventService.listEvents(pageable);
    }

    @GetMapping("/{id}")
    public EventResponse detail(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return eventService.getEvent(id, userId);
    }

    @PatchMapping("/{id}")
    public EventResponse update(Authentication authentication, @AuthenticationPrincipal Long userId,
                                 @PathVariable Long id, @Valid @RequestBody EventUpdateRequest request) {
        return eventService.updateEvent(authentication, userId, id, request);
    }

    @PostMapping("/{id}/grades")
    public SeatGradeResponse addGrade(Authentication authentication, @AuthenticationPrincipal Long userId,
                                       @PathVariable Long id, @Valid @RequestBody SeatGradeCreateRequest request) {
        return eventService.addGrade(authentication, userId, id, request);
    }

    @GetMapping("/{id}/grades")
    public List<SeatGradeResponse> listGrades(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return eventService.listGrades(id, userId);
    }

    @PatchMapping("/{id}/grades/{gradeId}")
    public SeatGradeResponse updateGrade(Authentication authentication, @AuthenticationPrincipal Long userId,
                                          @PathVariable Long id, @PathVariable Long gradeId,
                                          @Valid @RequestBody SeatGradeUpdateRequest request) {
        return eventService.updateGrade(authentication, userId, id, gradeId, request);
    }

    @GetMapping("/{id}/seats")
    public Map<String, List<SeatResponse>> seats(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return eventService.getSeats(id, userId);
    }
}
