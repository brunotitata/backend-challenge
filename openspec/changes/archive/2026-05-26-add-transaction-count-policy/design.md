# Design: Politica TX_COUNT_LIMIT

## Context

O sistema ja possui politicas `VALUE_LIMIT` com avaliador proprio (`ValueLimitEvaluator`), `PolicyResolver` e `PolicyEvaluatorRegistry`. A tabela `policies` ja possui campos para `VALUE_LIMIT`, mas ainda nao suporta `TX_COUNT_LIMIT`. A tabela `limit_consumptions` ja possui `transaction_count` como coluna, criada na fatia de concorrencia, mas ainda nao e usada para limites baseados em quantidade.

## Goals / Non-Goals

**Goals:**
- Permitir criacao de politicas `TX_COUNT_LIMIT` via `POST /policies`.
- Implementar `TxCountLimitEvaluator` registrado no `PolicyEvaluatorRegistry`.
- Avaliar pagamentos usando contagem de transacoes no periodo diario.
- Garantir consistencia concorrente ao incrementar `transaction_count`.
- Manter o fluxo principal de pagamento generico.

**Non-Goals:**
- Combinacao de multiplas politicas ativas simultaneamente.
- Politicas compostas.
- Limite por quantidade em subperiodos (diurno/noturno/fim de semana separados).
- Alterar a estrutura da tabela `policies` para suportar colunas totalmente dinamicas.

## Decisions

### Reutilizar tabela `policies` com `daily_transaction_limit`

**Decisao**: Adicionar coluna `daily_transaction_limit` na tabela `policies` para `TX_COUNT_LIMIT`.

**Rationale**: As politicas ja sao desnormalizadas com colunas para `VALUE_LIMIT`. Em vez de normalizar para tabelas separadas neste momento, adicionar uma coluna e a forma mais simples e pragmatica, dado que cada politica pertence a uma unica categoria. Isso evita JOINs adicionais e mantem a simplicidade da camada de persistencia.

**Alternativa considerada**: Tabelas separadas `value_limit_configs` e `tx_count_limit_configs`. Rejeitada por adicionar complexidade desnecessaria para o escopo atual, sem ganho funcional.

### Contagem por dia local, independente de periodo

**Decisao**: `TX_COUNT_LIMIT` conta transacoes aprovadas por dia local inteiro (00:00:00 a 23:59:59.999), usando a mesma chave de periodo que `DAYTIME` e `WEEKEND` para simplificacao: `period_start` como inicio do dia local.

**Rationale**: O requisito do desafio diz "quantidade maxima de pagamentos aprovados por carteira em um dia". Nao ha distincao entre periodos para `TX_COUNT_LIMIT` no escopo atual. Usar `period_start` como inicio do dia local permite reutilizar a estrutura de `limit_consumptions` sem criar uma nova tabela ou chave.

**Alternativa considerada**: Criar um contador separado por subperiodo (diurno/noturno/fim de semana). Rejeitada por ser fora de escopo e mais complexa.

### Reutilizar `limit_consumptions.transaction_count`

**Decisao**: Usar `transaction_count` em `limit_consumptions` para rastrear quantidade de transacoes aprovadas.

**Rationale**: A tabela ja foi projetada na fatia de concorrencia com `transaction_count` como coluna preparatoria. Usar atomically via `INSERT ... ON CONFLICT UPDATE` ou `SELECT FOR UPDATE` mantem a mesma estrategia de consistencia usada para `consumed_amount`.

### Estrutura de resposta especifica por categoria

**Decisao**: Os DTOs de resposta (`PolicyResponseDTO`, `WalletPolicyResponseDTO`) incluem todos os campos possiveis de todas as categorias, retornando `null` para campos irrelevantes a categoria. Para `TX_COUNT_LIMIT`, `dailyTransactionLimit` e populado; `maxPerPayment`, `daytimeDailyLimit`, etc. sao `null`.

**Rationale**: Mantem um unico DTO simples para todas as categorias, evitando serializacao polimorfica complexa. O frontend pode usar `category` para saber quais campos esperar, ignorando os campos `null`.

## Risks / Trade-offs

- **[Risco]** Adicionar colunas a `policies` para cada nova categoria pode levar a tabela muito larga.  
  **Mitigacao**: Se o numero de categorias crescer, documentar a necessidade de normalizar em tabelas separadas.
- **[Risco]** `TX_COUNT_LIMIT` usando chave diaria pode gerar confusao se no futuro for necessario limitar por subperiodo.  
  **Mitigacao**: Documentar explicitamente no `spec.md` e no `design.md` que `TX_COUNT_LIMIT` aplica-se ao dia inteiro.
- **[Trade-off]** Campos nulos em `policies` para categorias diferentes. Aceito pelo simplicidade operacional.

## Migration Plan

1. Criar migracao para adicionar `daily_transaction_limit` em `policies`.
2. Implementar `TxCountLimitEvaluator`.
3. Registrar `TxCountLimitEvaluator` no `PolicyEvaluatorRegistry`.
4. Ajustar validacao de `POST /policies` para aceitar `TX_COUNT_LIMIT`.
5. Ajustar serializacao de resposta de politicas para omitir campos irrelevantes por categoria.
6. Executar testes de regressao para `VALUE_LIMIT`.
7. Arquivar change.

## Open Questions

Nenhuma.
