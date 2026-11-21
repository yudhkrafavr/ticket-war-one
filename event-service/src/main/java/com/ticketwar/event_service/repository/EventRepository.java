package com.ticketwar.event_service.repository;

import com.ticketwar.event_service.entity.Event;
import com.ticketwar.event_service.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByStatus(EventStatus status);

    List<Event> findByNameContainingIgnoreCase(String name);
}