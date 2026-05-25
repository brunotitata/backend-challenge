## Why

Retries de requisicoes de pagamento podem criar pagamentos duplicados se a primeira tentativa for bem-sucedida mas o cliente nao receber a resposta a tempo. Sem idempotencia, o usuario ou sistema pode ser cobrado mais de uma vez pelo mesmo pagamento. Esta change introduz idempotencia baseada em header `Idempotency-Key` para garantir que retries nao gerem duplicidade.

## What Changes

- Adicionar header `Idempotency-Key` obrigatorio em `POST /wallets/{walletId}/payments`
- Criar tabela `payment_idempotency_keys` com constraint unique `(wallet_id, idempotency_key)`
- Registrar chave idempotente apos aceitacao do pagamento para processamento
- Retornar resultado original em retries com mesma chave e mesmo payload
- Retornar `409 Conflict` em retries com mesma chave e payload diferente
- Validacoes de `400 Bad Request` nao devem registrar chave idempotente
- Idempotencia isolada por carteira: mesma chave pode ser usada em carteiras diferentes

## Capabilities

### New Capabilities
- `payment-idempotency`: idempotencia para pagamentos via header `Idempotency-Key`, com registro em banco e constraint de unicidade

### Modified Capabilities
- `payments`: adicionar obrigatoriedade do header `Idempotency-Key` e comportamento idempotente no endpoint `POST /wallets/{walletId}/payments`

## Impact

- `POST /wallets/{walletId}/payments`: header `Idempotency-Key` passa a ser obrigatorio
- Nova tabela `payment_idempotency_keys` no banco de dados
- Camada de infraestrutura: repository para idempotencia, validacao de header
- Fluxo de pagamento: registrar chave idempotente antes de persistir pagamento, retornar resposta cacheada em retries
