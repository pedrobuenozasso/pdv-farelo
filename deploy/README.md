# Farelo OS na VPS Contabo

O domínio `farelodebolo.com.br` foi adquirido em 29/10/2026. Na última
consulta, não havia registros A para raiz, `www`, `app` ou `pedido`. Outro
projeto (`sucao-estoque`) já opera Caddy nas portas 80/443 da mesma VPS;
por isso o `infra/compose.production.yml` não pode ser iniciado como está.
O acesso administrativo do Farelo segue privado por túnel SSH. Planejar a
integração com o proxy existente, a proteção do Admin e as regras de firewall
antes de apontar DNS e abrir HTTPS público ([#21](https://github.com/pedrobuenozasso/pdv-farelo/issues/21)).
O proprietário informou que administra o DNS no Registro.br e quer cardápio
para clientes e Admin/PDV acessíveis com uma camada extra de proteção. O
Caddy atual monta `/root/sucao-estoque/Caddyfile` e usa a rede Docker
`sucao-estoque_default`; os contêineres Farelo estão na rede separada
`farelo-os_default`. Nenhum DNS, proxy, firewall ou serviço do outro projeto
foi alterado até esta atualização. Preservar o túnel SSH como retorno.

### Preparação do domínio (29/10)

Os arquivos `deploy/compose.domain.yml` e
`deploy/domain/Caddyfile.farelo.template` preparam dois hosts: `pedido` expõe
somente `/c/*`, os assets Next e o POST de pedido por QR; `app` serve o
Admin/PDV com Basic Auth adicional nas páginas e no login da API, além do
login/RBAC próprio do Farelo. No login, o proxy retira o cabeçalho Basic antes
de entregar a requisição à API; as demais rotas da API preservam o Bearer JWT.
A API protegida continua exigindo JWT; banco e
API não ganham portas públicas. O Caddyfile completo passou em `caddy
validate` no contêiner atual com hash fictício, **sem reload**. A sobreposição
Compose passou em `docker compose config`. O código dos QR agora aceita
`NEXT_PUBLIC_MENU_ORIGIN=https://pedido.farelodebolo.com.br` no build, para
que os QR impressos pelo Admin não apontem a `app`.

Para ativar, primeiro definir uma senha extra diferente da senha do PDV com
`bash deploy/domain/set-staff-password.sh` na VPS em terminal SSH interativo.
O script lê sem eco, exige 16 caracteres e grava só o hash Argon2id em
`/opt/farelo-os/shared/domain/staff.hash` (modo 600). O script
`deploy/domain/activate-domain.sh` valida o Caddyfile final, faz backup do
original e recarrega o Caddy em execução, com restauração em caso de falha.
**Executá-lo somente depois de conferir os A records**, para evitar tentativas
ACME repetidas antes de o domínio resolver para a VPS. O Web Farelo foi
recriado em `dbeeaf8` com **ambos** `deploy/compose.yml` e
`deploy/compose.domain.yml`, preservando `farelo-os_default` e conectando-se
à rede `sucao-estoque_default`; todo deploy futuro deve incluir esse overlay.
`/opt/farelo-os/shared/.env` contém `NEXT_PUBLIC_MENU_ORIGIN` e continua modo
600. A imagem anterior está marcada `farelo-os-web:pre-dbeeaf8`. O login
privado respondeu 200; o Caddy existente acessou a Web pelo nome da rede.
Manter o SSH/túnel como rollback. Nenhum DNS/Caddy público foi alterado.

Implantação privada inicial em `209.126.6.77`, Ubuntu 24.04.5. Em 29/10/2026,
a API usa o release `2da26ab` e a Web usa `1031ad3`. O atalho
`/opt/farelo-os/current` aponta para `/opt/farelo-os/releases/1031ad3`. Os segredos ficam
somente na VPS em
`/opt/farelo-os/shared/.env` (permissão `600`); nunca copie esse arquivo para
o Git.

## Serviços

- `db`: PostgreSQL 16, volume persistente `farelo-os_pgdata`, sem porta pública.
- `api`: Spring Boot, somente na rede interna do Compose.
- `web`: Next.js, publicado apenas em `127.0.0.1:3000` da VPS.

No Mac, o atalho `farelo-vps` em `~/.ssh/config` aponta para a chave SSH e
encaminha `127.0.0.1:3000` à web privada da VPS. Em 16/10/2026, o
LaunchAgent `~/Library/LaunchAgents/com.farelo.vps-tunnel.plist` foi instalado
e validado (`launchctl print gui/501/com.farelo.vps-tunnel`: `running`;
`http://127.0.0.1:3000/login`: HTTP 200). Um reinício simulado com
`launchctl kickstart -k` também voltou a responder HTTP 200. Ele inicia no login do Mac e
reconecta após queda; não é mais necessário executar `ssh -fN farelo-vps`
manualmente. O acesso por chave sem senha foi validado com
`ssh -o BatchMode=yes farelo-vps whoami`.

## Operação

`1031ad3` também faz a tela `/admin/fiscal-profiles` apontar formatos
incompatíveis do emitente, além de campos vazios. Assim, CNPJ com máscara não
aparece como cadastro pronto. TypeScript, lint e build de produção passaram;
`/login` e `/admin/fiscal-profiles` HTTP 200, monitor privado OK. Imagem Web
anterior em `farelo-os-web:pre-1031ad3`; rollback da interface usando essa
imagem e o release `0d55b56`, sem alterar API ou banco.

O editor fiscal valida o tamanho do CNPJ e da inscrição estadual paulista e,
ao salvar, envia apenas os dígitos para o cadastro. O registro existente foi
salvo anteriormente com CNPJ pontuado; precisa de novo salvamento pela Web
para normalizar o valor. Inscrição estadual e código IBGE ainda estão vazios.
Essa mudança não habilita emissão e não altera o banco automaticamente.
O release Web `0d55b56` passou em TypeScript, lint e build de produção;
`/login` e `/admin/fiscal-company` responderam HTTP 200, API/DB continuaram
saudáveis e o monitor privado não apontou alertas. A imagem Web anterior foi
reconstruída do código de `86a040f` como `farelo-os-web:pre-0d55b56` para
rollback; a API e o volume PostgreSQL não foram recriados.

`86a040f` alterou apenas a Web: `/admin/fiscal-company` agora explica que
ME (Microempresa) é o porte da empresa, não o CRT da NFC-e. O proprietário
informou o porte ME; o CRT efetivo ainda precisa ser conferido no XML atual
ou com a contabilidade. O formulário continua aceitando apenas CRT 1 ou 3;
não selecionar um deles por inferência do porte. Não houve alteração de API,
banco, credenciais ou emissão fiscal. Build de produção, TypeScript e lint
passaram; `/login` e `/admin/fiscal-company` responderam HTTP 200, API `UP`
e monitor privado sem alerta. A imagem Web anterior foi preservada como
`farelo-os-web:pre-86a040f`; para rollback, remarcar essa imagem como `latest`,
recriar apenas a Web com o Compose anterior e voltar `current` para `2da26ab`.

`2da26ab` aplicou V73 e colocou CEST, CFOP e CSOSN históricos na mesma revisão
administrativa dos NCMs, com originais preservados e rascunhos editáveis.
Prévia da migração em transação revertida: 52/25/32/32. Backup antes do deploy
em `/opt/farelo-os/shared/backups/pre-taxcodes-2da26ab-20261028.dump`, modo
`600`, validado com `pg_restore --list`; imagens dos contêineres anteriores
preservadas em `farelo-os-api:pre-2da26ab` e `farelo-os-web:pre-2da26ab`.
Build API/Web passou na VPS, compilação de testes backend, TypeScript e lint
passaram no Mac. O teste `FiscalNcmReviewServiceIntegrationTests` passou em
PostgreSQL 16 descartável, no namespace Docker separado do banco de produção;
o banco e a imagem de teste foram removidos. Após atualizar API e depois Web: V73 `success`, 52 linhas,
44 NCM, 25 CEST, 32 CFOP, 32 CSOSN, zero perfis fiscais, API `UP`, `/login`
e `/admin/fiscal-review` HTTP 200, API de revisão sem JWT 401 e monitor
privado sem alerta. Nenhuma NFC-e foi enviada. Para rollback, restaurar as
duas tags `pre-2da26ab` como `latest`, recriar API/Web com o Compose de
`fe1e4a2` e voltar `current` para esse release; V73 é aditiva e pode permanecer
no banco. Não restaurar o backup só para reverter a interface.

`fe1e4a2` carregou em V72 os NCMs históricos numa tabela de revisão separada
dos perfis usados pela emissão: 52 produtos, 44 NCMs preenchidos, oito vazios,
zero marcados como conferidos. O Admin pode editar o rascunho em
`/admin/fiscal-review`; o original Sischef permanece imutável. Antes do
deploy, a migração foi testada dentro de uma transação revertida na VPS,
resultando em 52/44. Backup `600` validado por `pg_restore --list` em
`/opt/farelo-os/shared/backups/pre-ncm-fe1e4a2-20261028.dump`; imagens
anteriores preservadas como `farelo-os-api:pre-fe1e4a2` e
`farelo-os-web:pre-fe1e4a2`. API compilou, Web compilou e passou em TypeScript
e lint; o teste de integração novo foi compilado, mas não executado localmente
porque o Docker Desktop estava desligado. Depois do deploy: Flyway V72 aplicada,
API `UP`, `/login` e `/admin/fiscal-review` HTTP 200, rota fiscal sem JWT
retornando 401, monitor privado sem alertas. Nenhuma nota foi emitida.
Para reverter as imagens, remarcar as tags `pre-fe1e4a2` como `latest`, recriar
`api` e `web` com o Compose do release `7320307` e voltar `current` para
`/opt/farelo-os/releases/7320307`; V72 é aditiva e pode permanecer no banco.

O release `9e83fdb` acrescenta a ponte fiscal explícita entre documento
pendente e preparação de homologação, ainda sem bean, endpoint ou envio. Os
**928 testes backend** passaram em PostgreSQL descartável isolado. Na Web,
“Confirmar e receber” espera o saldo atualizado após criar o pedido; falha da
consulta deixa o valor vazio e avisa o operador. As imagens anteriores estão
marcadas `farelo-os-api:pre-9e83fdb` e `farelo-os-web:pre-9e83fdb`; o backup
pré-deploy passou pela validação do `pg_restore --list`. Após o deploy, API
`UP`, login HTTP 200, Flyway V71 `success`, 52 produtos, zero documentos
fiscais e monitor privado sem alerta. O ensaio presencial do pagamento e da
impressão ainda falta.

O A1 e sua senha foram instalados em 27/10 como credenciais cifradas do
systemd, separadas dos contêineres. O Java 21 abriu o A1 real em processo
transitório sem rede; o PDV continua operando sem essas credenciais. O
procedimento, teste e limite de segurança estão em
[`deploy/fiscal/README.md`](fiscal/README.md).

`7320307` atualizou somente a Web com o cruzamento de NCM dos 52 produtos em
`/admin/fiscal-review`. Há 44 NCMs candidatos históricos e 8 itens sem NCM
associado; a tela é apenas leitura. Build de produção, lint, TypeScript e
formatação passaram; o UUID de cada item da revisão foi conferido com os 52
produtos do banco. Após a atualização: `/login` HTTP 200, API e banco saudáveis,
monitor privado sem alerta. A imagem Web anterior permanece como
`farelo-os-web:pre-7320307`. Para reverter somente a Web, remarcar essa imagem
como `farelo-os-web:latest`, recriar somente `web` com o Compose do release
`ac06b6c`, verificar o login e voltar `current` para `ac06b6c`. Não restaurar o
banco para reverter esta alteração de interface.

O release `aa35560` aplicou a migração Flyway V71 (unicidade de documento fiscal
por comanda) após **926 testes backend** em PostgreSQL descartável e backup local
validado. Antes da migração havia zero documentos e zero duplicatas; depois,
V71 está marcada como sucesso, há 52 produtos, zero documentos, API `UP`, login
HTTP 200 e monitor privado sem alerta. A Web sincroniza detalhes/saldo da
comanda a cada 10 s. As imagens anteriores estão nas tags
`farelo-os-api:pre-aa35560` e `farelo-os-web:pre-aa35560`. Reverter só imagens
depois de V71 requer atenção: a migração permanece no banco e o código antigo
deve ser conferido quanto à compatibilidade. Não restaurar o banco para uma
reversão de interface. O Edge Agent atualizado ainda não foi instalado no
Windows da cafeteria; sua impressão física depende do ensaio #8/#9.

Na VPS, como root:

```bash
docker compose --env-file /opt/farelo-os/shared/.env -f /opt/farelo-os/current/deploy/compose.yml ps
docker compose --env-file /opt/farelo-os/shared/.env -f /opt/farelo-os/current/deploy/compose.yml logs --tail=100 api
curl -fsS http://127.0.0.1:3000/
```

O release `92f14f5` atualizou a API para não entregar tickets de pedidos
cancelados à fila da impressora. O release `c21de37` atualizou apenas a Web:
pedidos internos de comanda/balcão enviam `printOnConfirm: false`, e os
controles de impressão ainda não validados foram retirados do PDV. O KDS
continua recebendo os pedidos. Em 24/10, os 913 testes da API passaram em
PostgreSQL descartável; lint, TypeScript e build Web passaram. API, Web e banco
ficaram ativos após o deploy, com `/login` HTTP 200. A imagem Web atual foi
marcada `farelo-os-web:c21de37`. O backup anterior a `92f14f5` está em
`/opt/farelo-os/shared/backups/pre-operational-92f14f5-20261024.dump`, com
catálogo validado. Para voltar apenas a Web anterior, reconstruir a imagem do
release `92f14f5` e recriar somente o serviço `web`; não restaurar o banco.
O volume XSD está montado na API em `/opt/farelo-os/xsd`, mas o fluxo fiscal
ainda não consome o pacote. O ensaio físico reprovou o layout de impressão;
não ligar o Edge Agent antes de validar o fluxo e a fila.

`a13c6e6` mudou o padrão de impressão automática na criação de pedidos:
omitir `printOnConfirm` agora vale `false` nas rotas de comanda e balcão.
`true` explícito continua aceito. A suíte backend inteira passou com **914
testes**, sem falhas, em PostgreSQL descartável. A API foi recriada sozinha,
retornou `UP`, e `/login` respondeu 200; Web e banco seguiram ativos. O backup
`/opt/farelo-os/shared/backups/pre-core05-a13c6e6-20261024.dump` foi validado
com `pg_restore --list` (modo 600), e a imagem anterior da API está marcada
`farelo-os-api:pre-a13c6e6`. Para voltar apenas a API, marcar essa imagem
como `farelo-os-api:latest` e recriar só `api` usando o Compose do release
`c21de37`; depois verificar saúde e apontar `current` para `c21de37`. Não
restaurar o banco para reverter somente esta mudança sem migração.

`0882bb6` adicionou uma fábrica explícita para carregar o pacote oficial XSD
montado somente leitura em `/opt/farelo-os/xsd/PL_010f_v1.04`, conferindo os
cinco arquivos por SHA-256 antes da preparação fiscal. Não cria endpoint, bean
nem envio de NFC-e. **917 testes backend** passaram em PostgreSQL descartável;
API `UP`, `/login` HTTP 200 e arquivo XSD legível dentro do contêiner após
o deploy somente da API. Backup anterior validado em
`/opt/farelo-os/shared/backups/pre-fisc05-0882bb6-20261024.dump` (modo 600);
imagem anterior marcada `farelo-os-api:pre-0882bb6`. Reversão: remarcar essa
imagem como `latest`, recriar apenas `api` com o Compose de `a13c6e6` e
apontar `current` para `a13c6e6` depois de conferir a saúde. Sem migração.

`d058180` expõe a criação idempotente de documento fiscal **pendente** para
comanda fechada e paga, apenas para ADMIN/MANAGER. Corrige a precisão do
horário de criação para manter a resposta estável entre chamadas. **925 testes
backend** passaram, sem falhas/erros/pulados, em PostgreSQL descartável numa
rede isolada, sem montar o socket Docker no contêiner de testes. O backup
`/opt/farelo-os/shared/backups/pre-fisc07-d058180-20261026.dump` foi validado
com `pg_restore --list` e está em modo 600. A imagem anterior da API está
marcada `farelo-os-api:pre-d058180`; a Web e o banco não foram recriados. API
`UP`, `/login` HTTP 200, checagem operacional manual OK, nenhuma pendência
fiscal. O banco temporário de teste foi removido. **Não há reserva de número,
uso do A1, XML ou transmissão de NFC-e por esta rota.** Para reverter só a API,
remarcar `farelo-os-api:pre-d058180` como `farelo-os-api:latest`, recriar apenas
`api` com o Compose de `0882bb6`, verificar saúde e apontar `current` para
`0882bb6`. Não restaurar o banco para reverter apenas esta mudança sem
migration.

Em 26/10, `farelo-health.timer` foi instalado em `/etc/systemd/system` com o
serviço `farelo-health.service`, ambos versionados em `deploy/monitoring/`.
O timer chama o verificador somente leitura de `current` a cada cinco minutos;
a execução inicial terminou com `Result=success`. Consulte
`systemctl status farelo-health.timer` e
`journalctl -u farelo-health.service -n 100 --no-pager` para ver resultados.
O journal registra apenas estados, contagens e idades. **Não existe canal de
alerta ao responsável ainda**: #20 permanece aberto. O teste de backup local
falhará quando o dump mais recente passar de 26 horas.

`9af697b` acrescentou `deploy/backup-local.sh` e timer diário
`farelo-backup-local.timer` (subtarefa #23). O script escreve um `pg_dump -Fc`
temporário com permissão `600`, confere o catálogo por `pg_restore --list` no
contêiner PostgreSQL e só então publica o arquivo em
`/opt/farelo-os/shared/backups/`. Dois ensaios reais passaram em 26/10,
com timer `active`, API `UP`, `/login` HTTP 200 e `local_backup=OK` no monitor.
Um desses dumps foi restaurado com `pg_restore --exit-on-error` em PostgreSQL
16 descartável sem rede; o banco restaurado continha 52 produtos e o
contêiner foi removido.
Consultar `systemctl status farelo-backup-local.timer` e
`journalctl -u farelo-backup-local.service -n 50 --no-pager`. O script não faz
retenção nem criptografia externa. **Não considera o ticket #19 concluído**:
configurar destino remoto cifrado, restauração isolada, retenção e alerta.

Para reverter apenas a API `f522695` para `cfb6b05`, na VPS:

```bash
docker image tag farelo-os-api:pre-f522695 farelo-os-api:latest
docker compose --env-file /opt/farelo-os/shared/.env -f /opt/farelo-os/releases/cfb6b05/deploy/compose.yml up -d --no-build --no-deps api
ln -sfnT /opt/farelo-os/releases/cfb6b05 /opt/farelo-os/current
```

Para reverter a API `cfb6b05` para `7138766`, na VPS:

```bash
docker image tag farelo-os-api:pre-cfb6b05 farelo-os-api:latest
docker compose --env-file /opt/farelo-os/shared/.env -f /opt/farelo-os/releases/7138766/deploy/compose.yml up -d --no-build --no-deps api
ln -sfnT /opt/farelo-os/releases/7138766 /opt/farelo-os/current
```

Para reverter as imagens de API e Web anteriores a `7138766`, na VPS:

```bash
docker image tag farelo-os-api:pre-7138766 farelo-os-api:latest
docker image tag farelo-os-web:pre-7138766 farelo-os-web:latest
docker compose --env-file /opt/farelo-os/shared/.env -f /opt/farelo-os/releases/9fef5f7/deploy/compose.yml up -d --no-build --no-deps api web
ln -sfnT /opt/farelo-os/releases/9fef5f7 /opt/farelo-os/current
docker compose --env-file /opt/farelo-os/shared/.env -f /opt/farelo-os/current/deploy/compose.yml ps
```

Conferir `http://127.0.0.1:3000/login` e a saúde da API após a reversão. As migrações V56–V70 são aditivas e não são removidas por trocar as imagens. O backup anterior a `7138766` está em `/opt/farelo-os/shared/backups/pre-fiscal-7138766-20261023.dump`; não restaurar o banco automaticamente ao reverter apenas o aplicativo. `d3fa06c` acrescentou a prévia interna em `/admin/menu-preview`; `4c3f335` atualizou as descrições e ordem a partir da vitrine QR.

`a00adff` corrigiu os controles de Cardápio e PDV na lista de produtos. Somente a imagem web foi atualizada; a API e o banco continuaram na versão `4c3f335`.
`9bfab72` acrescentou a aba `/admin/cardapio`, organizada por seções, e atualizou somente a imagem web. Em 23/10/2026, duas seções e 13 itens do painel autenticado do QR Pedir foram adicionados ao banco, pausados no cardápio e no PDV. O backup anterior está em `/opt/farelo-os/shared/backups/pre-qr-admin-extra-20261023.dump` (modo 600). O catálogo passou a ter 11 seções e 52 produtos; a extração está em `sischef-extraction/qrpedir-admin-extra-20261023.md`.

`bdf094d` acrescentou campos explícitos de GTIN, emitente e perfil fiscal, o painel de pendências no Admin, preparação do cancelamento e unicidade da chave fiscal. As migrações V62–V65 foram aplicadas em 23/10/2026 após um backup validado em `/opt/farelo-os/shared/backups/pre-fiscal-bdf094d-20261023.dump` (modo 600). Os 869 testes do backend passaram em PostgreSQL descartável na VPS; login HTTP 200 e API `UP` após o deploy. O banco continua com 52 produtos, 39 disponíveis no PDV, 0 perfis fiscais e 0 configuração do emitente. Nenhuma NFC-e foi enviada e o fechamento do caixa ainda não aciona emissão.

`e8a5c32` acrescentou unidade fiscal e GTIN editáveis no Admin, snapshots de nome/GTIN/unidade no item vendido e uma cerca persistente para tentativas de emissão. Migrações V66–V68 aplicadas em 23/10/2026. Os 880 testes do backend passaram em PostgreSQL descartável na VPS. Backup validado em `/opt/farelo-os/shared/backups/pre-fiscal-e8a5c32-20261023.dump` (modo 600, 88689 bytes), imagens antigas preservadas como `pre-e8a5c32`. API `UP`, login HTTP 200. Contagens após deploy: 52 produtos, 0 perfis fiscais, 0 configuração do emitente, 0 documentos e 0 tentativas de transmissão. Não há envio automático de NFC-e.

`9fef5f7` acrescentou descontos explícitos no XML, snapshot de classificação no item da venda, painel de pendências de GTIN/unidade e reserva atômica de número por CNPJ/ambiente/série (sem sequência inicial). Migrações V69–V70 aplicadas em 23/10/2026. Os 888 testes do backend passaram em PostgreSQL descartável na VPS. Backup validado em `/opt/farelo-os/shared/backups/pre-fiscal-9fef5f7-20261023.dump` (modo 600, 92205 bytes), imagens anteriores preservadas como `pre-9fef5f7`. API `UP`, login HTTP 200. Contagens: 52 produtos, 0 perfis, 0 emitente, 0 sequências, 0 documentos, 0 tentativas. Fechamento de comanda ainda não emite NFC-e.

`7138766` acrescentou revisão fiscal dos 39 itens do cardápio no Admin, montagem de rascunho da comanda fechada, preparação do XML assinado com validação XSD explícita e transmissão de homologação protegida contra duplicidade. Os serviços de preparação e transmissão são explícitos, sem endpoint ou acionamento no fechamento do caixa. Sem nova migração. Os 906 testes do backend passaram em PostgreSQL descartável na VPS; o build de produção da Web passou. Backup validado em `/opt/farelo-os/shared/backups/pre-fiscal-7138766-20261023.dump` (modo 600, 95909 bytes), imagens anteriores preservadas como `pre-7138766`. API `UP`, login HTTP 200, Flyway V70. Contagens: 52 produtos, 0 perfis, 0 emitente, 0 sequências, 0 documentos, 0 tentativas; nenhuma nota transmitida.

`cfb6b05` atualizou somente a API com a conferência do CNPJ do certificado ICP-Brasil contra o emitente antes da reserva de número. O teste em PostgreSQL descartável confirmou rollback de número e XML em falha de validação e unicidade de identidade fiscal; os testes específicos passaram (8/8). API `UP`, login HTTP 200. Sem migração nova ou transmissão de nota. A imagem anterior da API está preservada como `pre-cfb6b05`.

`f522695` acrescentou criação controlada de um documento fiscal pendente por comanda fechada. O bloqueio da comanda impede que duas solicitações simultâneas criem documentos diferentes; uma reemissão exige caminho explícito. Os **912 testes** do backend passaram em PostgreSQL descartável na VPS, inclusive o teste de concorrência. Backup pré-atualização validado em `/opt/farelo-os/shared/backups/pre-fiscal-f522695-20261023.dump` (modo 600); imagem anterior preservada como `pre-f522695`. API `UP`, login HTTP 200, sem migração, endpoint fiscal novo ou transmissão de nota. Contagens após deploy: 52 produtos, 0 perfis, 0 emitente, 0 sequências, 0 documentos e 0 tentativas.

Em 24/10, o pacote público `PL_010f_v1.04.zip` foi baixado do Portal Nacional da NF-e, verificado pelo SHA-256 `b8589490a58a09a993a80e6ac4d7ed10f20892061ecfc56719337098d4b95998` e extraído em `/opt/farelo-os/shared/xsd/PL_010f_v1.04/`. A montagem somente leitura em `/opt/farelo-os/xsd` está no Compose de `f83fda0`; **o contêiner API existente não foi recriado com esse volume** para preservar o ensaio presencial. O XSD tampouco foi fornecido aos serviços explícitos de homologação.

`f83fda0` atualizou **somente a Web** para permitir imprimir a conferência não fiscal pelo diálogo do Edge na Epson USB da cafeteria. A nova Web respondeu HTTP 200 em `/login`; API e banco não foram reiniciados. O Compose apontado por `/opt/farelo-os/current` agora contém a montagem XSD, mas o contêiner API existente ainda não foi recriado com ela. O teste em papel depende do usuário no Windows. Dois tickets de cozinha estavam pendentes na fila em 24/10; conferir antes de ligar qualquer Edge Agent.

A imagem Web do código anterior foi reconstruída do release `f522695` e guardada como `farelo-os-web:pre-f83fda0`. Para voltar somente a Web, na VPS: `docker image tag farelo-os-web:pre-f83fda0 farelo-os-web:latest` e `docker compose --env-file /opt/farelo-os/shared/.env -f /opt/farelo-os/releases/f522695/deploy/compose.yml up -d --no-build --no-deps web`. Não reverter API nem banco por causa de uma falha apenas na impressão do navegador.

O release `ac2f37c` aplicou 55 migrações Flyway em 16/10/2026; `dc09445` ativou a política explícita de acesso da API. Em 17/10, `edfabc1` foi implantado sem nova migração. A API respondeu `UP`, o login HTTP 200, e a fila de impressão respondeu 401 sem chave de dispositivo, 200 com chave e 401 na rota de funcionários sem JWT. A chave está somente em `/opt/farelo-os/shared/.env` e precisa ser configurada no Edge Agent da estação física antes do teste de impressão. As imagens anteriores foram preservadas com as tags `dc09445` para reversão. O primeiro administrador usa
`adm.farelodebolo` sem e-mail; a senha foi inserida diretamente no Terminal e
somente o hash BCrypt fica no banco. O backup anterior à atualização de login está em
`/opt/farelo-os/shared/backups/pre-username-login-20261012.sql.gz`.
Um dump novo em `/opt/farelo-os/shared/backups/pre-upgrade-20261016.dump`
foi restaurado em PostgreSQL temporário isolado, com 27 tabelas verificadas.
O banco tinha duas contas ativas (ADM e `caixa.farelodebolo`) e zero produtos na auditoria de 17/10; o login do Caixa ainda precisa ser ensaiado. Um novo dump pré-release está em `/opt/farelo-os/shared/backups/pre-release-edfabc1.dump` (modo 600), com catálogo `pg_restore --list` validado. O `restic` 0.16.4 está instalado na VPS; o script e timer de backup externo foram preparados no código, mas não instalados: falta destino externo, credenciais e teste de restauração. Ainda faltam catálogo atual conferido, backup externo automático, domínio/HTTPS e homologação fiscal. Não publique a porta
3000 ou a API diretamente na internet antes dessas etapas.
