package com.learning.taskmanager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final UserRepository users; private final PasswordEncoder encoder;
 public AuthController(UserRepository users,PasswordEncoder encoder){this.users=users;this.encoder=encoder;}
 public record Registration(@NotNull @Pattern(regexp="[a-z0-9_]{3,40}") String username,@NotNull @Size(min=10,max=72) String password){}
 @GetMapping("/csrf") public CsrfToken csrf(CsrfToken token){return token;}
 @GetMapping("/me") public Map<String,String> me(Principal user){return Map.of("username",user.getName());}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,String> register(@Valid @RequestBody Registration input){
  if(input.password().getBytes(StandardCharsets.UTF_8).length>72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password exceeds 72 bytes");
  try {users.saveAndFlush(new AppUser(input.username(),encoder.encode(input.password())));}
  catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Username unavailable");}
  return Map.of("username",input.username());
 }
}
