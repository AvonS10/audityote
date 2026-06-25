package io.muzoo.ssc.controlmap.repository;

import io.muzoo.ssc.controlmap.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link User}. Used by Spring Security (load-by-email) and the seeder. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
