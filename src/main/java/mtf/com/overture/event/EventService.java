package mtf.com.overture.event;

import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.EventUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventCache eventCache;

    public EventService(EventRepository eventRepository, EventCache eventCache) {
        this.eventRepository = eventRepository;
        this.eventCache = eventCache;
    }

    @Transactional
    public EventResponse createEvent(Authentication authentication, Long userId, EventCreateRequest request) {
        requireOrganizerOrAdmin(authentication);
        validateSalePeriod(request.saleStartAt(), request.saleEndAt());

        Event event = Event.builder()
                .title(request.title())
                .venue(request.venue())
                .description(request.description())
                .posterUrl(request.posterUrl())
                .saleStartAt(request.saleStartAt())
                .saleEndAt(request.saleEndAt())
                .status(EventStatus.DRAFT)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();

        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse updateEvent(Authentication authentication, Long userId, Long eventId, EventUpdateRequest request) {
        Event event = findEvent(eventId);
        requireOwnerOrAdmin(authentication, event, userId);

        LocalDateTime newSaleStartAt = request.saleStartAt() != null ? request.saleStartAt() : event.getSaleStartAt();
        LocalDateTime newSaleEndAt = request.saleEndAt() != null ? request.saleEndAt() : event.getSaleEndAt();
        validateSalePeriod(newSaleStartAt, newSaleEndAt);

        event.update(request.title(), request.venue(), request.description(), request.posterUrl(),
                request.saleStartAt(), request.saleEndAt());

        eventCache.evictEvent(eventId);

        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId, Long viewerId) {
        Optional<EventResponse> cached = eventCache.getEvent(eventId);
        if (cached.isPresent()) {
            EventResponse response = cached.get();
            assertVisible(response.status(), response.createdBy(), viewerId);
            return response;
        }

        Event event = findEvent(eventId);
        assertVisible(event.getStatus().name(), event.getCreatedBy(), viewerId);

        EventResponse response = EventResponse.from(event);
        eventCache.putEvent(eventId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listEvents(Pageable pageable) {
        return eventRepository.findByStatus(EventStatus.PUBLISHED, pageable)
                .map(EventResponse::from);
    }

    Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.NOT_FOUND));
    }

    void assertVisible(String status, Long createdBy, Long viewerId) {
        boolean isDraft = EventStatus.DRAFT.name().equals(status);
        boolean isOwner = viewerId != null && viewerId.equals(createdBy);
        if (isDraft && !isOwner) {
            throw new EventException(EventErrorCode.NOT_FOUND);
        }
    }

    void requireOrganizerOrAdmin(Authentication authentication) {
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ORGANIZER") || a.equals("ROLE_ADMIN"));
        if (!allowed) {
            throw new EventException(EventErrorCode.FORBIDDEN);
        }
    }

    void requireOwnerOrAdmin(Authentication authentication, Event event, Long userId) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        if (!isAdmin && !event.isOwnedBy(userId)) {
            throw new EventException(EventErrorCode.FORBIDDEN);
        }
    }

    void validateSalePeriod(LocalDateTime saleStartAt, LocalDateTime saleEndAt) {
        if (!saleEndAt.isAfter(saleStartAt)) {
            throw new EventException(EventErrorCode.INVALID_SALE_PERIOD);
        }
    }
}
