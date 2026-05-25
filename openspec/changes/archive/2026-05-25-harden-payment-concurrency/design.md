## Context

O processamento de pagamentos atualmente executa em transacao com `SELECT FOR UPDATE` na tabela `limit_consumptions` e usa UPSERT para incremento atomico. No entanto, existem duas falhas de concorrencia:

1. **Idempotencia fora da transacao**: a checagem `findByWalletAndKey` e o `save` da chave idempotente ocorrem fora da transacao do pagamento. Duas requisicoes simultaneas com a mesma chave podem passar pela checagem (nenhuma chave existe ainda) e ambas processar o pagamento.

2. **SELECT FOR UPDATE sem lock quando a linha nao existe**: quando `limit_consumptions` ainda nao possui registro para a chave de consumo, o `SELECT FOR UPDATE` retorna `BigDecimal.ZERO` (fallback) sem travar nada. Duas requisicoes concorrentes veem `0`, ambas passam na verificacao de limite, ambas inserem pagamento e o UPSERT de consumo incrementa duas vezes (ex: `700 + 700 = 1400` com limite `1000`).

## Goals / Non-Goals

**Goals:**
- Pagamentos simultaneos para a mesma carteira nao ultrapassam limites de periodo
- Cenario `700 + 700` com limite `1000` aprova apenas um pagamento
- Idempotencia sob concorrencia: mesma chave simultanea resulta em um unico pagamento
- Pagamentos de carteiras diferentes nao se bloqueiam entre si
- Todas as operacoes de checagem e gravacao sao atomicas dentro de uma unica transacao

**Non-Goals:**
- Otimizacoes avancadas de performance (ex: particionamento de tabelas)
- Metricas de concorrencia
- Limpeza de chaves idempotentes expiradas

## Decisions

### 1. Unificar idempotencia dentro da transacao de pagamento

**Decisao:** Mover a checagem e o registro da chave idempotente para dentro da transacao do pagamento, usando `INSERT ... ON CONFLICT DO NOTHING` para atomicidade.

**Como funciona:**
- Dentro da transacao, tentar inserir a chave idempotente com `ON CONFLICT DO NOTHING`
- Se a insercao foi bem-sucedida (nenhuma chave existia), a requisicao e nova e prossegue
- Se a insercao nao inseriu nada (chave ja existe), consultar o registro existente e retornar o resultado original ou `409`
- O INSERT com `ON CONFLICT DO NOTHING` dentro da transacao serializa as duas requisicoes simultaneas: apenas uma consegue inserir

**Alternativa considerada:** Manter o `findByWalletAndKey` + `save` separados, com `UNIQUE` constraint capturando excecao.
**Racional:** A constraint capturaria o conflito, mas resultaria em excecao lancada para uma das requisicoes, exigindo tratamento de erro mais complexo. O `ON CONFLICT DO NOTHING` e mais elegante e evita o uso de excecao para fluxo normal.

### 2. Garantir que SELECT FOR UPDATE sempre encontre linha

**Decisao:** Inserir a linha em `limit_consumptions` com `consumed_amount = 0` antes de realizar o `SELECT FOR UPDATE`, usando `INSERT ... ON CONFLICT DO NOTHING`.

**Como funciona:**
- Tentar inserir a linha `(wallet_id, policy_id, period_type, period_start, 0)` com `ON CONFLICT DO NOTHING`
- Isso garante que a linha existe antes do lock
- Em seguida, `SELECT ... FOR UPDATE` na linha recem-criada ou ja existente adquire o lock exclusivo
- Ler o `consumed_amount` atual, verificar o limite, e atualizar com o UPSERT existente

**Alternativa considerada:** Usar `pg_advisory_xact_lock(hash(wallet_id))` para serializar por carteira.
**Racional:** O lock granular por periodo ja e suficiente. O `advisory lock` serializaria ate mesmo pagamentos de periodos diferentes da mesma carteira, reduzindo concorrencia desnecessariamente.

### 3. Manter lock granular por chave de consumo

**Decisao:** O lock continua sendo por `(wallet_id, policy_id, period_type, period_start)`, conforme implementado.

**Impacto:** Pagamentos para periodos diferentes da mesma carteira (ex: diurno e noturno) nao competem entre si. Pagamentos de carteiras diferentes nunca competem.

### 4. Sem nova tabela ou schema

**Decisao:** Reutilizar `limit_consumptions` e `payment_idempotency_keys` existentes. Nenhuma nova migracao e necessaria para schema.

**Excecao:** Adicionar coluna `transaction_count` em `limit_consumptions` se `TX_COUNT_LIMIT` estiver no escopo. Para esta fatia, apenas `consumed_amount` e relevante.

## Risks / Trade-offs

- **[Performance]** `ON CONFLICT DO NOTHING` antes de cada pagamento adiciona um INSERT extra para cada requisicao, mesmo quando a linha ja existe. O impacto e irrelevante para o volume do desafio.
- **[Deadlock]** O unico lock adquirido e o `FOR UPDATE` na linha de consumo. Nao ha risco de deadlock porque cada transacao adquire um unico lock de linha.
- **[Transacao longa]** A chave idempotente e o pagamento estao na mesma transacao. Transacao pode durar mais se houver contencao. Mitigacao: o lock e de linha unica e o tempo de execucao e deterministico (alguns inserts + updates).
