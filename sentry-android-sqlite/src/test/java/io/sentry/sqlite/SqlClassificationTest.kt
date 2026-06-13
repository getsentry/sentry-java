package io.sentry.sqlite

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlClassificationTest {

  @Test
  fun `BEGIN variants are classified as BEGIN_TRANSACTION`() {
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("BEGIN IMMEDIATE TRANSACTION"))
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("BEGIN DEFERRED TRANSACTION"))
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("BEGIN EXCLUSIVE TRANSACTION"))
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("BEGIN TRANSACTION"))
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("BEGIN"))
  }

  @Test
  fun `BEGIN is case-insensitive`() {
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("begin immediate transaction"))
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("Begin Immediate Transaction"))
  }

  @Test
  fun `BEGIN with leading whitespace is classified correctly`() {
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("  BEGIN IMMEDIATE TRANSACTION"))
    assertEquals(SqlRole.BEGIN_TRANSACTION, classifySql("\tBEGIN"))
  }

  @Test
  fun `COMMIT variants are classified as COMMIT_TRANSACTION`() {
    assertEquals(SqlRole.COMMIT_TRANSACTION, classifySql("COMMIT"))
    assertEquals(SqlRole.COMMIT_TRANSACTION, classifySql("COMMIT TRANSACTION"))
    assertEquals(SqlRole.COMMIT_TRANSACTION, classifySql("commit"))
  }

  @Test
  fun `END variants are classified as COMMIT_TRANSACTION`() {
    assertEquals(SqlRole.COMMIT_TRANSACTION, classifySql("END TRANSACTION"))
    assertEquals(SqlRole.COMMIT_TRANSACTION, classifySql("END"))
    assertEquals(SqlRole.COMMIT_TRANSACTION, classifySql("end transaction"))
  }

  @Test
  fun `bare ROLLBACK is classified as ROLLBACK_TRANSACTION`() {
    assertEquals(SqlRole.ROLLBACK_TRANSACTION, classifySql("ROLLBACK"))
    assertEquals(SqlRole.ROLLBACK_TRANSACTION, classifySql("ROLLBACK TRANSACTION"))
    assertEquals(SqlRole.ROLLBACK_TRANSACTION, classifySql("rollback"))
    assertEquals(SqlRole.ROLLBACK_TRANSACTION, classifySql("rollback transaction"))
  }

  @Test
  fun `ROLLBACK TO is classified as PASSTHROUGH`() {
    assertEquals(SqlRole.PASSTHROUGH, classifySql("ROLLBACK TO SAVEPOINT '1'"))
    assertEquals(SqlRole.PASSTHROUGH, classifySql("ROLLBACK TRANSACTION TO SAVEPOINT '1'"))
    assertEquals(SqlRole.PASSTHROUGH, classifySql("rollback to savepoint '1'"))
    assertEquals(SqlRole.PASSTHROUGH, classifySql("rollback transaction to savepoint '1'"))
  }

  @Test
  fun `SAVEPOINT is classified as PASSTHROUGH`() {
    assertEquals(SqlRole.PASSTHROUGH, classifySql("SAVEPOINT '1'"))
    assertEquals(SqlRole.PASSTHROUGH, classifySql("savepoint '1'"))
  }

  @Test
  fun `RELEASE is classified as PASSTHROUGH`() {
    assertEquals(SqlRole.PASSTHROUGH, classifySql("RELEASE SAVEPOINT '1'"))
    assertEquals(SqlRole.PASSTHROUGH, classifySql("release savepoint '1'"))
  }

  @Test
  fun `regular queries are classified as QUERY`() {
    assertEquals(SqlRole.QUERY, classifySql("SELECT * FROM users"))
    assertEquals(
      SqlRole.QUERY,
      classifySql("INSERT OR ABORT INTO `song` (`id`, `title`) VALUES (?, ?)"),
    )
    assertEquals(SqlRole.QUERY, classifySql("UPDATE songs SET title = ? WHERE id = ?"))
    assertEquals(SqlRole.QUERY, classifySql("DELETE FROM songs WHERE id = ?"))
    assertEquals(
      SqlRole.QUERY,
      classifySql("CREATE TABLE IF NOT EXISTS songs (id INTEGER PRIMARY KEY)"),
    )
    assertEquals(SqlRole.QUERY, classifySql("PRAGMA journal_mode"))
  }

  @Test
  fun `edge cases are classified as QUERY`() {
    assertEquals(SqlRole.QUERY, classifySql(""))
    assertEquals(SqlRole.QUERY, classifySql("   "))
  }
}
