# Estrutura Clean Architecture do Payment API

## Visao Geral

O projeto e organizado em **4 camadas** com modulos Gradle separados, seguindo o principio de que dependencias sempre apontam **para dentro** — detalhes externos ficam nas bordas, enquanto regras de negocio e entidades permanecem independentes de frameworks, banco de dados, HTTP e infraestrutura.

```
project-root/
|-- application/       # Bootstrap da aplicacao
|-- core/              # Regras de negocio e entidades do dominio
|-- boundary-context/  # Contratos, portas e objetos de fronteira
`-- adapters/          # Implementacoes de entrada, saida e infraestrutura
```

---

## Camadas e Responsabilidades

### `application/` — Bootstrap

Inicializacao da aplicacao, configuracao Spring Boot, DI e wiring entre adapters e casos de uso.

- **Modulo Gradel:** `:application`
- **Pacote base:** `com.trace.payment`
- **Depende de:** `entities`, `use-cases`, `database-boundary`, `exceptions`, `input-boundary`, `web`, `database-adapter`
- **Arquivos:**
  - `Application.kt` — Entrypoint `@SpringBootApplication` com `scanBasePackages`
  - `application.yml` — Configuracoes de porta, datasource, Flyway, JPA

### `core/` — Dominio

Entidades, regras de negocio e casos de uso. Nao conhece HTTP, banco de dados, frameworks ou qualquer detalhe tecnico.

- **Submodulos:**
  - `entities/` (`:entities`) — Entidades puras sem dependencias externas. Ex: `WalletEntity`, `PaymentEntity`, `PolicyEntity`
  - `use-cases/` (`:use-cases`) — Implementacao dos casos de uso. Depende apenas de `entities` e de contratos definidos em `boundary-context`

### `boundary-context/` — Contratos / Portas

Interfaces, portas de entrada, portas de saida, DTOs de fronteira e excecoes padronizadas. Funciona como contrato entre dominio e infraestrutura.

- **Submodulos:**
  - `input-boundary/` (`:input-boundary`) — Portas de entrada (ex: `CreateWalletInputSpec`)
  - `database-boundary/` (`:database-boundary`) — Contratos de persistencia (ex: `WalletDAOSpec`)
  - `exceptions/` (`:exceptions`) — Excecoes padronizadas do dominio (ex: `ValidationException`)

### `adapters/` — Infraestrutura

Implementacoes concretas de interfaces. Conhece frameworks, banco de dados e tecnologias externas.

- **Submodulos:**
  - `web/` (`:web`) — Controllers REST, DTOs HTTP, mappers e tratamento global de erros
  - `database-adapter/` (`:database-adapter`) — DAOs, models de persistencia, mappers e migracoes Flyway
  - `input-adapter/` (previsto) — Adaptadores para portas de entrada
  - `external-service-adapter/` (previsto) — Clients HTTP e integracoes externas

---

## Regra de Dependencia

```
application -> web + database-adapter + input-boundary + database-boundary + exceptions
application -> entities + use-cases
web          -> exceptions
database-adapter -> database-boundary
input-boundary   -> entities
database-boundary -> entities
use-cases       -> entities
entities        -> (nenhuma)
exceptions      -> (nenhuma)
```

Em formato visual:

```
Controller (adapters/web)
    v
Input Adapter (boundary-context/input-boundary)
    v
UseCase (core/use-cases)
    v
Database Boundary (boundary-context/database-boundary)
    v
Database Adapter (adapters/database-adapter)
    v
Banco de Dados (PostgreSQL)
```

---

## Estrutura Interna Completa

```
project-root/
|
|-- application/
|   |-- build.gradle.kts
|   `-- src/
|       |-- main/
|       |   |-- kotlin/com/trace/payment/
|       |   |   `-- Application.kt
|       |   `-- resources/
|       |       `-- application.yml
|       `-- test/kotlin/com/trace/payment/
|           `-- ApplicationModuleTest.kt
|
|-- adapters/
|   |-- web/
|   |   |-- build.gradle.kts
|   |   `-- src/main/kotlin/com/trace/payment/adapters/web/
|   |       |-- controllers/
|   |       |   |-- HealthController.kt
|   |       |   `-- GlobalExceptionHandler.kt
|   |       |-- dtos/
|   |       |   |-- HealthResponseDTO.kt
|   |       |   `-- ErrorResponseDTO.kt
|   |       `-- mappers/
|   |
|   |-- database-adapter/
|   |   |-- build.gradle.kts
|   |   `-- src/
|   |       |-- main/kotlin/com/trace/payment/adapters/database/
|   |       |   |-- dao/
|   |       |   |-- models/
|   |       |   |-- mappers/
|   |       |   `-- config/
|   |       `-- resources/db/migration/
|   |           `-- V1__foundation.sql
|   |
|   |-- input-adapter/      (previsto)
|   `-- external-service-adapter/  (previsto)
|
|-- boundary-context/
|   |-- input-boundary/
|   |   |-- build.gradle.kts
|   |   `-- src/main/kotlin/com/trace/payment/boundary/input/
|   |
|   |-- database-boundary/
|   |   |-- build.gradle.kts
|   |   `-- src/main/kotlin/com/trace/payment/boundary/database/
|   |
|   |-- exceptions/
|   |   |-- build.gradle.kts
|   |   `-- src/main/kotlin/com/trace/payment/boundary/exceptions/
|   |       `-- ValidationException.kt
|   |
|   `-- common/     (previsto: tipos, constantes, utilitarios)
|
|-- core/
|   |-- entities/
|   |   |-- build.gradle.kts
|   |   `-- src/main/kotlin/com/trace/payment/core/entities/
|   |
|   `-- use-cases/
|       |-- build.gradle.kts
|       `-- src/main/kotlin/com/trace/payment/core/usecase/
|
|-- docs/
|   |-- clean-architecture-structure.md
|   `-- cronograma-openspec.md
|
|-- docker-compose.yml
|-- build.gradle.kts
|-- settings.gradle
`-- README.md
```

