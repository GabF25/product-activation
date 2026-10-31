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

## 🧠 Decisões técnicas

### Estrutura

Controller, service e repository, com o domínio separado da entidade JPA. O status só muda pelos métodos do domínio
(`PENDING → PROCESSING → COMPLETED/FAILED`). Falha temporária volta para `PENDING` com a próxima tentativa agendada, e
`PROCESSING` fica só para "chamando o provedor agora". A gravação do status usa `@Version`.

### Duplicidade

`UNIQUE` em (`client_id`, `external_id`), e o service tenta inserir direto. Se eu consultasse antes de inserir, duas
requisições ao mesmo tempo passariam pela consulta e as duas inseririam.

- Mesmo `externalId` e mesmo conteúdo: é retry, devolvo `201` com o registro que já existe.
- Mesmo `externalId` com outro conteúdo: `409`.
- Duas iguais em paralelo: as duas recebem o mesmo registro (tem teste com 10 threads no PostgreSQL).

### Índices

Cada índice existe por causa de uma query:

| Índice | Colunas | Query que atende |
|--------|---------|------------------|
| `idx_activation_listing` | `client_id, customer_id, status, created_at DESC` | Listagem paginada |
| `idx_activation_pending` | `next_attempt_at`, só linhas `PENDING` | Scheduler buscando as pendentes vencidas |
| `idx_activation_processing` | `updated_at`, só linhas `PROCESSING` | Recuperação das solicitações presas |
| `uk_activation_client_external_id` | `client_id, external_id` | Criação idempotente (`UNIQUE`) |
| `uk_activation_attempt_number` | `activation_id, attempt_number` | Contagem das tentativas (`UNIQUE`) |

Na listagem, `client_id` vem primeiro porque está em toda consulta (cada sistema só vê as próprias). Depois vêm
`customer_id` e `status`, os filtros opcionais, e por último `created_at DESC`, a ordenação.

| Filtros informados | Como o índice ajuda |
|--------------------|---------------------|
| `customerId` e `status` | Lê as linhas já na ordem da página, sem ordenar |
| Só `customerId` | Restringe por cliente e `customer_id`; ordena o resultado |
| Só `status`, ou nenhum | Restringe só pelo `client_id`; ordena o resultado |

A ordenação sem custo só acontece com os dois filtros. Nos outros casos, o PostgreSQL ordena as linhas daquele
cliente, o que é barato enquanto cada cliente tem poucas solicitações. Se a listagem sem filtro ficasse lenta, o próximo
passo seria um índice `(client_id, created_at DESC)`.

Os índices da fila são parciais: como quase tudo termina em `COMPLETED`, eles guardam só as linhas em andamento e
continuam pequenos mesmo com a tabela grande.

### Integração com o provedor

Assíncrona: o `POST` grava `PENDING` e responde, e um scheduler chama o provedor depois.

| Retorno do provedor | Novo status | Tenta de novo? | Por quê |
|---------------------|-------------|----------------|---------|
| 2xx | `COMPLETED` | Não | Ativado |
| 400 e outros 4xx | `FAILED` | Não | Repetir daria o mesmo erro |
| 500 e outros 5xx, 408 | `PENDING` | Sim, com backoff | Erro temporário |
| 429 / 503 com `Retry-After` | `PENDING` | Sim, no tempo pedido (até 5 min) | O provedor diz quando voltar |
| Timeout | `PENDING` | Sim, com a mesma chave de idempotência | O provedor pode ter ativado |

- Até 5 tentativas, backoff começando em 5 s e dobrando até 5 min, com jitter (entre metade e o valor cheio) para as
  solicitações que falharam juntas não voltarem todas no mesmo segundo. Depois disso, `FAILED`.
- Cada chamada ao provedor vira uma linha em `activation_attempt`, com o número da tentativa e o erro. A tabela
  `activation` guarda só o pedido, o status e o `next_attempt_at`, que fica nela porque é a coluna que a fila consulta.
