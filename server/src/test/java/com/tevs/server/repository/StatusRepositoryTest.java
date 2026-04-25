package com.tevs.server.repository;

import com.tevs.server.model.StatusMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StatusRepositoryTest {

    @Autowired
    private StatusRepository repository;

    private StatusMessage sampleMessage() {
        return new StatusMessage("alice", "Hello", Instant.now(), 48.2, 16.4);
    }

    @Test
    void saveAndFindById() {
        StatusMessage saved = repository.save(sampleMessage());
        Optional<StatusMessage> found = repository.findById("alice");
        assertThat(found).isPresent();
        assertThat(found.get().getStatustext()).isEqualTo("Hello");
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        Optional<StatusMessage> found = repository.findById("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    void findAllReturnsAllRecords() {
        repository.save(sampleMessage());
        repository.save(new StatusMessage("bob", "World", Instant.now(), 48.3, 16.5));
        List<StatusMessage> all = repository.findAll();
        assertThat(all).hasSize(2);
    }

    @Test
    void findAllReturnsEmptyWhenNoRecords() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void deleteRemovesRecord() {
        repository.save(sampleMessage());
        assertThat(repository.existsById("alice")).isTrue();
        repository.deleteById("alice");
        assertThat(repository.existsById("alice")).isFalse();
    }

    @Test
    void existsByIdReturnsFalseForMissing() {
        assertThat(repository.existsById("nobody")).isFalse();
    }

    @Test
    void updateExistingRecord() {
        repository.save(sampleMessage());
        StatusMessage updated = new StatusMessage("alice", "Updated", Instant.now(), 48.2, 16.4);
        repository.save(updated);
        Optional<StatusMessage> found = repository.findById("alice");
        assertThat(found).isPresent();
        assertThat(found.get().getStatustext()).isEqualTo("Updated");
    }
}
