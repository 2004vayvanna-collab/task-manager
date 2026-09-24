package com.learning.taskmanager;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService userDetailsService(UserRepository users){
  return username -> users.findByUsername(username).map(u -> User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles("USER").build())
   .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
 }
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http.authorizeHttpRequests(a -> a
   .requestMatchers("/","/index.html","/style.css","/app.js","/api/auth/csrf","/api/auth/register","/api/auth/login","/error","/actuator/health/**").permitAll()
   .anyRequest().authenticated())
   .requestCache(c -> c.disable())
   .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
   .formLogin(f -> f.loginProcessingUrl("/api/auth/login")
    .successHandler((req,res,auth) -> res.setStatus(204))
    .failureHandler((req,res,e) -> res.setStatus(401)))
   .logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
   .build();
 }
}
