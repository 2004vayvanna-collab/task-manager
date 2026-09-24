package com.learning.taskmanager;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name="tasks")
public class Task {
 public enum Priority { LOW, MEDIUM, HIGH }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, length=120) private String title;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=10) private Priority priority;
 @Column(nullable=false) private boolean completed;
 @Column(nullable=false, updatable=false) private Instant createdAt;
 @Column private LocalDate dueDate;
 @JsonIgnore @Column(name="owner_id") private Long ownerId;
 protected Task() {}
 public Task(String title, Priority priority) { this.title=title.trim(); this.priority=priority; this.createdAt=Instant.now(); }
 public LocalDate getDueDate(){return dueDate;}
 public void setDueDate(LocalDate value){dueDate=value;}
 public void setOwnerId(Long value){ownerId=value;}
 public Long getId() { return id; }
 public String getTitle() { return title; }
 public Priority getPriority() { return priority; }
 public boolean isCompleted() { return completed; }
 public Instant getCreatedAt() { return createdAt; }
 public void update(String title, Priority priority, boolean completed) { this.title=title.trim(); this.priority=priority; this.completed=completed; }
}
