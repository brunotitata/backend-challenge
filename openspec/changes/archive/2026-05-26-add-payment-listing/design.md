## Context

O cronograma prevê a Fatia 7 (add-payment-listing) após a conclusão das fatias de pagamento, idempotência e concorrência. A tabela `payments` já existe com os campos `id`, `wallet_id`, `amount`, `occurred_at`, `period_type`, `period_start`, `status`, `created_at`, `updated_at`. O indice `payments(wallet_id, occurred_at, id)` será usado para a consulta paginada.

Atualmente a aplicação possui rota de criação de pagamentos (`POST /wallets/{walletId}/payments`) e sua contraparte de listagem ainda não existe. O banco de dados utilizado é PostgreSQL com jOOQ para queries tipadas.

## Goals / Non-Goals

**Goals:**
- Implementar `GET /wallets/{walletId}/payments` com paginação por cursor.
- Suportar filtros opcionais `startDate` e `endDate` em ISO-8601 UTC.
- Suportar parâmetro opcional `limit` para controlar tamanho da página (padrão 20, máximo 100).
- Resposta no padrão `data` e `meta` com `nextCursor`, `previousCursor`, `total` e `totalMatches`.
- Ordenação estável por `occurredAt` ascendente e `id` ascendente.
- Listar apenas pagamentos com status `APPROVED`.

**Non-Goals:**
- Exportação de pagamentos.
- Busca textual.
- Relatórios agregados.
- Paginação offset-based.

## Decisions

### 1. Cursor encoding em Base64 URL-safe
O cursor será codificado como `$direction|$occurredAt|$id` em Base64 URL-safe, onde `direction` é `FWD` (forward) ou `BWD` (backward). O campo de direção permite que o servidor saiba se deve navegar para frente (`(occurred_at, id) > cursor`) ou para trás (`(occurred_at, id) < cursor` com ordenação reversa). O cursor permanece opaco para o cliente.

Alternativa considerada: cursor como par ordinal (page number + offset). Rejeitada porque não é estável sob inserção de novos registros.

### 2. `total` via COUNT completo, `totalMatches` como null
O campo `total` reflete a contagem total de pagamentos da carteira (sem filtros). O campo `totalMatches` será retornado como `null` intencionalmente para evitar custo de COUNT com filtros em grandes volumes, conforme especificado no cronograma.

### 3. Consulta jOOQ com predicates dinâmicos
A query de listagem será montada com jOOQ usando predicates condicionais:
- `wallet_id = ?`
- `status = 'APPROVED'`
- `occurred_at >= ?` (se startDate presente)
- `occurred_at <= ?` (se endDate presente)
- `(occurred_at, id) > (?, ?)` (se cursor presente, para next page)
- `ORDER BY occurred_at ASC, id ASC`
- `LIMIT ? + 1` (para detectar se há próxima página)

### 4. previousCursor via cursor BWD
Para suportar `previousCursor`, o cursor armazena a direção (`FWD` ou `BWD`). O `previousCursor` retornado no `meta` de cada página é o cursor do primeiro item da página atual com direção `BWD`. Quando o cliente envia esse cursor, a implementação reconhece a direção `BWD`, consulta com ordenação reversa (`ORDER BY occurred_at DESC, id DESC`) e reverte o resultado para manter a ordenação original.

### 5. Indice composto
Indice `payments(wallet_id, occurred_at, id)` garante performance da consulta paginada com filtro por carteira e ordenação estável.

### 6. UseCase ListPayments
A lógica de paginação será encapsulada em um caso de uso `ListPayments` na camada de application, mantendo as rotas HTTP enxutas.

## Risks / Trade-offs

- [Performance] `total` com COUNT sem filtro é constante para a carteira, mas pode degradar com muitos pagamentos. Mitigação: aceitável para o volume esperado do desafio.
- [Complexidade] `totalMatches = null` evita custo de COUNT filtrado, mas reduz informação para o cliente. Decisão documentada e alinhada com o cronograma.
- [Cursor] Se a ordenação natural por `occurredAt` tiver muitos empates, o fallback para `id` garante estabilidade.
- [Validação] Cursor malformado ou com dados corrompidos retorna `400 Bad Request`. A implementação deve validar o JSON decodificado antes de usar.