---

## Pacotes e Convencao de Nomes

**Pacote base unico:** `com.trace.payment`

### Packages vs. Diretorios

Observacao tecnica: `boundary-context` e o nome fisico/logico da camada nos diretorios e na documentacao. Packages Kotlin nao podem conter hifen; nesse caso, o pacote usa uma forma valida como `boundary`, mantendo os diretorios em kebab-case.

### Convencao de Nomes para Pastas

Todas as pastas seguem **kebab-case** conforme definido em `AGENTS.md`.

### Convencao de Nomes para Classes

| Tipo | Sufixo | Exemplo |
|------|--------|---------|
| Contrato de caso de uso | `UseCaseSpec` | `CreateWalletUseCaseSpec` |
| Implementacao do caso de uso | `UseCaseSpecImpl` | `CreateWalletUseCaseSpecImpl` |
| Contrato de entrada | `InputSpec` | `CreateWalletInputSpec` |
| Implementacao de entrada | `InputSpecImpl` | `CreateWalletInputSpecImpl` |
| Contrato de persistencia | `DAOSpec` | `WalletDAOSpec` |
| Implementacao de persistencia | `DAOSpecImpl` | `WalletDAOSpecImpl` |
| Entidade de dominio | `Entity` | `WalletEntity` |
| Objeto de fronteira | `BO` | `WalletBO` |
| Modelo de persistencia | `Model` | `WalletModel` |
| DTO HTTP | `DTO` | `CreateWalletRequestDTO` |

---

## Configuracao Gradle Multi-Modulo

O projeto utiliza **Gradle multi-modulo** onde cada camada e subcamada da Clean Architecture e um modulo Gradle独立 com seu proprio `build.gradle.kts`. Isso garante isolamento de dependencias e respeito a regra de dependencia (dependencias apontam para dentro) em nivel de build.

### Descoberta Dinamica de Modulos

Diferente de projetos que listam cada modulo manualmente, este projeto usa descoberta **dinamica** em `settings.gradle`:

```kotlin
rootProject.name = 'payment-api'

include 'application'
include 'adapters'
include 'boundary-context'
include 'core'

// Cada subdiretorio com build.gradle.kts dentro de core/ vira um modulo Gradle
file('core').eachDir { File dir ->
    if (new File(dir, 'build.gradle').exists() || new File(dir, 'build.gradle.kts').exists()) {
        include dir.name
        project(":${dir.name}").projectDir = dir
    }
}

// O mesmo para adapters/ e boundary-context/
file('adapters').eachDir { dir ->
    if (new File(dir, 'build.gradle').exists() || new File(dir, 'build.gradle.kts').exists()) {
        include dir.name
        project(":${dir.name}").projectDir = dir
    }
}

file('boundary-context').eachDir { dir ->
    if (new File(dir, 'build.gradle').exists() || new File(dir, 'build.gradle.kts').exists()) {
        include dir.name
        project(":${dir.name}").projectDir = dir
    }
}
```

**Como funciona:** o `settings.gradle` inclui `core/`, `adapters/` e `boundary-context/` como diretorios agregadores. Em tempo de configuracao, ele itera sobre cada subdiretorio dentro deles e, se encontrar um `build.gradle.kts`, registra automaticamente como modulo Gradle com o nome do diretorio.

**Importante:** o modulo `:application` e incluido explicitamente porque esta na raiz, e nao dentro de um agregador.

### Configuracao Compartilhada (Root `build.gradle.kts`)

O arquivo raiz define plugins e configuracao compartilhada entre todos os submodulos:

