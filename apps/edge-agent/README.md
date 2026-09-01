# apps/edge-agent

`farelo-edge-agent` — serviço executado no mini PC local da cafeteria, responsável por:

- conectar com a API
- buscar/receber `PrintJob`s
- imprimir (preferencialmente ESC/POS)
- reportar status de impressão
- verificar impressoras
- manter fila temporária local

Este serviço nunca deve conter regra de negócio de pedidos — é apenas infraestrutura
de dispositivos. Implementação inicial é escopo do FARELO-075 (Epic 6 — Impressão).
