## Why

Permitir que clientes consultem o historico de pagamentos de uma carteira com suporte a paginacao por cursor e filtro opcional por intervalo de datas. A listagem de pagamentos e um requisito obrigatorio do desafio e depende da infraestrutura de pagamentos ja implementada nas faturas anteriores (carteiras, politicas, processamento, idempotencia e concorrencia).

## What Changes

- Novo endpoint `GET /wallets/{walletId}/payments` com paginacao por cursor.
- Filtros opcionais `startDate` e `endDate` em ISO-8601 UTC.
- Parametro opcional `limit` para controlar tamanho da pagina.
- Ordenacao estavel por `occurredAt` e `id`.
- Resposta padrao no formato `data` e `meta` com `nextCursor`, `previousCursor`, `total` e `totalMatches`.
- Indice composto em `payments(wallet_id, occurred_at, id)` para performance.
- Validacoes de data invalida, cursor invalido, intervalo reverso e carteira inexistente.
- Apenas pagamentos com status `APPROVED` aparecem na listagem.

## Capabilities

### New Capabilities
- `payment-listing`: listagem paginada por cursor com filtro por data para pagamentos aprovados de uma carteira

### Modified Capabilities
- Nenhuma. Nao altera requisitos de capacidades existentes.

## Impact

- **API**: nova rota `GET /wallets/{walletId}/payments` no modulo de rotas de pagamento.
- **Application**: novo caso de uso `ListPayments` para orquestrar a consulta paginada.
- **Infrastructure**: nova consulta jOOQ com filtros dinamicos, cursor encoding/decoding e indice adicional.
- **Domain**: possivel expansao do modelo `Payment` se campos de paginacao forem necessarios no repositorio.
- **Testes**: testes de integracao para paginacao, filtros e validacoes; testes unitarios para logica de cursor.
