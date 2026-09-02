/**
 * Teste de integração de `printOverTcp` (ver printerTransport.ts) — sem
 * mocks de rede: sobe um servidor TCP local de verdade (`net.createServer`,
 * escutando em `127.0.0.1` numa porta efêmera, `listen(0, ...)`) e confirma
 * o comportamento real do transporte contra ele.
 *
 * Esta é a prova de correção do transporte pedida pelo ticket FARELO-078 na
 * ausência de uma impressora física real neste ambiente de desenvolvimento
 * (ver README.md): não valida o protocolo ESC/POS em si (isso é
 * responsabilidade da impressora física, fora do nosso controle), mas
 * valida de ponta a ponta que os bytes corretos chegam ao socket de destino
 * e que falhas de rede reais (conexão recusada, timeout) rejeitam a
 * Promise em vez de travar o processo.
 */

import { test } from "node:test";
import assert from "node:assert/strict";
import net from "node:net";
import { printOverTcp } from "./printerTransport.js";

/** Sobe `server` em 127.0.0.1 numa porta efêmera e devolve a porta escolhida pelo SO. */
function listen(server: net.Server): Promise<number> {
  return new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      if (address === null || typeof address === "string") {
        reject(new Error("Endereço do servidor de teste inesperado"));
        return;
      }
      resolve(address.port);
    });
  });
}

function closeServer(server: net.Server): Promise<void> {
  return new Promise((resolve) => server.close(() => resolve()));
}

test("printOverTcp entrega os bytes exatos ao servidor de destino", async () => {
  const received: Buffer[] = [];
  let acceptedSocket: net.Socket | undefined;
  const server = net.createServer();

  const serverReceivedEverything = new Promise<void>((resolve) => {
    // "end" só dispara depois que o servidor recebeu tudo que o cliente
    // enviou (o FIN chega depois dos dados, na mesma conexão TCP) — ponto
    // seguro para comparar os bytes recebidos, sem depender de um sleep
    // arbitrário no teste.
    server.on("connection", (socket) => {
      acceptedSocket = socket;
      socket.on("data", (chunk) => received.push(chunk));
      socket.on("end", () => resolve());
    });
  });

  const port = await listen(server);
  const ticket = Buffer.from([
    0x1b,
    0x40, // ESC @
    0x1b,
    0x21,
    0x38, // ESC ! 0x38
    0x48,
    0x69, // "Hi"
    0x1d,
    0x56,
    0x00, // GS V 0 (corte)
  ]);

  await printOverTcp("127.0.0.1", port, ticket);
  await serverReceivedEverything;

  assert.deepEqual(
    Buffer.concat(received),
    ticket,
    "os bytes recebidos pelo servidor devem ser idênticos ao ticket enviado",
  );

  acceptedSocket?.destroy();
  await closeServer(server);
});

test("printOverTcp rejeita quando a conexão é recusada (impressora offline/endereço errado)", async () => {
  // Sobe um servidor só para reservar uma porta livre do SO, depois fecha
  // — a porta volta a não ter nada escutando, garantindo ECONNREFUSED
  // determinístico ao tentar conectar de novo (mesmo raciocínio de uma
  // impressora desligada num endereço configurado).
  const probe = net.createServer();
  const port = await listen(probe);
  await closeServer(probe);

  await assert.rejects(
    () =>
      printOverTcp("127.0.0.1", port, Buffer.from([0x00]), {
        timeoutMs: 1000,
      }),
    (error: unknown) => {
      assert.ok(error instanceof Error);
      assert.match(error.message, /ECONNREFUSED/);
      return true;
    },
  );
});

test("printOverTcp rejeita por timeout quando a impressora nunca drena os dados", async () => {
  let acceptedSocket: net.Socket | undefined;

  // `pauseOnConnect: true` garante que o servidor nunca lê nenhum byte da
  // conexão aceita (nem sequer o buffer interno do Node é preenchido) —
  // simula uma impressora travada/sem papel que aceita a conexão TCP mas
  // nunca drena o buffer, forçando o controle de fluxo do TCP a estagnar a
  // escrita do lado do cliente.
  const server = net.createServer({ pauseOnConnect: true }, (socket) => {
    acceptedSocket = socket;
  });

  const port = await listen(server);

  // Buffer grande o bastante para exceder qualquer buffer de recepção
  // padrão do SO (tipicamente dezenas a poucas centenas de KB) já que o
  // servidor nunca lê nada — garante que o `write()` não complete dentro
  // do timeout curto configurado abaixo.
  const oversizedTicket = Buffer.alloc(20 * 1024 * 1024, 0x00);

  await assert.rejects(
    () => printOverTcp("127.0.0.1", port, oversizedTicket, { timeoutMs: 200 }),
    /Timeout ao imprimir/,
  );

  acceptedSocket?.destroy();
  await closeServer(server);
});
