package com.ticket_war.war.event;

import com.ticket_war.war.dto.event.WarResultsFinalizedMessage;

public record WarResultsFinalizedApplicationEvent(WarResultsFinalizedMessage message) {
}
