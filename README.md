# spring_data_astradb

A minimal Spring Boot app that connects to **DataStax Astra DB** using
**Spring Data for Apache Cassandra** (CQL, through the secure connect bundle) and exposes
a create/read/update/delete (CRUD) REST API for a `Book` entity.

| | |
|---|---|
| Spring Boot | 4.1.1 |
| Java | 25 (Gradle toolchain) |
| Build | Gradle 9.7.1 (wrapper) |
| Driver | Apache Cassandra Java driver 4.19.x (from `spring-boot-starter-data-cassandra`) |

---

## 1. Prerequisites

1. **JDK 25**, for example `brew install openjdk@25`, with `JAVA_HOME` pointing to it.
   Gradle doesn't use the `java` on your `PATH`; see [Java setup](#java-setup).
2. An **Astra DB** (serverless) database with:
   - a keyspace. The app uses `default_keyspace` unless you set `ASTRA_DB_KEYSPACE`.
   - the **secure connect bundle** zip (Astra UI → your database → *Connect* → download bundle).
   - an **application token** (`AstraCS:...`) with a role that can create tables and read/write data.

## 2. Configure credentials

All secrets come from environment variables and none are stored in the repo:

```bash
export ASTRA_DB_APPLICATION_TOKEN='AstraCS:...'
export ASTRA_DB_SECURE_BUNDLE_PATH=/absolute/path/to/secure-connect-<db>.zip
export ASTRA_DB_KEYSPACE=default_keyspace   # optional; this is the default
```

| Variable | Required | Used for |
|---|---|---|
| `ASTRA_DB_APPLICATION_TOKEN` | yes | CQL password (the username is the literal string `token`) |
| `ASTRA_DB_SECURE_BUNDLE_PATH` | yes | Path to the secure connect bundle zip |
| `ASTRA_DB_KEYSPACE` | no | Keyspace name (default `default_keyspace`) |

Don't commit the token or the bundle zip.

## 3. Build, test, run

```bash
./gradlew build      # compile + tests
./gradlew test       # tests only (add --rerun-tasks to force a re-run)
./gradlew bootRun    # start the app on http://localhost:8080
```

### Try the API

```bash
# Create (201, returns the book with a generated id)
curl -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"Dune","author":"Frank Herbert","publishedYear":1965}'

curl localhost:8080/api/books                 # list all          -> 200
curl localhost:8080/api/books/<id>            # get one           -> 200 / 404

curl -X PUT localhost:8080/api/books/<id> -H 'Content-Type: application/json' \
  -d '{"title":"Dune Messiah","author":"Frank Herbert","publishedYear":1969}'   # -> 200 / 404

curl -X DELETE localhost:8080/api/books/<id>  # delete            -> 204 / 404
```

| Method | Path | Success | Not found |
|---|---|---|---|
| `GET` | `/api/books` | 200 | – |
| `GET` | `/api/books/{id}` | 200 | 404 |
| `POST` | `/api/books` | 201 + `Location` header | – |
| `PUT` | `/api/books/{id}` | 200 | 404 |
| `DELETE` | `/api/books/{id}` | 204 | 404 |

You can also check the data in the Astra CQL console: `SELECT * FROM default_keyspace.books;`

### Walkthrough: build, run and watch every CRUD operation

This walkthrough uses two terminals. Terminal 1 runs the app and prints the CQL it sends to
Astra. Terminal 2 sends the HTTP requests. Optionally, keep the Astra CQL console open in a
browser to see the rows change.

**Step 0: check your setup** (in both terminals)

```bash
java -version                       # 25.x
echo $JAVA_HOME                     # .../openjdk@25/...
echo $ASTRA_DB_SECURE_BUNDLE_PATH   # path to the bundle zip
[ -n "$ASTRA_DB_APPLICATION_TOKEN" ] && echo "token set"
```

**Step 1: build and test** (terminal 1)

```bash
cd spring-data-astradb
./gradlew clean build
```

