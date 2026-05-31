package io.vidocq.tools.arago.persistence;

import jakarta.data.repository.BasicRepository;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

/**
 * Jakarta Data repository for the superadmin {@link AdminAudit} trail. Mansart generates the impl at
 * compile-time. CRUD + {@code findAll()} (Stream) come from {@link BasicRepository}.
 */
@Transactional
@Repository
public interface AdminAuditRepository extends BasicRepository<AdminAudit, String> {

    long count();
}
