package com.learning.taskmanager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class TaskApiTest {
 @Autowired MockMvc mvc;
 @Autowired TaskRepository tasks;
 @Autowired UserRepository users;
 @Autowired PasswordEncoder encoder;
 @Autowired ObjectMapper mapper;
 Long aliceId;
 @BeforeEach void reset(){tasks.deleteAll();users.deleteAll();aliceId=users.saveAndFlush(new AppUser("alice",encoder.encode("a-long-password"))).getId();users.saveAndFlush(new AppUser("bob",encoder.encode("another-password")));}
 @Test void anonymousCannotReadTasks() throws Exception {mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());}
 @Test @WithMockUser("alice") void writesRequireCsrf() throws Exception {mvc.perform(post("/api/tasks").contentType("application/json").content("{\"title\":\"Test\",\"priority\":\"HIGH\"}")).andExpect(status().isForbidden());}
 @Test @WithMockUser("alice") void taskLifecycleAndDueDates() throws Exception {
  String body=mvc.perform(post("/api/tasks").with(csrf()).contentType("application/json").content("{\"title\":\" Learn Docker \",\"priority\":\"HIGH\",\"dueDate\":\"2026-10-01\"}"))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Learn Docker")).andExpect(jsonPath("$.dueDate").value("2026-10-01")).andExpect(jsonPath("$.ownerId").doesNotExist()).andReturn().getResponse().getContentAsString();
  long id=mapper.readTree(body).get("id").asLong();
  mvc.perform(get("/api/tasks")).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(put("/api/tasks/"+id).with(csrf()).contentType("application/json").content("{\"title\":\"Edited task\",\"priority\":\"LOW\",\"completed\":true,\"dueDate\":null}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Edited task")).andExpect(jsonPath("$.priority").value("LOW")).andExpect(jsonPath("$.completed").value(true)).andExpect(jsonPath("$.dueDate").isEmpty());
  mvc.perform(delete("/api/tasks/"+id).with(csrf())).andExpect(status().isNoContent());
 }
 @Test @WithMockUser("bob") void otherUsersCannotReadModifyOrDelete() throws Exception {
  Task t=new Task("Private",Task.Priority.HIGH);t.setOwnerId(aliceId);tasks.saveAndFlush(t);
  mvc.perform(get("/api/tasks")).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(put("/api/tasks/"+t.getId()).with(csrf()).contentType("application/json").content("{\"title\":\"Stolen\",\"priority\":\"LOW\",\"completed\":true}")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/tasks/"+t.getId()).with(csrf())).andExpect(status().isNotFound());
  assertTrue(tasks.existsById(t.getId()));
 }
 @Test @WithMockUser("alice") void legacyTasksRemainUnassigned() throws Exception {tasks.saveAndFlush(new Task("Legacy",Task.Priority.LOW));mvc.perform(get("/api/tasks")).andExpect(jsonPath("$.length()").value(0));assertEquals(1,tasks.count());}
 @Test @WithMockUser("alice") void rejectsInvalidTaskInputs() throws Exception {
  for(String body:new String[]{"{\"title\":\"  \",\"priority\":\"HIGH\"}","{\"title\":\"A\",\"priority\":\"URGENT\"}","{\"title\":\"A\",\"priority\":\"HIGH\",\"dueDate\":\"not-a-date\"}"})
   mvc.perform(post("/api/tasks").with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
 }
 @Test void registrationHashesPasswordAndRejectsDuplicates() throws Exception {
  String body="{\"username\":\"charlie\",\"password\":\"my-long-password\"}";
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
  String hash=users.findByUsername("charlie").orElseThrow().getPasswordHash();assertNotEquals("my-long-password",hash);assertTrue(encoder.matches("my-long-password",hash));
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
 }
 @Test void rejectsWeakPassword() throws Exception {mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"username\":\"charlie\",\"password\":\"short\"}")).andExpect(status().isBadRequest());}
 @Test void loginSessionAndLogout() throws Exception {
  mvc.perform(post("/api/auth/login").with(csrf()).param("username","alice").param("password","wrong")).andExpect(status().isUnauthorized());
  var login=mvc.perform(post("/api/auth/login").with(csrf()).param("username","alice").param("password","a-long-password")).andExpect(status().isNoContent()).andReturn();
  var cookies=login.getResponse().getCookies();assertTrue(cookies.length>0);
  mvc.perform(get("/api/auth/me").cookie(cookies)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
  mvc.perform(post("/api/auth/logout").cookie(cookies).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/auth/me").cookie(cookies)).andExpect(status().isUnauthorized());
 }
 @Test void uiCsrfAndReadinessArePublic() throws Exception {
  mvc.perform(get("/index.html")).andExpect(status().isOk());
  mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
  mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
 }
}
