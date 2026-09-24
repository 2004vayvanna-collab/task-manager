package com.learning.taskmanager;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TaskRepository extends JpaRepository<Task,Long> {
 List<Task> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
 Optional<Task> findByIdAndOwnerId(Long id,Long ownerId);
}
