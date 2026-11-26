package com.bayflow.auth.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.UserAvatar;

@Transactional(readOnly = true)
public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {
}
