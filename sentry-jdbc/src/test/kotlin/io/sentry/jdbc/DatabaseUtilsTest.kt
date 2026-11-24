package io.sentry.jdbc

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseUtilsTest {
  @Test
  fun `parses to empty details for null`() {
    val details = DatabaseUtils.parse(null)
    assertNotNull(details)
    assertNull(details.dbSystem)
    assertNull(details.dbName)
  }

  @Test
  fun `detects db system for hsql in-memory`() {
    val details = DatabaseUtils.parse("jdbc:p6spy:hsqldb:mem:testdb;a=b")
    assertEquals("hsqldb", details.dbSystem)
    assertEquals("testdb", details.dbName)
  }

  @Test
  fun `detects db system for hsql in-memory legacy`() {
    val details = DatabaseUtils.parse("jdbc:p6spy:hsqldb:.;a=b")
    assertEquals("hsqldb", details.dbSystem)
    assertEquals(".", details.dbName)
  }

  @Test
  fun `detects db system for hsql remote`() {
    val details = DatabaseUtils.parse("jdbc:hsqldb:hsql://some-host.com:1234/testdb;a=b")
    assertEquals("hsqldb", details.dbSystem)
    assertEquals("testdb", details.dbName)
  }

  @Test
  fun `detects db system for h2 in-memory`() {
    val details = DatabaseUtils.parse("jdbc:h2:mem:AZ;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
    assertEquals("h2", details.dbSystem)
    assertEquals("az", details.dbName)
  }

  @Test
  fun `detects db system for h2 tcp`() {
    val details = DatabaseUtils.parse("jdbc:h2:tcp://localhost/~/test")
    assertEquals("h2", details.dbSystem)
    assertEquals("~/test", details.dbName)
  }

  @Test
  fun `detects db system for derby`() {
    val details = DatabaseUtils.parse("jdbc:derby:sample")
    assertEquals("derby", details.dbSystem)
    assertEquals("sample", details.dbName)
  }

  @Test
  fun `detects db system for derby remote`() {
    val details = DatabaseUtils.parse("jdbc:derby://some-host.com:1234/sample")
    assertEquals("derby", details.dbSystem)
    assertEquals("sample", details.dbName)
  }

  @Test
  fun `detects db system for derby remote no port`() {
    val details = DatabaseUtils.parse("jdbc:derby://some-host.com/sample")
    assertEquals("derby", details.dbSystem)
    assertEquals("sample", details.dbName)
  }

  @Test
  fun `detects db system for sqlite`() {
    val details = DatabaseUtils.parse("jdbc:sqlite:sample.db")
    assertEquals("sqlite", details.dbSystem)
    assertEquals("sample.db", details.dbName)
  }

  @Test
  fun `detects db system for sqlite memory`() {
    val details = DatabaseUtils.parse("jdbc:sqlite::memory:")
    assertEquals("sqlite", details.dbSystem)
    assertEquals("memory", details.dbName)
  }

  @Test
  fun `detects db system for sqlite windows`() {
    val details = DatabaseUtils.parse("jdbc:sqlite:C:/sqlite/db/some.db")
    assertEquals("sqlite", details.dbSystem)
    assertEquals("/sqlite/db/some.db", details.dbName)
  }

  @Test
  fun `detects db system for sqlite linux`() {
    val details = DatabaseUtils.parse("jdbc:sqlite:/home/sqlite/db/some.db")
    assertEquals("sqlite", details.dbSystem)
    assertEquals("/home/sqlite/db/some.db", details.dbName)
  }

  @Test
  fun `detects db system for mongo`() {
    val details = DatabaseUtils.parse("jdbc:mongo://some-server.com:1234/mydb")
    assertEquals("mongo", details.dbSystem)
    assertEquals("mydb", details.dbName)
  }

  @Test
  fun `detects db system for mongo no db`() {
    val details = DatabaseUtils.parse("jdbc:mongo://some-server.com:1234")
    assertEquals("mongo", details.dbSystem)
    assertEquals("", details.dbName)
  }

  @Test
  fun `detects db system for redis`() {
    val details = DatabaseUtils.parse("jdbc:redis:Server=127.0.0.1;Port=6379;Password=myPassword;")
    assertEquals("redis", details.dbSystem)
    assertNull(details.dbName)
  }

  @Test
  fun `detects db system for dynamodb`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:amazondynamodb:Access Key=xxx;Secret Key=xxx;Domain=amazonaws.com;Region=OREGON;"
      )
    assertEquals("amazondynamodb", details.dbSystem)
    assertNull(details.dbName)
  }

  @Test
  fun `detects db system for oracle`() {
    val details = DatabaseUtils.parse("jdbc:oracle:thin:@//myoracle.db.server:1521/my_servicename")
    assertEquals("oracle", details.dbSystem)
    assertEquals("my_servicename", details.dbName)
  }

  @Test
  fun `detects db system for oracle2`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=myoracle.db.server)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=my_servicename)))"
      )
    assertEquals("oracle", details.dbSystem)
    assertEquals("my_servicename", details.dbName)
  }

  @Test
  fun `detects db system for mariadb`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:mariadb://example.skysql.net:5001/jdbc_demo?useSsl=true&serverSslCert=/path/to/skysql_chain.pem"
      )
    assertEquals("mariadb", details.dbSystem)
    assertEquals("jdbc_demo", details.dbName)
  }

  @Test
  fun `detects db system for mariadb no host and port`() {
    val details = DatabaseUtils.parse("jdbc:mariadb://")
    assertEquals("mariadb", details.dbSystem)
    assertNull(details.dbName)
  }

  @Test
  fun `detects db system for mysql`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:mysql://mysql.db.server:3306/my_database?useSSL=false&serverTimezone=UTC"
      )
    assertEquals("mysql", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for mysql no host and port and database`() {
    val details = DatabaseUtils.parse("jdbc:mysql://")
    assertEquals("mysql", details.dbSystem)
    assertNull(details.dbName)
  }

  @Test
  fun `detects db system for mssql`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:sqlserver://mssql.db.server\\\\mssql_instance;databaseName=my_database"
      )
    assertEquals("sqlserver", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for mssql2`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:sqlserver://mssql.db.server\\\\mssql_instance;databaseName=my_database;otherProperty=value"
      )
    assertEquals("sqlserver", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for mssql2 no host and port`() {
    val details =
      DatabaseUtils.parse("jdbc:sqlserver://;databaseName=my_database;otherProperty=value")
    assertEquals("sqlserver", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for postgres`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:postgresql://postgresql.db.server:5430/my_database?ssl=true&loglevel=2"
      )
    assertEquals("postgresql", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for postgres no host and port`() {
    val details = DatabaseUtils.parse("jdbc:postgresql:///my_database?ssl=true&loglevel=2")
    assertEquals("postgresql", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for datadirect postgres`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:datadirect:postgresql://postgresql.db.server:5430/my_database?ssl=true&loglevel=2"
      )
    assertEquals("postgresql", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for tibcosoftware postgres`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:tibcosoftware:postgresql://postgresql.db.server:5430/my_database?ssl=true&loglevel=2"
      )
    assertEquals("postgresql", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for jtds postgres`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:jtds:postgresql://postgresql.db.server:5430/my_database?ssl=true&loglevel=2"
      )
    assertEquals("postgresql", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  @Test
  fun `detects db system for microsoft sqlserver`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:microsoft:sqlserver://mssql.db.server\\\\mssql_instance;databaseName=my_database"
      )
    assertEquals("sqlserver", details.dbSystem)
    assertEquals("my_database", details.dbName)
  }

  // Wrapper URL forms from each project's docs. Testcontainers ignores the database name in the
  // URL, so none is reported for it.

  @Test
  fun `detects db system for testcontainers with image tag`() {
    val details = DatabaseUtils.parse("jdbc:tc:postgresql:9.6.8:///databasename")
    assertThat(details.dbSystem).isEqualTo("postgresql")
    assertThat(details.dbName).isNull()
  }

  @Test
  fun `detects db system for testcontainers without image tag behind p6spy`() {
    val details =
      DatabaseUtils.parse("jdbc:p6spy:tc:mysql:///databasename?TC_INITSCRIPT=file:init.sql")
    assertThat(details.dbSystem).isEqualTo("mysql")
    assertThat(details.dbName).isNull()
  }

  @Test
  fun `detects db system for aws advanced jdbc wrapper`() {
    val details =
      DatabaseUtils.parse(
        "jdbc:aws-wrapper:postgresql://db.example.com/example?wrapperPlugins=failover2,efm2"
      )
    assertThat(details.dbSystem).isEqualTo("postgresql")
    assertThat(details.dbName).isEqualTo("example")
  }

  @Test
  fun `detects db system for opentelemetry jdbc driver`() {
    val details = DatabaseUtils.parse("jdbc:otel:h2:mem:test")
    assertThat(details.dbSystem).isEqualTo("h2")
    assertThat(details.dbName).isEqualTo("test")
  }

  @Test
  fun `detects db system for log4jdbc`() {
    val details = DatabaseUtils.parse("jdbc:log4jdbc:mysql://localhost:3306/app?useSSL=false")
    assertThat(details.dbSystem).isEqualTo("mysql")
    assertThat(details.dbName).isEqualTo("app")
  }

  @Test
  fun `keeps connection properties out of file-based database names`() {
    // url to expected db.name; the properties may hold the database key or password
    val cases =
      listOf(
        "jdbc:sqlite:sample.db?journal_mode=WAL&password=secret" to "sample.db",
        "jdbc:sqlite:/data/app.db?key=secret" to "/data/app.db",
        "jdbc:sqlite:file:/data/app.db?key=secret#frag" to "/data/app.db",
        "jdbc:sqlite:file:/data/app.db#secret" to "/data/app.db",
        "jdbc:sqlite:file::memory:?cache=shared" to null,
        "jdbc:otel:sqlite:file:sample.db?mode=ro&key=secret" to "sample.db",
        "jdbc:p6spy:sqlite:sample.db?password=secret" to "sample.db",
        "jdbc:hsqldb:file:db?password=secret" to "db",
        "jdbc:h2:file:/data/app?key=secret" to "/data/app",
      )

    cases.forEach { (url, dbName) ->
      assertWithMessage(url).that(DatabaseUtils.parse(url).dbName).isEqualTo(dbName)
    }
  }
}
