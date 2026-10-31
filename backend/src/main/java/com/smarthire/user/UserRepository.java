package com.smarthire.user;

import com.smarthire.enums.Enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@code JpaSpecificationExecutor} is used for the admin search instead of a null-guarded
 * JPQL query: {@code (:q is null or lower(x) like ...)} makes PostgreSQL bind untyped
 * parameters and fails with "function lower(bytea) does not exist".
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByIdAndRole_Code(Long id, String roleCode);

    long countByRole_Code(String roleCode);

    @Query("select u from User u join fetch u.role r where r.code = 'CANDIDATE'")
    List<User> findAllCandidates();

    @Query("select u.id from User u where u.role.code = :role and u.status = com.smarthire.enums.Enums.UserStatus.ACTIVE")
    List<Long> findActiveIdsByRole(@Param("role") String role);
}
