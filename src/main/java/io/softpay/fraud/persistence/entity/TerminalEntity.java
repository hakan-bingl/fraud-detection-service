package io.softpay.fraud.persistence.entity;

import io.softpay.fraud.model.records.data.Terminal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "terminals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerminalEntity {

    @Id
    @Column(name = "terminal_id", length = 64, nullable = false)
    private String terminalId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public Terminal toDomain() {
        return new Terminal(terminalId, active, description);
    }

    public static TerminalEntity fromDomain(Terminal terminal) {
        return new TerminalEntity(terminal.terminalId(), terminal.active(), terminal.description(), Instant.now());
    }
}
