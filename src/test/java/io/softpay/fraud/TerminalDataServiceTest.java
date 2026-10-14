package io.softpay.fraud;

import io.softpay.fraud.adapter.out.TerminalDataService;
import io.softpay.fraud.persistence.repository.TerminalRepository;
import io.softpay.fraud.model.records.data.Terminal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TerminalDataService.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true"
})
class TerminalDataServiceTest {

    @Autowired
    private TerminalDataService jpaTerminalRepository;

    @Autowired
    private TerminalRepository terminalRepository;

    @Test
    @DisplayName("Should retrieve seed terminals pre-populated by Flyway migration")
    void testFindFlywaySeededTerminal() {
        Optional<Terminal> terminal = jpaTerminalRepository.findByTerminalId("TERM-001");

        assertThat(terminal).isPresent();
        assertThat(terminal.get().terminalId()).isEqualTo("TERM-001");
        assertThat(terminal.get().active()).isTrue();
    }

    @Test
    @DisplayName("Should save and retrieve custom terminal entity in relational store")
    void testSaveAndFindTerminal() {
        Terminal newTerminal = new Terminal("TERM-CUSTOM-999", true, "New Custom POS");
        jpaTerminalRepository.save(newTerminal);

        Optional<Terminal> fetched = jpaTerminalRepository.findByTerminalId("TERM-CUSTOM-999");
        assertThat(fetched).isPresent();
        assertThat(fetched.get().terminalId()).isEqualTo("TERM-CUSTOM-999");
        assertThat(fetched.get().description()).isEqualTo("New Custom POS");
    }
}
