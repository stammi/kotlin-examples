package me.qoomon.examples


import com.impossibl.postgres.jdbc.PGDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import strikt.api.expectThat

class DataSourceTest {

    val postgresContainer = PostgreSQLContainer<Nothing>("postgres:10.7")

    @BeforeEach
    fun beforeEach() {
        postgresContainer.start()
    }

    @AfterEach
    fun afterEach() {
        postgresContainer.stop()
    }

    @Test
    fun `expect reconnect after failure`() {
        // GIVEN
// implementation("com.impossibl.pgjdbc-ng:pgjdbc-ng:0.8.4")
        val dataSource = PGDataSource().apply {
            serverName = postgresContainer.getContainerIpAddress()
            port = postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
            databaseName = postgresContainer.getDatabaseName()
            user = postgresContainer.getUsername()
            password = postgresContainer.getPassword()
            networkTimeout = 5
//                this.sslMode = "require"
        }

// implementation("org.postgresql:postgresql:42.2.12")
//            val dataSource = PGSimpleDataSource().apply {
//                serverNames += postgresContainer.getContainerIpAddress()
//                portNumbers += postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
//                databaseName = postgresContainer.getDatabaseName()
//                user = postgresContainer.getUsername()
//                password = postgresContainer.getPassword()
//
//                connectTimeout = 5
//                socketTimeout = 5
////                sslMode = "require"
//            }

// implementation("com.zaxxer:HikariCP:3.2.0")
//        val pooledDatasource = HikariDataSource().apply {
//            maximumPoolSize = 1
//            connectionTimeout = 10.seconds.toLongMilliseconds()
//            initializationFailTimeout = -1
//            this.dataSource = dataSource
//        }

        val database = Database.connect(dataSource)

        println("database created")

        // WHEN
        repeat(3) { index ->
            if (index % 2 == 0) {
                println("Database: Starting...")
                postgresContainer.start()
                dataSource.apply {
                    serverName = postgresContainer.getContainerIpAddress()
                    port = postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                }
                println("Database: Running")
            } else {
                println("Database: Stopping...")
                postgresContainer.stop()
                println("Database: Stopped")
            }

            val result = runCatching {
                transaction(database) {
                    addLogger(StdOutSqlLogger)

                    val resultSet = exec("select 1") {
                        generateSequence { it.takeIf { it.next() } }
                            .map { rs ->
                                rs.getInt(1)
                            }
                            .toList()
                    }

                    println("$index -> ${resultSet?.joinToString()}")
                }
            }
            if (index % 2 == 0) {
                expectThat(result).isSuccess()
                println("SUCCESS")
            } else {
                expectThat(result).isFailure()
                println("FAILURE")
            }
        }
    }

    @Test
    fun `expect on the fly password change succeed`() {
        // GIVEN
        val datasource = PGSimpleDataSource().apply {
            setUrl(postgresContainer.getJdbcUrl())
            user = postgresContainer.getUsername()
            password = "wrong"
        }

        val database = Database.connect(datasource)

        println("database created")

        // WHEN
        repeat(10) { index ->
            if (index % 2 == 0) {
                datasource.password = postgresContainer.getPassword()
                println("Password: VALID")
            } else {
                datasource.password = "wrong"
                println("Password: WRONG")
            }

            val result = runCatching {
                transaction(database) {
                    addLogger(StdOutSqlLogger)

                    val resultSet = exec("select 1") {
                        generateSequence { it.takeIf { it.next() } }
                            .map { rs ->
                                rs.getInt(1)
                            }
                            .toList()
                    }

                    println("$index -> ${resultSet?.joinToString()}")
                }
            }
            if (index % 2 == 0) {
                expectThat(result).isSuccess()
                println("SUCCESS")
            } else {
                expectThat(result).isFailure()
                println("FAILURE")
            }
        }
    }
}