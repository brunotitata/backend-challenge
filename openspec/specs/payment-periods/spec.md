## Purpose
Especifica requisitos funcionais e de comportamento para esta capability.n

## Requirements

### Requirement: Classificar periodo como DAYTIME em dias uteis das 06:00 as 18:00
O sistema MUST classificar como `DAYTIME` qualquer `occurredAt` que, no timezone operacional (`America/Sao_Paulo`), ocorra em um dia util (segunda a sexta) entre `06:00:00` (inclusive) e `18:00:00` (exclusive).

#### Scenario: Dia util as 06:00:00 e DAYTIME
- **GIVEN** um `occurredAt` em uma segunda-feira as `06:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `DAYTIME`

#### Scenario: Dia util as 17:59:59 e DAYTIME
- **GIVEN** um `occurredAt` em uma segunda-feira as `17:59:59` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `DAYTIME`

### Requirement: Classificar periodo como NIGHTTIME em dias uteis das 18:00 as 06:00
O sistema MUST classificar como `NIGHTTIME` qualquer `occurredAt` que, no timezone operacional, ocorra em um dia util (segunda a sexta) entre `18:00:00` (inclusive) e `06:00:00` do dia seguinte (exclusive).

#### Scenario: Dia util as 05:59:59 e NIGHTTIME
- **GIVEN** um `occurredAt` em uma segunda-feira as `05:59:59` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `NIGHTTIME`

#### Scenario: Dia util as 18:00:00 e NIGHTTIME
- **GIVEN** um `occurredAt` em uma segunda-feira as `18:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `NIGHTTIME`

#### Scenario: Dia util as 23:59:59 e NIGHTTIME
- **GIVEN** um `occurredAt` em uma segunda-feira as `23:59:59` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `NIGHTTIME`

### Requirement: Classificar periodo como WEEKEND em sabados e domingos
O sistema MUST classificar como `WEEKEND` qualquer `occurredAt` que, no timezone operacional, ocorra em um sabado ou domingo, independentemente do horario. Final de semana tem prioridade sobre horario.

#### Scenario: Sabado as 00:00:00 e WEEKEND
- **GIVEN** um `occurredAt` em um sabado as `00:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `WEEKEND`

#### Scenario: Sabado as 12:00:00 e WEEKEND
- **GIVEN** um `occurredAt` em um sabado as `12:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `WEEKEND`

#### Scenario: Sabado as 22:00:00 e WEEKEND (prioridade sobre horario)
- **GIVEN** um `occurredAt` em um sabado as `22:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `WEEKEND`

#### Scenario: Domingo as 23:59:59 e WEEKEND
- **GIVEN** um `occurredAt` em um domingo as `23:59:59` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `WEEKEND`

### Requirement: Calcular periodStart para DAYTIME
O `periodStart` para `DAYTIME` MUST ser o mesmo dia local as `06:00:00`.

#### Scenario: periodStart do DAYTIME e o mesmo dia as 06:00
- **GIVEN** um `occurredAt` em uma segunda-feira as `10:30:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodStart` MUST ser o mesmo dia as `06:00:00` no mesmo timezone

### Requirement: Calcular periodStart para WEEKEND
O `periodStart` para `WEEKEND` MUST ser o mesmo dia local as `00:00:00`.

#### Scenario: periodStart do WEEKEND e o mesmo dia as 00:00
- **GIVEN** um `occurredAt` em um sabado as `14:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodStart` MUST ser o mesmo dia as `00:00:00` no mesmo timezone

### Requirement: Calcular periodStart para NIGHTTIME apos 18:00
Para horario noturno a partir de `18:00:00`, o `periodStart` MUST ser o mesmo dia as `18:00:00`.

#### Scenario: periodStart do NIGHTTIME apos 18:00 e o mesmo dia as 18:00
- **GIVEN** um `occurredAt` em uma segunda-feira as `23:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `NIGHTTIME`
- **AND** o `periodStart` MUST ser a segunda-feira as `18:00:00`

### Requirement: Calcular periodStart para NIGHTTIME antes de 06:00
Para horario noturno antes de `06:00:00`, o `periodStart` MUST ser o dia anterior as `18:00:00`.

#### Scenario: periodStart do NIGHTTIME antes de 06:00 e o dia anterior as 18:00
- **GIVEN** um `occurredAt` em uma terca-feira as `01:00:00` no timezone `America/Sao_Paulo`
- **WHEN** o `PeriodClassifier.classify()` for chamado
- **THEN** o `periodType` MUST ser `NIGHTTIME`
- **AND** o `periodStart` MUST ser a segunda-feira as `18:00:00`

### Requirement: Virada de dia noturno mantem mesma chave de consumo
Segunda `23:00` e terca `01:00` MUSTM ter o mesmo `periodStart` (ambos pertencem a mesma noite iniciada segunda as `18:00`).

#### Scenario: Segunda 23:00 e terca 01:00 tem mesmo periodStart
- **GIVEN** um `occurredAt` em uma segunda-feira as `23:00:00` no timezone `America/Sao_Paulo`
- **AND** outro `occurredAt` em uma terca-feira as `01:00:00` no mesmo timezone
- **WHEN** ambos forem classificados pelo `PeriodClassifier.classify()`
- **THEN** ambos MUSTM ter `periodType` igual a `NIGHTTIME`
- **AND** ambos MUSTM ter o mesmo `periodStart` (segunda-feira as `18:00:00`)

### Requirement: Classificacao independe de framework e banco
O `PeriodClassifier` MUST ser uma funcao pura que recebe `Instant` e `ZoneId` e retorna `PeriodClassification`. Nao MUST depender de Ktor, jOOQ, banco de dados ou qualquer framework externo.

#### Scenario: Teste unitario sem dependencias
- **GIVEN** a classe `PeriodClassifier`
- **WHEN** instanciada em um teste unitario sem任何 framework
- **THEN** `classify()` MUST funcionar sem banco, sem HTTP e sem configuracao externa
