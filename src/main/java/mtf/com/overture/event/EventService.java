package mtf.com.overture.event;

import mtf.com.overture.event.dto.EventCreateRequest;
import mtf.com.overture.event.dto.EventResponse;
import mtf.com.overture.event.dto.EventUpdateRequest;
import mtf.com.overture.event.dto.SeatGradeCreateRequest;
import mtf.com.overture.event.dto.SeatGradeResponse;
import mtf.com.overture.event.dto.SeatGradeUpdateRequest;
import mtf.com.overture.event.dto.SeatResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventCache eventCache;
    private final SeatGradeRepository seatGradeRepository;
    private final SeatRepository seatRepository;

    public EventService(EventRepository eventRepository, EventCache eventCache,
                         SeatGradeRepository seatGradeRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.eventCache = eventCache;
        this.seatGradeRepository = seatGradeRepository;
        this.seatRepository = seatRepository;
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
        assertVisible(authentication, event.getStatus().name(), event.getCreatedBy(), userId);
        requireOwnerOrAdmin(authentication, event, userId);

        LocalDateTime newSaleStartAt = request.saleStartAt() != null ? request.saleStartAt() : event.getSaleStartAt();
        LocalDateTime newSaleEndAt = request.saleEndAt() != null ? request.saleEndAt() : event.getSaleEndAt();
        validateSalePeriod(newSaleStartAt, newSaleEndAt);

        event.update(request.title(), request.venue(), request.description(), request.posterUrl(),
                request.saleStartAt(), request.saleEndAt());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventCache.evictEvent(eventId);
            }
        });

        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId, Long viewerId) {
        return getEvent(null, eventId, viewerId);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Authentication authentication, Long eventId, Long viewerId) {
        Optional<EventResponse> cached = eventCache.getEvent(eventId);
        if (cached.isPresent()) {
            EventResponse response = cached.get();
            assertVisible(authentication, response.status(), response.createdBy(), viewerId);
            return response;
        }

        Event event = findEvent(eventId);
        assertVisible(authentication, event.getStatus().name(), event.getCreatedBy(), viewerId);

        EventResponse response = EventResponse.from(event);
        eventCache.putEvent(eventId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listEvents(Pageable pageable) {
        return eventRepository.findByStatus(EventStatus.PUBLISHED, pageable)
                .map(EventResponse::from);
    }

    @Transactional
    public SeatGradeResponse addGrade(Authentication authentication, Long userId, Long eventId, SeatGradeCreateRequest request) {
        Event event = findEvent(eventId);
        assertVisible(authentication, event.getStatus().name(), event.getCreatedBy(), userId);
        requireOwnerOrAdmin(authentication, event, userId);

        if (seatGradeRepository.existsByEventIdAndName(eventId, request.name())) {
            throw new EventException(EventErrorCode.DUPLICATE_GRADE_NAME);
        }

        SeatGrade grade = seatGradeRepository.save(SeatGrade.builder()
                .eventId(eventId)
                .name(request.name())
                .price(request.price())
                .totalCount(request.totalCount())
                .remainCount(request.totalCount())
                .build());

        List<Seat> seats = new ArrayList<>();
        for (int seatNumber = 1; seatNumber <= request.totalCount(); seatNumber++) {
            seats.add(Seat.builder()
                    .gradeId(grade.getId())
                    .row(1)
                    .col(seatNumber)
                    .status(SeatStatus.AVAILABLE)
                    .build());
        }
        seatRepository.saveAll(seats);

        if (event.isDraft()) {
            event.publish();
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventCache.evictEvent(eventId);
                eventCache.evictGrades(eventId);
            }
        });

        return SeatGradeResponse.from(grade);
    }

    @Transactional(readOnly = true)
    public List<SeatGradeResponse> listGrades(Long eventId, Long viewerId) {
        return listGrades(null, eventId, viewerId);
    }

    @Transactional(readOnly = true)
    public List<SeatGradeResponse> listGrades(Authentication authentication, Long eventId, Long viewerId) {
        Event event = findEvent(eventId);
        assertVisible(authentication, event.getStatus().name(), event.getCreatedBy(), viewerId);

        Optional<List<SeatGradeResponse>> cached = eventCache.getGrades(eventId);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<SeatGradeResponse> grades = seatGradeRepository.findByEventId(eventId).stream()
                .map(SeatGradeResponse::from)
                .toList();
        eventCache.putGrades(eventId, grades);
        return grades;
    }

    @Transactional
    public SeatGradeResponse updateGrade(Authentication authentication, Long userId, Long eventId, Long gradeId, SeatGradeUpdateRequest request) {
        Event event = findEvent(eventId);
        assertVisible(authentication, event.getStatus().name(), event.getCreatedBy(), userId);
        requireOwnerOrAdmin(authentication, event, userId);

        SeatGrade grade = seatGradeRepository.findById(gradeId)
                .filter(g -> g.getEventId().equals(eventId))
                .orElseThrow(() -> new EventException(EventErrorCode.NOT_FOUND));

        grade.update(request.name(), request.price());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventCache.evictEvent(eventId);
                eventCache.evictGrades(eventId);
            }
        });

        return SeatGradeResponse.from(grade);
    }

    @Transactional(readOnly = true)
    public Map<String, List<SeatResponse>> getSeats(Long eventId, Long viewerId) {
        return getSeats(null, eventId, viewerId);
    }

    @Transactional(readOnly = true)
    public Map<String, List<SeatResponse>> getSeats(Authentication authentication, Long eventId, Long viewerId) {
        Event event = findEvent(eventId);
        assertVisible(authentication, event.getStatus().name(), event.getCreatedBy(), viewerId);

        List<SeatGrade> grades = seatGradeRepository.findByEventId(eventId);
        List<Long> gradeIds = grades.stream().map(SeatGrade::getId).toList();
        List<Seat> seats = seatRepository.findByGradeIdIn(gradeIds);

        Map<Long, String> gradeNameById = grades.stream()
                .collect(Collectors.toMap(SeatGrade::getId, SeatGrade::getName));

        return seats.stream()
                .collect(Collectors.groupingBy(
                        seat -> gradeNameById.get(seat.getGradeId()),
                        Collectors.mapping(SeatResponse::from, Collectors.toList())));
    }

    Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventException(EventErrorCode.NOT_FOUND));
    }

    void assertVisible(Authentication authentication, String status, Long createdBy, Long viewerId) {
        boolean isDraft = EventStatus.DRAFT.name().equals(status);
        boolean isOwner = viewerId != null && viewerId.equals(createdBy);
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        if (isDraft && !isOwner && !isAdmin) {
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