```kotlin
plugins {
    kotlin("jvm") version "1.9.20" apply false
    kotlin("plugin.spring") version "1.9.20" apply false
    id("org.springframework.boot") version "3.2.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}
```

`apply false` significa que os plugins sao declarados mas **nao aplicados** na raiz — cada submodulo decide quais aplicar.

**Configuracao para todos os submodulos:**

```kotlin
subprojects {
    apply(plugin = "io.spring.dependency-management")

    // BOM do Spring Boot para gerenciar versoes
    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        }
    }

    // Se o modulo usar kotlin jvm, adiciona kotlin-test e JUnit Platform
    plugins.withId("org.jetbrains.kotlin.jvm") {
        dependencies {
            "testImplementation"("org.jetbrains.kotlin:kotlin-test:1.9.20")
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}
```

Isso garante que **todo** modulo tenha acesso ao BOM do Spring Boot e, se usar Kotlin, ganhe automaticamente `kotlin-test` e `useJUnitPlatform()`.

### Categorias de Modulos

Existem dois tipos de modulo neste projeto:

**1. Modulos Spring Boot (com `application` plugin)**

Apenas o modulo `:application` entra nesta categoria:

```kotlin
// application/build.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")     // Habilita suporte Spring (open classes, etc)
    id("org.springframework.boot")  // Gera fat JAR, task bootRun
    application                     // Task run
}

application {
    mainClass.set("com.trace.payment.ApplicationKt")
}

dependencies {
    implementation(project(":entities"))
    implementation(project(":use-cases"))
    implementation(project(":database-boundary"))
    implementation(project(":exceptions"))
    implementation(project(":input-boundary"))
    implementation(project(":database-adapter"))
    implementation(project(":web"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
}
```

**2. Modulos Kotlin JVM puros (sem Spring Boot plugin)**

Todos os demais modulos usam apenas `kotlin("jvm")`:

```kotlin
// Exemplo: core/entities/build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    // Sem dependencias — entidades sao puras
}
```

```kotlin
// Exemplo: adapters/web/build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":exceptions"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Nota: usa dependencia do Spring Boot (via BOM) mas SEM o plugin spring-boot
}
```

### Como Adicionar um Novo Modulo

Para criar um novo submodulo (ex: `adapters/external-service-adapter/`):

1. **Criar o diretorio** com a estrutura `src/main/kotlin/...`:

   ```
   adapters/external-service-adapter/
   |-- build.gradle.kts
   `-- src/main/kotlin/com/trace/payment/adapters/externalservice/
       |-- clients/
       `-- mappers/
   ```

2. **Criar `build.gradle.kts`** com os plugins e dependencias necessarias:

   ```kotlin
   plugins {
       kotlin("jvm")
   }

   dependencies {
       implementation(project(":external-service-boundary"))
       implementation("org.springframework.boot:spring-boot-starter-web")
   }
   ```

3. **Nao e necessario modificar `settings.gradle`** — o `eachDir` em `adapters/` detectara automaticamente o novo diretorio e o registrara como modulo `:external-service-adapter`.

4. **Adicionar a dependencia** no modulo que for consumi-lo (provavelmente `:application`):

   ```kotlin
   // application/build.gradle.kts
   dependencies {
       implementation(project(":external-service-adapter"))
   }
   ```

### Modulos Atualmente Registrados

| Modulo Gradle | Caminho | Plugins | Depende de |
|---------------|---------|---------|------------|
| `:application` | `application/` | `kotlin("jvm")`, `kotlin("plugin.spring")`, `org.springframework.boot`, `application` | `entities`, `use-cases`, `database-boundary`, `exceptions`, `input-boundary`, `web`, `database-adapter` |
| `:web` | `adapters/web/` | `kotlin("jvm")` | `exceptions`, `spring-boot-starter-web` |
| `:database-adapter` | `adapters/database-adapter/` | `kotlin("jvm")` | `database-boundary`, `spring-boot-starter-data-jpa`, `postgresql`, `flyway` |
| `:input-boundary` | `boundary-context/input-boundary/` | `kotlin("jvm")` | `entities` |
| `:database-boundary` | `boundary-context/database-boundary/` | `kotlin("jvm")` | `entities` |
| `:exceptions` | `boundary-context/exceptions/` | `kotlin("jvm")` | (nenhuma) |
| `:entities` | `core/entities/` | `kotlin("jvm")` | (nenhuma) |
| `:use-cases` | `core/use-cases/` | `kotlin("jvm")` | `entities` |

---

## Tecnologias

| Componente | Tecnologia |
|------------|------------|
| Linguagem | Kotlin 1.9.20 |
| Framework | Spring Boot 3.2.5 (Spring Web MVC) |
| Build | Gradle Kotlin DSL |
| Banco | PostgreSQL 16 |
| Migracoes | Flyway |
| Testes | JUnit 5, MockMvc, Testcontainers |
| Container | Docker Compose |
