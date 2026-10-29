package com.devarena.users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("select u from User u where lower(u.email) = lower(:login) or lower(u.username) = lower(:login)")
    Optional<User> findByLogin(@Param("login") String login);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("""
            select u from User u
            where u.id <> :excludeId
              and (lower(u.username) like lower(concat('%', :q, '%'))
                   or lower(u.displayName) like lower(concat('%', :q, '%')))
            order by u.username
            """)
    List<User> search(@Param("q") String q, @Param("excludeId") UUID excludeId, Pageable pageable);
}
