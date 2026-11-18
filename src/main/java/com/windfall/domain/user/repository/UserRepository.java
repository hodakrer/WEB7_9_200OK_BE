package com.windfall.domain.user.repository;

import com.windfall.domain.user.entity.User;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByProviderUserId(String providerUserId);

  boolean existsByEmail(String email);

  @Query("select u.id from User u where u.id in :ids")
  Set<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
