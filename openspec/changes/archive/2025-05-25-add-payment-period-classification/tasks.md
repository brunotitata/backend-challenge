## 1. Domain classes

- [x] 1.1 Criar enum `PeriodType` com valores `DAYTIME`, `NIGHTTIME`, `WEEKEND`
- [x] 1.2 Criar data class `PeriodClassification` com campos `periodType: PeriodType` e `periodStart: Instant`
- [x] 1.3 Criar object `PeriodClassifier` com metodo `classify(occurredAt: Instant, zone: ZoneId): PeriodClassification`

## 2. Testes unitarios da classificacao

- [x] 2.1 Testar dia util `05:59:59` → `NIGHTTIME`
- [x] 2.2 Testar dia util `06:00:00` → `DAYTIME`
- [x] 2.3 Testar dia util `17:59:59` → `DAYTIME`
- [x] 2.4 Testar dia util `18:00:00` → `NIGHTTIME`
- [x] 2.5 Testar sabado `00:00:00` → `WEEKEND`
- [x] 2.6 Testar sabado `12:00:00` → `WEEKEND`
- [x] 2.7 Testar domingo `23:59:59` → `WEEKEND`
- [x] 2.8 Testar virada noturna: segunda `23:00` e terca `01:00` com mesmo `periodStart`
- [x] 2.9 Testar `periodStart` do `DAYTIME` = mesmo dia `06:00`
- [x] 2.10 Testar `periodStart` do `WEEKEND` = mesmo dia `00:00`
- [x] 2.11 Testar `periodStart` do `NIGHTTIME` (> 18:00) = mesmo dia `18:00`
- [x] 2.12 Testar `periodStart` do `NIGHTTIME` (< 06:00) = dia anterior `18:00`
- [x] 2.13 Testar que a classificacao funciona sem banco, sem framework

## 3. Build e verificacao

- [x] 3.1 Executar `./gradlew test` e confirmar que todos os testes passam
- [x] 3.2 Verificar que nenhuma dependencia de framework foi adicionada ao modulo `entities`
