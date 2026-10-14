package io.softpay.fraud.adapter.out;

import io.softpay.fraud.model.records.data.Terminal;
import io.softpay.fraud.persistence.entity.TerminalEntity;
import io.softpay.fraud.persistence.repository.TerminalRepository;
import io.softpay.fraud.port.out.TerminalDataOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Authoritative TerminalRepository implementation backed by Spring Data JPA & PostgreSQL.
 */
@Repository
@RequiredArgsConstructor
public class TerminalDataService implements TerminalDataOut {

    private final TerminalRepository springDataRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Terminal> findByTerminalId(String terminalId) {
        if (terminalId == null) {
            return Optional.empty();
        }
        return springDataRepository.findById(terminalId).map(TerminalEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(Terminal terminal) {
        if (terminal != null && terminal.terminalId() != null) {
            springDataRepository.save(TerminalEntity.fromDomain(terminal));
        }
    }
}
