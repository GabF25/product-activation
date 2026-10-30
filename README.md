# ⚡ Product Activation — Desafio Técnico Java

Projeto desenvolvido como parte do desafio técnico para a vaga de Desenvolvedor Java Pleno/Sênior. É um serviço que
registra solicitações de ativação de produtos de clientes, permite consultar o status e faz a ativação num provedor
externo, de forma assíncrona e sem duplicidade.

## 🚀 Tecnologias Utilizadas

- Java 21
- Spring Boot 3.5 (Spring MVC, Spring Data JPA, Spring Security)
- PostgreSQL 17 + Flyway
- OAuth 2.0 Resource Server (JWT)
- Swagger (springdoc-openapi)
- JUnit 5, Mockito, MockMvc e Testcontainers
- Docker e Docker Compose (com WireMock simulando o provedor)
- GitHub Actions
- Maven

## 📋 Funcionalidades

- Criação de solicitações de ativação, com proteção contra duplicidade por `externalId` (inclusive em requisições
  simultâneas)
- Consulta do status da solicitação e listagem paginada, com filtros por status e cliente
- Cada sistema consumidor só enxerga as próprias solicitações
- Ativação assíncrona no provedor externo, com novas tentativas, backoff e recuperação de solicitações presas
- Autenticação por JWT, com escopos separados para leitura e escrita
- Limite de tamanho do corpo e de chamadas por minuto
- Erros padronizados em Problem Details (RFC 9457), com `traceId` para rastrear no log
- Logs estruturados em JSON (formato ECS), com o `correlationId` em cada linha
- Health checks de liveness e readiness

## 🛣️ Rotas

| Método | Rota | Ação | Escopo |
|--------|------|------|--------|
| POST | `/api/v1/requests` | Criar solicitação | `activation:write` |
| GET | `/api/v1/requests/{id}` | Consultar solicitação | `activation:read` |
| GET | `/api/v1/requests?status=&customerId=&page=&size=` | Listar solicitações (até 100 por página) | `activation:read` |

Na criação, `externalId`, `customerId` e `productCode` são obrigatórios, com até 64 caracteres (letras, dígitos, `-`
e `_`).

## 📦 Como executar

Precisa de Java 21 e Docker rodando (os testes de integração sobem um PostgreSQL com Testcontainers).

```bash
# Clone o repositório
git clone https://github.com/GabF25/product-activation.git

# Acesse a pasta
cd product-activation

# Suba a aplicação, o banco e o mock do provedor
docker compose up --build -d
```

A API sobe em http://localhost:28080, o PostgreSQL em 5432 e o mock do provedor em 8089. A documentação fica em
http://localhost:28080/swagger-ui.html. Os logs saem em JSON (`docker compose logs -f app`); com `LOG_FORMAT=` vazio,
voltam a ser texto.

### 🔑 Chamando a API

As rotas exigem um token. Para fins de teste, há um endpoint que gera tokens localmente:

1. Abra o [Swagger](http://localhost:28080/swagger-ui.html) e, em **Test token**, chame `POST /dev/token`.
2. Escolha o escopo: `READ` (só consulta), `WRITE` (só cria) ou `READ_WRITE` (os dois).
3. Copie o `accessToken` da resposta, clique em **Authorize** e cole.

O token vale por 1 hora. Com `READ`, o `POST` devolve `403`; com `WRITE`, os `GET` devolvem `403`.

O `/dev/token` existe só para testar a API na máquina: ele só é criado quando o segredo de desenvolvimento está
configurado (perfil `local`). Em produção ele não existe, e o token vem do servidor de autorização (veja Segurança).

Também dá para gerar o token pelo terminal (no Windows, pelo Git Bash), passando o escopo como argumento:

```bash
bash scripts/dev-token.sh activation:read
```

Pelo terminal:

```bash
TOKEN=$(bash scripts/dev-token.sh)

curl -i -X POST localhost:28080/api/v1/requests \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'
```

Alguns segundos depois da criação, o status vai para `COMPLETED`.

### 🧩 Testando os cenários do provedor

O mock do provedor (WireMock, regras em `provider-mock/mappings/`) muda a resposta conforme o `productCode`. A aplicação
não conhece esses códigos: ela só reage ao status HTTP que o provedor devolve.

| `productCode` | Provedor responde | O que acontece |
|---------------|-------------------|----------------|
| qualquer outro | 200 | `COMPLETED` em poucos segundos |
| `INVALID-PRODUCT` | 400 | `FAILED` na primeira tentativa |
| `BROKEN-PRODUCT` | 500 | Tenta de novo com backoff e vira `FAILED` em cerca de 1 min e meio |
| `BUSY-PRODUCT` | 429 com `Retry-After: 10` | Tenta de novo a cada 10 s e vira `FAILED` depois de 5 tentativas |
| `SLOW-PRODUCT` | 200 depois de 10 s | Timeout de 5 s, tenta de novo com a mesma `Idempotency-Key` até `FAILED` |

Para testar: crie a solicitação, acompanhe o `status` com `GET /api/v1/requests/{id}` e veja cada tentativa na tabela
`activation_attempt`. Use um `externalId` novo em cada teste: repetir um `externalId` com outro `productCode` dá `409`.

## 🧪 Testes

```bash
./mvnw verify
```

Roda os testes unitários e os de integração (com PostgreSQL real via Testcontainers). O mesmo comando roda no CI, a
cada push.
