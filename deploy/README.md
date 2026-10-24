# Farelo OS na VPS Contabo

Implantação privada inicial em `209.126.6.77`, Ubuntu 24.04.5. Em 24/10/2026,
a API usa `0882bb6` e a Web usa `c21de37`. O atalho `/opt/farelo-os/current`
aponta para `/opt/farelo-os/releases/0882bb6`. Os segredos ficam somente na VPS em
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
