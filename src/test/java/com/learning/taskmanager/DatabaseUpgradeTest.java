package com.learning.taskmanager;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseUpgradeTest {
 @Test void upgradePreservesExistingTasksWithoutAssigningThem() throws Exception {
  String url="jdbc:h2:mem:upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
  Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
   s.executeUpdate("INSERT INTO tasks(title,priority,completed,created_at) VALUES ('Existing task','HIGH',FALSE,CURRENT_TIMESTAMP)");
  }
  Flyway.configure().dataSource(url,"sa","").load().migrate();
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("SELECT title,owner_id,due_date FROM tasks")){
   assertTrue(r.next());assertEquals("Existing task",r.getString("title"));assertNull(r.getObject("owner_id"));assertNull(r.getObject("due_date"));assertFalse(r.next());
  }
 }
}
