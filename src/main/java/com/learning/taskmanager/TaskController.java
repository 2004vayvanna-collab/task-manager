package com.learning.taskmanager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.security.Principal;
import java.time.LocalDate;
@RestController @RequestMapping("/api/tasks")
public class TaskController {
 private final TaskRepository repository; private final UserRepository users;
 public TaskController(TaskRepository repository,UserRepository users){this.repository=repository;this.users=users;}
 public record CreateTask(@NotBlank @Size(max=120) String title,@NotNull Task.Priority priority,LocalDate dueDate){}
 public record UpdateTask(@NotBlank @Size(max=120) String title,@NotNull Task.Priority priority,@NotNull Boolean completed,LocalDate dueDate){}
 private Long owner(Principal user){return users.findByUsername(user.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();}
 @GetMapping public List<Task> list(Principal user){return repository.findByOwnerIdOrderByCreatedAtDesc(owner(user));}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public Task create(Principal user,@Valid @RequestBody CreateTask input){
  Task t=new Task(input.title(),input.priority());t.setOwnerId(owner(user));t.setDueDate(input.dueDate());return repository.save(t);
 }
 @PutMapping("/{id}") public Task update(Principal user,@PathVariable Long id,@Valid @RequestBody UpdateTask input){
  Task t=find(id,owner(user));t.update(input.title(),input.priority(),input.completed());t.setDueDate(input.dueDate());return repository.save(t);
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(Principal user,@PathVariable Long id){repository.delete(find(id,owner(user)));}
 private Task find(Long id,Long owner){return repository.findByIdAndOwnerId(id,owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));}
}
