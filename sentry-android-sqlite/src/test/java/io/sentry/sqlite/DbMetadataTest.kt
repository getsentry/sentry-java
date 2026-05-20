package io.sentry.sqlite

import kotlin.test.Test
import kotlin.test.assertEquals

class DbMetadataTest {

  @Test
  fun `dbMetadataFromFileName returns in-memory system with no db name for in-memory sentinel`() {
    assertEquals(
      DbMetadata(name = null, system = DB_SYSTEM_IN_MEMORY),
      dbMetadataFromFileName(":memory:"),
    )
  }

  @Test
  fun `dbMetadataFromDatabaseName returns in-memory system with no db name when databaseName is null`() {
    assertEquals(
      DbMetadata(name = null, system = DB_SYSTEM_IN_MEMORY),
      dbMetadataFromDatabaseName(null),
    )
  }

  @Test
  fun `dbMetadataFromFileName returns sqlite system and db name for unix path`() {
    assertEquals(
      DbMetadata(name = "tracks.db", system = DB_SYSTEM_SQLITE),
      dbMetadataFromFileName("/data/data/com.example/databases/tracks.db"),
    )
  }

  @Test
  fun `dbMetadataFromFileName returns sqlite system and db name when fileName has no separator`() {
    assertEquals(
      DbMetadata(name = "tracks", system = DB_SYSTEM_SQLITE),
      dbMetadataFromFileName("tracks"),
    )
    assertEquals(
      DbMetadata(name = "tracks.db", system = DB_SYSTEM_SQLITE),
      dbMetadataFromFileName("tracks.db"),
    )
  }

  @Test
  fun `dbMetadataFromFileName returns sqlite system and db name for relative path with forward slashes`() {
    assertEquals(
      DbMetadata(name = "myapp.db", system = DB_SYSTEM_SQLITE),
      dbMetadataFromFileName("databases/myapp.db"),
    )
  }

  @Test
  fun `dbMetadataFromFileName returns sqlite system and db name for windows-style path`() {
    assertEquals(
      DbMetadata(name = "myapp.db", system = DB_SYSTEM_SQLITE),
      dbMetadataFromFileName("C:\\Users\\app\\databases\\myapp.db"),
    )
  }

  @Test
  fun `dbMetadataFromFileName uses last separator when both slash types are present`() {
    assertEquals(
      DbMetadata(name = "db.sqlite", system = DB_SYSTEM_SQLITE),
      dbMetadataFromFileName("/data\\mixed/path\\db.sqlite"),
    )
  }

  @Test
  fun `dbMetadataFromFileName returns sqlite system and db name when fileName ends with separator`() {
    assertEquals(
      DbMetadata(name = "databases", system = DB_SYSTEM_SQLITE),
      dbMetadataFromFileName("/data/data/com.example/databases/"),
    )
  }

  @Test
  fun `dbMetadataFromFileName returns sqlite system and unknown db name when fileName contains only separators`() {
    assertEquals(DbMetadata(name = null, system = DB_SYSTEM_SQLITE), dbMetadataFromFileName("/"))
    assertEquals(DbMetadata(name = null, system = DB_SYSTEM_SQLITE), dbMetadataFromFileName("///"))
    assertEquals(DbMetadata(name = null, system = DB_SYSTEM_SQLITE), dbMetadataFromFileName("\\\\"))
  }

  @Test
  fun `dbMetadataFromFileName returns sqlite system and unknown db name for empty fileName`() {
    assertEquals(DbMetadata(name = null, system = DB_SYSTEM_SQLITE), dbMetadataFromFileName(""))
  }
}
