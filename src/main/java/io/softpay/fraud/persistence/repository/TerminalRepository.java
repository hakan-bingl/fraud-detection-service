package io.softpay.fraud.persistence.repository;

import io.softpay.fraud.persistence.entity.TerminalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TerminalRepository extends JpaRepository<TerminalEntity, String> {
}