Expect `BUILD SUCCESSFUL`. With the credentials set, both tests run against Astra
(see [Testing](#6-testing)).

**Step 2: start the app with CQL logging** (terminal 1)

```bash
./gradlew bootRun --args='--logging.level.org.springframework.data.cassandra.core.cql.CqlTemplate=DEBUG'
```

This debug logger prints every CQL statement Spring Data runs. On startup you'll see the
table being created, then Tomcat starting:

```
CqlTemplate  : Executing CQL statement [CREATE TABLE IF NOT EXISTS books (id uuid, title text, author text, publishedyear int, PRIMARY KEY (id));]
TomcatWebServer : Tomcat started on port 8080 (http) with context path '/'
SpringDataAstradbApplication : Started SpringDataAstradbApplication in 4.2 seconds
```

Leave it running.

**Step 3: create** (terminal 2)

```bash
curl -s -i -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
  -d '{"title":"Dune","author":"Frank Herbert","publishedYear":1965}'
```

You get `HTTP/1.1 201`, a `Location: /api/books/<id>` header, and the book as JSON with a
generated `id`. Save the id for the next steps:

```bash
ID=<paste the id here>
```

Terminal 1 shows:

```
Executing prepared statement [INSERT INTO books (id,title,author,publishedyear) VALUES (?,?,?,?)]
```

In the Astra CQL console, `SELECT * FROM default_keyspace.books;` now returns the row.

**Step 4: read**

```bash
curl -s localhost:8080/api/books        # list: a JSON array containing the book
curl -s localhost:8080/api/books/$ID    # one book
```

Terminal 1 shows `SELECT * FROM books` for the list, then `SELECT * FROM books WHERE id=? LIMIT 1`.

**Step 5: update**

```bash
curl -s -X PUT localhost:8080/api/books/$ID -H 'Content-Type: application/json' \
  -d '{"title":"Dune Messiah","author":"Frank Herbert","publishedYear":1969}'
```

Returns `200` and the updated book. Terminal 1 shows an existence check, then the write:

```
Executing prepared statement [SELECT id FROM books WHERE id=? LIMIT 1]
Executing prepared statement [INSERT INTO books (id,title,author,publishedyear) VALUES (?,?,?,?)]
```

The update is also an `INSERT`, because in Cassandra an `INSERT` for an existing primary key
overwrites the row (an "upsert"). The existence check is what makes `PUT` return 404 for an
unknown id instead of creating a new row. The CQL console now shows `Dune Messiah` / `1969`.

**Step 6: delete**

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE localhost:8080/api/books/$ID   # 204
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/books/$ID             # 404
```

Terminal 1 shows `SELECT id FROM books WHERE id=? LIMIT 1` then `DELETE FROM books WHERE id=?`.
The row is gone from the CQL console.

**Step 7: stop the app**: press `Ctrl+C` in terminal 1.

Without the `--args` logging flag, the app behaves the same but doesn't print the CQL.

---

## 4. How it was built

1. **Generated with Spring Initializr**: Gradle (Groovy), Spring Boot 4.1.1, dependency
   *Spring Data for Apache Cassandra*. The requested package `com.madhavan.demo.spring-data-astradb`
   isn't a valid Java package name, so Initializr used `com.madhavan.demo.spring_data_astradb`
   (see `HELP.md`).
2. **Added the web starter** `spring-boot-starter-webmvc` (the Boot 4 name for the
   Spring MVC starter) for the REST layer.
3. **Astra connection**: `application.properties` + `AstraConfig` (see section 5).
4. **Domain layer** in the `book` package: entity, repository, controller.
5. **Tests** against a live Astra database, which only run when credentials are present.
6. **Moved to Java 25** with the toolchain in `build.gradle` + `gradle/gradle-daemon-jvm.properties`,
   plus the Foojay toolchain resolver in `settings.gradle` (see [Java setup](#java-setup)).

### Project layout

```
settings.gradle                      project name + Foojay toolchain resolver
build.gradle                         dependencies + Java 25 toolchain
gradle/gradle-daemon-jvm.properties  runs the Gradle daemon on JDK 25 (generated by updateDaemonJvm)
src/main/resources/application.properties
src/main/java/com/madhavan/demo/spring_data_astradb/
  SpringDataAstradbApplication.java  @SpringBootApplication entry point
  config/AstraConfig.java            points the CQL session at the secure connect bundle
  book/Book.java                     @Table("books") entity
  book/BookRepository.java           CassandraRepository<Book, UUID>
  book/BookController.java           REST controller at /api/books
src/test/java/com/madhavan/demo/spring_data_astradb/
  SpringDataAstradbApplicationTests.java   checks the context loads (connects to Astra)
  book/BookRepositoryTests.java            CRUD round trip against Astra
```

---

## 5. How configuration is loaded

### 5.1 `application.properties`

Spring Boot loads `src/main/resources/application.properties` from the classpath on
startup. Each `${VAR:default}` placeholder is resolved from the Spring `Environment`, which
includes OS environment variables. That's how the `ASTRA_DB_*` variables get in:

```properties
spring.cassandra.keyspace-name=${ASTRA_DB_KEYSPACE:default_keyspace}
spring.cassandra.username=token
spring.cassandra.password=${ASTRA_DB_APPLICATION_TOKEN:}
spring.cassandra.schema-action=create-if-not-exists
spring.cassandra.request.timeout=10s
spring.cassandra.connection.connect-timeout=10s
spring.cassandra.connection.init-query-timeout=10s
astra.secure-connect-bundle=${ASTRA_DB_SECURE_BUNDLE_PATH:}
```

- `spring.cassandra.*` are standard Spring Boot properties. Boot's Cassandra
  auto-configuration reads them to build the driver's `CqlSession`: keyspace, credentials,
  and timeouts. The longer timeouts allow for the round trip to a cloud database.
- `spring.cassandra.schema-action=create-if-not-exists` tells Spring Data to create the table
  for each `@Table` entity on startup (`CREATE TABLE IF NOT EXISTS books ...`). The
  **keyspace** must already exist, because Astra keyspaces are created in the Astra UI.
- `astra.secure-connect-bundle` is a property specific to this app, read by `AstraConfig`.

Any property can also be overridden without editing the file. Spring Boot's precedence
order is: command-line args > OS environment variables > `application.properties`. For example:

```bash
./gradlew bootRun --args='--spring.cassandra.keyspace-name=other_ks'
SPRING_CASSANDRA_KEYSPACE_NAME=other_ks ./gradlew bootRun     # relaxed binding
```

### 5.2 `AstraConfig`: the secure connect bundle

Spring Boot has no property for the Astra bundle, so `AstraConfig` registers a
`CqlSessionBuilderCustomizer`. Boot calls it while building the `CqlSession`:

```java
return builder -> builder.withCloudSecureConnectBundle(bundle);
```

The bundle contains the endpoints, the local datacenter and the TLS certificates, so
`contact-points`, `local-datacenter` and SSL settings are deliberately **not** set. If the
bundle path is empty, the app stops at startup with
`ASTRA_DB_SECURE_BUNDLE_PATH must point to the Astra secure connect bundle zip`.

### 5.3 Startup sequence

1. `SpringDataAstradbApplication.main` starts Spring Boot.
2. `application.properties` is loaded and the `${ASTRA_DB_*}` placeholders are resolved from the environment.
3. Cassandra auto-configuration builds a `CqlSessionBuilder` from `spring.cassandra.*`, then
   applies `AstraConfig`'s customizer, which adds the secure connect bundle.
4. The `CqlSession` connects to Astra over TLS as `token` / `<your token>` and uses the keyspace.
5. Spring Data maps `Book`, runs `CREATE TABLE IF NOT EXISTS books`, and creates the
   `BookRepository` proxy.
6. Spring MVC starts Tomcat on port 8080 with `BookController`.

---

## 6. Testing

Both tests are `@SpringBootTest` integration tests that talk to the **real Astra database**.
There's no mock or embedded Cassandra.

| Test | What it checks |
|---|---|
| `SpringDataAstradbApplicationTests.contextLoads` | the full context starts and connects to Astra |
| `BookRepositoryTests.crudRoundTrip` | save → find → update → find → delete → find is empty |

Both are annotated with
`@EnabledIfEnvironmentVariable(named = "ASTRA_DB_APPLICATION_TOKEN", matches = ".+")`:

- **Token not set**: the tests are **skipped**, so `./gradlew build` still passes on a
  machine without credentials.
- **Token set**: the tests run. `ASTRA_DB_SECURE_BUNDLE_PATH` must also be set, or the
  context fails to start.

```bash
./gradlew test --rerun-tasks
open build/reports/tests/test/index.html   # HTML report
```

To check whether tests ran or were skipped, look at `build/test-results/test/*.xml`
(`skipped="0"` means they ran).

---

## <a name="java-setup"></a>7. Java setup

The project builds and runs on **Java 25**, configured in three places:

- `build.gradle` → `java.toolchain.languageVersion = 25`: compile, test and `bootRun` use JDK 25.
- `gradle/gradle-daemon-jvm.properties` → `toolchainVersion=25`: the Gradle daemon itself
  runs on JDK 25. This takes precedence over an older `org.gradle.java.home` in
  `~/.gradle/gradle.properties`.
- `settings.gradle` → Foojay toolchain resolver: if Gradle can't find an installed JDK 25,
  it downloads one into `~/.gradle/jdks` instead of failing.

#### How Gradle finds JDK 25

Gradle does **not** use the `java` on your `PATH`, so `java -version` printing 25 isn't
enough. It looks at:

1. the JDK that `JAVA_HOME` points to
2. JDKs registered with macOS (`/usr/libexec/java_home -V`), SDKMAN, asdf and similar tools
3. any paths in the `org.gradle.java.installations.paths` property

Homebrew's `openjdk@25` isn't registered with macOS, so on a Homebrew setup set `JAVA_HOME`,
for example in `~/.zshrc` / `~/.zshenv`:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home   # Intel Homebrew
# Apple Silicon Homebrew: /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

With `JAVA_HOME` set this way, a fresh `./gradlew build` uses the installed JDK and
downloads nothing.

**Alternatives:**
- Register the Homebrew JDK with macOS so every tool finds it (needs `sudo`):
  `sudo ln -sfn /usr/local/opt/openjdk@25/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-25.jdk`
- Point Gradle at it for a single build:
  `./gradlew build -Porg.gradle.java.installations.paths=/path/to/jdk-25`
- Do nothing, and the Foojay resolver downloads JDK 25 for Gradle.

---

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Gradle requires JVM 17 or later to run ... JVM 11` | `gradle/gradle-daemon-jvm.properties` is missing, or JDK 25 isn't installed |
| `Unable to download toolchain matching ... languageVersion=25` | Gradle can't find JDK 25 and has no download URLs. Set `JAVA_HOME` to JDK 25 (see [Java setup](#java-setup)), or regenerate the daemon file with `./gradlew updateDaemonJvm --jvm-version=25` |
| `java -version` shows 25 but Gradle uses 11 | Gradle ignores `PATH`; set `JAVA_HOME` |
| `Port 8080 was already in use` | another `bootRun` is still running; stop it, or add `--server.port=8081` to `--args` |
| `ASTRA_DB_SECURE_BUNDLE_PATH must point to ...` | export `ASTRA_DB_SECURE_BUNDLE_PATH` |
| `AuthenticationException` | wrong or expired token, or the username isn't `token` |
| `InvalidKeyspaceException` / keyspace does not exist | create the keyspace in the Astra UI, or set `ASTRA_DB_KEYSPACE` |
| Tests show as skipped | `ASTRA_DB_APPLICATION_TOKEN` isn't set in the shell that ran Gradle |
| Driver connection timeouts | the database is hibernated or still resuming; open it in the Astra UI and retry |
| `WARNING: sun.misc.Unsafe ...` / `System::load` on startup | harmless JDK 25 warnings from the driver's shaded Guava / jnr libraries |

## 9. Reference Documentation

For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/gradle-plugin/packaging-oci-image.html)
* [Spring Data for Apache Cassandra®](https://docs.spring.io/spring-boot/4.1.1/reference/data/nosql.html#data.nosql.cassandra)

### Guides
The following guides illustrate how to use some features concretely:

* [Spring Data for Apache Cassandra®](https://spring.io/guides/gs/accessing-data-cassandra/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)
---