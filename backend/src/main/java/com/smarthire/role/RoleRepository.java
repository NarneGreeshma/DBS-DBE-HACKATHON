package com.smarthire.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Deliberately takes a {@link String} rather than {@code RoleCode}: Hibernate 6 binds
     * method-derived parameters strictly, and passing the enum here makes it reject the
     * value with "did not match expected type".
     */
    Optional<Role> findByCode(String code);
}
