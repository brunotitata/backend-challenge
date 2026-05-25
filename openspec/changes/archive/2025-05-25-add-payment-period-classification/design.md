## Context

O dominio atualmente possui campos de limite por periodo (`daytimeDailyLimit`, `nighttimeDailyLimit`, `weekendDailyLimit`) no `PolicyEntity`, mas nao ha codigo para classificar um `Instant` em um periodo. O `ValueLimitEvaluator` e um stub que sempre retorna `true`. Antes de implementar o processamento de pagamento com limite por periodo (Fatia 4), precisamos isolar e testar a logica de classificacao.

## Goals / Non-Goals

**Goals:**
- Criar um enum `PeriodType` (DAYTIME, NIGHTTIME, WEEKEND)
- Criar um `PeriodClassifier` que, dado `Instant` e `ZoneId`, retorna `PeriodType` e `periodStart`
- A classificacao deve ser puramente funcional, sem dependencia de framework, banco ou HTTP
- O timezone operacional do sistema sera `America/Sao_Paulo` (BRT/BRST)
- Testes unitarios para todas as bordas especificadas no cronograma

**Non-Goals:**
- Persistencia de consumo de limite
- Criacao de pagamentos
- Conciliacao de periodos entre fusos diferentes
- Suporte a multiplos timezones por carteira

## Decisions

### Decisao 1: Localizacao do codigo
- **Decisao:** Criar as classes no modulo `core/entities`, pacote `com.trace.payment.core.entities`
- **Justificativa:** O modulo `entities` e o unico sem dependencias externas (Ktor, jOOQ, banco), permitindo testes unitarios puros e reutilizacao por qualquer use case ou evaluator
- **Alternativa considerada:** Novo modulo `core/period`. Rejeitado por adicionar complexidade desnecessaria de build para um conjunto pequeno de classes

### Decisao 2: Timezone operacional
- **Decisao:** Usar `America/Sao_Paulo` como timezone padrao para classificacao de periodo
- **Justificativa:** O cronograma (item "Datas, Horarios e Timezone") exige que o timezone seja definido explicitamente. `America/Sao_Paulo` e o fuso horario mais comum para sistemas financeiros brasileiros, cobre BRT (UTC-3) e BRST (UTC-2) com horario de verao
- **Impacto:** O `occurredAt` informado no pagamento (em UTC) sera convertido para `America/Sao_Paulo` antes da classificacao

### Decisao 3: Estrutura das classes
- **Decisao:** Criar `PeriodType` (enum), `PeriodClassification` (data class com `periodType` e `periodStart`), e `PeriodClassifier` (object)
- **Justificativa:** Separar o tipo do periodo, o resultado da classificacao e a logica de classificacao mantem cada responsabilidade isolada e testavel
- **Alternativa considerada:** Funcao unica retornando par. Rejeitado porque `periodStart` precisa ser calculado com regras diferentes por periodo, e um objeto nomeado e mais expressivo

### Decisao 4: Calculo do `periodStart`
- `DAYTIME`: `periodStart` = `occurredAt` ajustado para `06:00:00` do mesmo dia local
- `WEEKEND`: `periodStart` = `occurredAt` ajustado para `00:00:00` do mesmo dia local
- `NIGHTTIME` (> 18:00): `periodStart` = mesmo dia as `18:00:00` local
- `NIGHTTIME` (< 06:00): `periodStart` = dia anterior as `18:00:00` local

## Risks / Trade-offs

- **[Fuso fixo]** Usar `America/Sao_Paulo` fixo significa que se o sistema operar em outro fuso no futuro, sera necessario alterar a configuracao. Mitigacao: o `ZoneId` e parametrizavel no `PeriodClassifier`, permitindo troca futura sem modificar a logica interna.
- **[Horario de verao]** `America/Sao_Paulo` tem horario de verao. As bordas `06:00` e `18:00` podem cair em horarios inexistentes ou ambíguos na transicao. Mitigacao: o `occurredAt` vem em UTC e a conversao usa o `ZoneId` do sistema, que lida com ambiguidades conforme a especificacao `java.time`.
- **[Precisao de nanos]** A comparacao usa `< 06:00:00` e `>= 06:00:00`. Como `LocalTime` tem precisao de nanossegundos, bordas como `05:59:59.999999999` sao `NIGHTTIME` e `06:00:00.000000000` e `DAYTIME`, conforme exigido.
