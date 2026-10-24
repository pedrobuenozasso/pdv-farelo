# Verificação privada da VPS (OPS-02)

`check-private-vps.sh` é um diagnóstico **somente leitura** da instalação
`deploy/compose.yml`. Ele confere os três contêineres, PostgreSQL, saúde da
API, login Web local, ocupação do disco, idade do dump local mais recente e
contagens/idade das filas outbox, impressão e fiscal. A saída contém apenas
estados, contagens e idades; stderr de Docker, curl e PostgreSQL é suprimido
para evitar revelar dados operacionais.

Na VPS, executar manualmente como operador que pode ler `.env` e acessar
Docker:

```bash
bash /opt/farelo-os/current/deploy/monitoring/check-private-vps.sh
```

Código de saída `0` indica que os limites passaram; `1` indica falha em
algum check. Um `print_pending_eligible` acima de zero é informativo enquanto
a impressora física não estiver pronta. `fiscal_attention` inclui documentos
pendentes, em processamento, rejeitados ou em contingência e exige análise
humana; `fiscal_submission_uncertain` acima de zero gera falha. A consulta não
mostra pedidos, nomes, números de comanda, XMLs ou segredos.

Os limites iniciais são 85% de uso do disco, 26 horas desde o dump local e
600 segundos para o outbox pendente mais antigo. Podem ser ajustados por
`FARELO_MONITOR_DISK_MAX_PERCENT`, `FARELO_MONITOR_BACKUP_MAX_HOURS` e
`FARELO_MONITOR_OUTBOX_MAX_SECONDS`. `FARELO_MONITOR_ROOT` só é necessário
em ambiente de teste ou instalação fora de `/opt/farelo-os`.

Esta verificação **não** prova que existe cópia externa nem restauração
testada. Não há timer, cron nem destino de alerta instalado neste ticket.
Antes de fechar OPS-02, escolher canal de aviso, executar a checagem na VPS,
validar uma falha simulada e confirmar que o aviso chega ao responsável.
O teste isolado do script roda com
`bash deploy/monitoring/test-check-private-vps.sh`.
