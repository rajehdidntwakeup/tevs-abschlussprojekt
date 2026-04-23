package com.tevs.server.repository;

import com.tevs.server.model.StatusMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StatusRepository extends JpaRepository<StatusMessage, String> {
}
