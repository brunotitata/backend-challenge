## Why

O processamento de pagamento precisa classificar o periodo do `occurredAt` para aplicar os limites corretos de consumo (diurno, noturno ou final de semana). Atualmente essa regra nao existe. Isola-la como uma camada de dominio testavel e sem dependencia de framework evita erros na fatia de pagamento e facilita a evolucao para novas politicas.

## What Changes

- Criar um enum `PeriodType` com valores `DAYTIME`, `NIGHTTIME` e `WEEKEND`.
- Criar um `PeriodClassifier` que, dado um `Instant` e um `ZoneId`, retorna o `PeriodType` e o `periodStart` (inicio do periodo de consumo).
- A regra deve ser puramente funcional (entrada -> saida), sem dependencia de banco,框架 HTTP ou framework.
- O timezone operacional sera definido como `America/Sao_Paulo` e documentado.
- Nenhuma alteracao em endpoints, persistencia ou fluxo de pagamento.

## Capabilities

### New Capabilities
- `payment-periods`: classificacao de periodos DAYTIME, NIGHTTIME e WEEKEND a partir de um Instant, com calculo do periodStart para consumo de limite

### Modified Capabilities

- Nenhuma

## Impact

- Codigo novo em pacote de dominio (`domain/period` ou similar)
- Testes unitarios para todas as bordas de horario e dias da semana
- Sem impacto em banco de dados, rotas HTTP ou contratos de API
