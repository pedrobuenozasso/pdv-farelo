# Fiscal: decisões que dependem do Farelo e da contabilidade

Estado em 23/10/2026: o release `7138766` está na VPS privada. O caixa **não** emite NFC-e; nenhum XML foi enviado à SEFAZ. O banco tem 52 produtos e ainda não tem perfil fiscal, configuração do emitente, sequência fiscal ou documento emitido. O pacote A1 e sua senha continuam fora do Git e da VPS.

## O que Pedro e a contabilidade precisam conferir

1. **Emitente atual.** Na aba de configuração fiscal do Admin, conferir CNPJ, inscrição estadual, CRT, razão social e endereço de emissão. Os XMLs antigos do Sischef são evidência histórica, mas não substituem a confirmação de que esses dados continuam válidos.
2. **Classificação do cardápio.** Abrir `/admin/fiscal-review` e confirmar os vínculos dos 39 itens ativos com o Sischef. A tela separa 12 vínculos de alta confiança, 18 médios, 4 ambíguos e 5 sem equivalente seguro. Para cada produto, validar com a contabilidade NCM, eventual CEST, CFOP, CSOSN/CST, origem ICMS, PIS/COFINS, unidade fiscal e GTIN real ou `SEM GTIN`. Os 13 itens adicionais pausados precisam de revisão antes de ativação. O relatório detalhado está em `apps/web/src/data/fiscal-review-decisions-20261023.md`.
3. **Produtos com opções.** Confirmar como adicionais e variantes aparecem na venda e no documento fiscal, com preço final e classificação de cada componente. O sistema bloqueia a montagem fiscal desses itens até haver regra explícita.
4. **Numeração e credencial.** Confirmar a série e o próximo número de NFC-e por ambiente com a contabilidade/Sischef. Quando o ensaio de homologação estiver pronto, inserir a senha do A1 somente no Terminal local ou no mecanismo seguro indicado; nunca por chat, Git ou formulário improvisado.

## Trabalho técnico que permanece com a equipe

- Instalar e verificar um pacote XSD oficial, versionado, sem busca de esquema na internet em tempo de emissão.
- Concluir a custódia do A1, o vínculo CNPJ certificado/emitente, a criação controlada de documentos pendentes e o teste de rollback no PostgreSQL.
- Executar homologação real, consulta de resposta incerta, rejeição e cancelamento; conferir XML, DANFE e QR Code.
- Implementar contingência, guarda/backup externo de XML e procedimento de recuperação antes de habilitar emissão em produção.

As classificações sugeridas pelo Sischef não foram aplicadas automaticamente. A revisão pode ser feita aos poucos; a emissão continuará bloqueada enquanto os campos necessários faltarem.
