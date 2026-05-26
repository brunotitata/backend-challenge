## 1. Interfaces e DTOs (boundary)

- [x] 1.1 Criar `ListPaymentsUseCaseSpec` em `boundary-context/input-boundary/` com metodo `execute(walletId, startDate?, endDate?, cursor?, limit?): PaginationResult<PaymentEntity>`
- [x] 1.2 Criar `Cursor` value object imutavel em `core/entities/` com campos `occurredAt: Instant` e `id: UUID`, e metodos `encode(): String` (Base64 URL-safe) e `decode(cursor: String): Cursor`
- [x] 1.3 Criar `ListPaymentResponseDTO` em `adapters/web/dtos/` com `id`, `walletId`, `amount`, `occurredAt`, `status`, `createdAt` e `updatedAt`
- [x] 1.4 Adicionar metodo `findApprovedByWalletId(walletId, startDate?, endDate?, cursor?, limit?): PaginatedResult<PaymentEntity>` em `PaymentGatewaySpec`

## 2. Pagination helper (domain)

- [x] 2.1 Criar `PaginationResult<T>` generico em `core/entities/` com `items: List<T>`, `nextCursor: String?`, `previousCursor: String?`, `total: Int`
- [x] 2.2 Implementar logica de `Cursor` em `core/entities/Cursor.kt` com encode/decode Base64 URL-safe contendo direcao (FWD/BWD), `occurredAt` e `id`

## 3. Implementacao do caso de uso

- [x] 3.1 Criar `ListPaymentsUseCaseImpl` em `core/use-cases/` implementando `ListPaymentsUseCaseSpec`
- [x] 3.2 Validar `walletId` existe (lancar `NotFoundException` se inexistente)
- [x] 3.3 Validar `startDate` nao posterior a `endDate` (lancar `ValidationException`)
- [x] 3.4 Delegar para `PaymentGateway.findApprovedByWalletId(...)` e mapear resultado

## 4. Implementacao do banco de dados

- [x] 4.1 Indice `payments(wallet_id, occurred_at, id)` ja existente na migracao V3
- [x] 4.2 Implementar `findApprovedByWalletId` em `PaymentGatewayImpl` usando jOOQ com predicates dinamicos, `LIMIT + 1` e direcao do cursor (FWD/BWD)
- [x] 4.3 Implementar `total` (COUNT por wallet_id, status APPROVED) e `totalMatches = null`
- [x] 4.4 Implementar `previousCursor` via cursor BWD

## 5. Rota HTTP

- [x] 5.1 Adicionar rota `GET /wallets/{walletId}/payments` em `PaymentRoutes.kt`
- [x] 5.2 Extrair e validar query parameters: `startDate`, `endDate`, `cursor`, `limit` (default 20, max 100)
- [x] 5.3 Chamar `ListPaymentsUseCase` e retornar `200 OK` com `DataResponseDTO<ListPaymentResponseDTO>`
- [x] 5.4 Wire `ListPaymentsUseCaseImpl` no `Application.kt`

## 6. Testes

- [x] 6.1 Teste unitario de `Cursor`: encode/decode, BWD direction, base64 invalido
- [x] 6.2 Teste de integracao: lista todos os pagamentos sem filtro
- [x] 6.3 Teste de integracao: filtra por `startDate`
- [x] 6.4 Teste de integracao: filtra por `endDate`
- [x] 6.5 Teste de integracao: filtra por intervalo `startDate` e `endDate`
- [x] 6.6 Teste de integracao: retorna `nextCursor` quando ha proxima pagina
- [x] 6.7 Teste de integracao: usa cursor para buscar proxima pagina
- [x] 6.8 Teste de integracao: ordenacao estavel para mesmo `occurredAt`
- [x] 6.9 Teste de integracao: nao mistura pagamentos de carteiras diferentes
- [x] 6.10 Teste de integracao: lista apenas pagamentos `APPROVED`
- [x] 6.11 Teste de integracao: retorna `404` para `walletId` inexistente
- [x] 6.12 Teste de integracao: rejeita `startDate` invalido com `400`
- [x] 6.13 Teste de integracao: rejeita `endDate` invalido com `400`
- [x] 6.14 Teste de integracao: rejeita `startDate` posterior a `endDate` com `400`
- [x] 6.15 Teste de integracao: rejeita cursor invalido com `400`
- [x] 6.16 Teste de integracao: rejeita `limit` zero ou acima do maximo com `400`
- [x] 6.17 Teste de integracao: `meta` contem `nextCursor`, `previousCursor`, `total` e `totalMatches` nulo
