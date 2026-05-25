## Context

Pagamentos em `POST /wallets/{walletId}/payments` atualmente nao possuem protecao contra duplicidade. Clientes podem reenviar a mesma requisicao por timeout ou falha de rede, resultando em multiplos pagamentos aprovados. O cronograma define que esta fatia deve introduzir idempotencia baseada em header `Idempotency-Key` com garantia de unicidade via constraint no banco.

A fatia anterior (`add-value-limit-payment-processing`) ja implementou o fluxo basico de pagamento com aprovacao e rejeicao por limite. Esta change adiciona a camada de idempotencia sem alterar as regras de negocio de limite.

## Goals / Non-Goals

**Goals:**
- Header `Idempotency-Key` obrigatorio em `POST /wallets/{walletId}/payments`
- Unicidade garantida por constraint unique `(wallet_id, idempotency_key)` no banco
- Retry com mesma chave e payload retorna resultado original (status code + body)
- Retry com mesma chave e payload diferente retorna `409 Conflict`
- Idempotencia isolada por carteira (mesma chave em carteiras diferentes e valida)
- Validacoes de `400 Bad Request` nao registram chave idempotente
- Comparacao de payload considera `amount` e `occurredAt` normalizados

**Non-Goals:**
- Concorrencia robusta entre requisicoes simultaneas (sera tratada na Fatia 6)
- Limpeza ou expiracao de chaves idempotentes antigas
- Idempotencia para outros endpoints alem de `POST /wallets/{walletId}/payments`

## Decisions

### 1. Header `Idempotency-Key` como mecanismo de idempotencia
**Decisao:** Usar header HTTP `Idempotency-Key` em vez de chave no body da requisicao.

**Alternativa considerada:** Incluir `idempotencyKey` no body JSON.
**Racional:** Header e o padrao da industria (Stripe, PayPal) e mantem o body da requisicao limpo. Facilita implementacao em middlewares Ktor.

### 2. Tabela `payment_idempotency_keys` com constraint unique
**Decisao:** Criar tabela dedicada para registro de chaves idempotentes.

**Alternativa considerada:** Adicionar coluna `idempotency_key` na tabela `payments` com unique constraint composta.
**Racional:** Tabela separada permite armazenar metadata da resposta (status, body) para retry sem poluir a tabela de pagamentos. A constraint unique `(wallet_id, idempotency_key)` garante unicidade em nivel de banco.

### 3. Request hash para comparacao de payload
**Decisao:** Calcular hash deterministico de `amount` e `occurredAt` normalizados e armazenar como `request_hash`.

**Alternativa considerada:** Armazenar `amount` e `occurredAt` em colunas separadas para comparacao direta.
**Racional:** Hash simplifica a comparacao e evita schema changes futuros se novos campos forem adicionados ao payload. A escolha de `SHA-256` garante colisoes praticamente inexistentes.

### 4. Registro da chave apos aceitacao
**Decisao:** Registrar a chave idempotente somente apos o pagamento passar pelas validacoes de `400 Bad Request` e ser aceito para processamento.

**Alternativa considerada:** Registro pre-validacao.
**Racional:** Evita poluir a tabela com chaves de requisicoes invalidas. Tentativas que retornam `400` nao consomem chave idempotente, permitindo que o cliente corrija o payload e reenvie com a mesma chave.

## Risks / Trade-offs

- **Chave sem expiracao**: Chaves idempotentes acumulam-se indefinidamente no banco. Mitigacao: considerar job de limpeza em fatia futura se necessario para o desafio.
- **Hash collision**: Probabilidade extremamente baixa com SHA-256. Mitigacao: aceitavel para este contexto.
- **Performance**: Tabela `payment_idempotency_keys` cresce linearmente com o volume de pagamentos. Mitigacao: indice unique ja otimiza lookup por `(wallet_id, idempotency_key)`.