- Timeout de conexão de 2 s e de leitura de 5 s, configuráveis.
- A tabela é a fila: o scheduler pega as pendentes e marca `PROCESSING` num único `UPDATE ... RETURNING` com
  `FOR UPDATE SKIP LOCKED`, faz commit e só então chama o provedor, fora de transação. Com `SKIP LOCKED`, cada instância
  pega linhas diferentes.
- As chamadas rodam em paralelo, em virtual threads, limitadas por um semáforo (`max-concurrency`, 50). Cada ciclo pega
  só a quantidade de vagas livres e não espera ninguém terminar, então uma chamada lenta ocupa só a vaga dela.
- Se a aplicação cair no meio, a solicitação fica em `PROCESSING` e volta para `PENDING` depois de 5 min. O
  `Idempotency-Key` (id da solicitação) evita ativar duas vezes.
- O `Retry-After` vale em segundos ou como data HTTP, limitado a 5 min para um valor alto não estacionar a solicitação.

**Circuit breaker (não implementado, mas válido).** Com o provedor fora do ar, cada solicitação pendente continua
tentando por conta própria, até 5 vezes. O backoff com jitter espaça as chamadas, mas o serviço segue mandando tráfego
para um provedor que já está com problema, o que pode atrasar a volta dele, e cada solicitação gasta tentativas numa
falha que não é dela. O circuit breaker resolveria isso: depois de algumas falhas seguidas, ele para de chamar o
provedor por um tempo, as solicitações continuam em `PENDING` sem gastar tentativas, e uma chamada de teste decide
quando voltar. Eu usaria o Resilience4j em volta do `ActivationProviderClient`. Ficou fora para manter o escopo do
desafio; hoje, o que limita o impacto é o `max-concurrency` e o backoff de até 5 min.

### Segurança

Resource server JWT: `activation:write` para criar, `activation:read` para consultar.

Cada solicitação guarda o sistema que a criou (o `sub` do token). Um sistema só lista e consulta as próprias, e a de
outro responde `404`, sem revelar que ela existe. Por isso o `externalId` é único por sistema.

Corpo acima de 1 KB recebe `413`. Corpo sem `Content-Length` (enviado com `Transfer-Encoding: chunked`) recebe `411`,
porque sem o tamanho o filtro teria que ler o corpo para medir. Cada sistema tem um limite de chamadas por minuto
(`429` com `Retry-After`). Esse
limite é por instância; com várias instâncias, o limite compartilhado ficaria no API Gateway.

Em produção:

- Fluxo Client Credentials, porque quem chama são outros sistemas.
- Validação de assinatura (JWKS do emissor), `iss`, `aud` e `exp`, só configurando a `issuer-uri`.
- Credencial do provedor (não implementada, porque o mock não exige autenticação):
  - **Armazenamento:** fica num gerenciador de segredos (AWS Secrets Manager, Azure Key Vault ou HashiCorp Vault),
    nunca no código nem no repositório. A plataforma injeta o valor numa variável de ambiente no deploy, e o
    `ActivationProviderClient` o envia no header `Authorization` de cada chamada.
  - **Rotação:** o gerenciador gera a credencial nova e mantém a antiga válida por um período de transição. As
    réplicas são reiniciadas aos poucos e passam a usar a nova, sem downtime; só depois a antiga é revogada.
  - **Credencial vencida ou revogada:** o provedor responde `401`/`403`, o que deveria gerar alerta (ver pontos
    pendentes).

O token HMAC do `dev-token.sh` só funciona no perfil local.

### Estratégia de testes

Unitários para domínio, mapper e cliente HTTP (contra um servidor HTTP local). Integração com PostgreSQL real via
Testcontainers para duplicidade, concorrência e processamento, porque o H2 não se comporta igual. E um teste de ponta
a ponta do provedor: sucesso, 400, 500 e timeout.
