"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Card } from "@/components/ui/card";
import candidates from "@/data/fiscal-candidates-20261027.json";
import { listFiscalProfiles } from "@/lib/api/fiscal-profiles";
import { listProducts } from "@/lib/api/products";

const confidenceOrder = ["ALTA", "MÉDIA", "AMBÍGUO", "SEM VÍNCULO"] as const;
const confidenceDescriptions: Record<string, string> = {
  ALTA: "Nome, grupo, preço e status coincidem; identidade ainda exige conferência.",
  MÉDIA: "Há divergência de variante, nome, grupo ou preço.",
  AMBÍGUO: "Há mais de um cadastro plausível ou variante indefinida.",
  "SEM VÍNCULO": "Não foi encontrado cadastro individual equivalente.",
};

function shown(value: string | null | undefined) {
  return value && value !== "—" ? value : "Não observado";
}

export default function FiscalReviewPage() {
  const [search, setSearch] = useState("");
  const [confidence, setConfidence] = useState("TODOS");
  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: listProducts,
  });
  const profilesQuery = useQuery({
    queryKey: ["fiscal-profiles"],
    queryFn: listFiscalProfiles,
  });
  const productById = new Map(
    (productsQuery.data ?? []).map((item) => [item.id, item]),
  );
  const profileById = new Map(
    (profilesQuery.data ?? []).map((item) => [item.id, item]),
  );
  const filtered = candidates.items.filter((item) => {
    if (confidence !== "TODOS" && item.confidence !== confidence) return false;
    const query = search.trim().toLocaleLowerCase("pt-BR");
    return (
      !query ||
      `${item.name} ${item.section} ${item.sischefCandidate ?? ""}`
        .toLocaleLowerCase("pt-BR")
        .includes(query)
    );
  });

  return (
    <AuthGuard allow={["ADMIN"]}>
      <AdminShell>
        <div className="mx-auto flex max-w-5xl flex-col gap-6">
          <header>
            <Link
              href="/admin/fiscal-profiles"
              className="text-primary text-sm"
            >
              ← Perfis fiscais
            </Link>
            <h1 className="mt-2 font-serif text-2xl font-semibold">
              Revisão fiscal do cardápio
            </h1>
            <p className="text-ink-soft mt-1 text-sm">
              Cruzamento histórico de {candidates.items.length} itens do QR
              Pedir com cadastro, relatório e XMLs do Sischef; complemento dos
              13 itens do painel autenticado em {candidates.observedAt}.
            </p>
          </header>
          <Card>
            <h2 className="font-semibold">Como usar esta lista</h2>
            <p className="text-ink-soft mt-1 text-sm">
              Confiança descreve apenas a ligação entre produtos. Os códigos
              exibidos são evidência histórica, não classificação aprovada. O
              código interno do Sischef não é GTIN validado. Esta tela não grava
              dados fiscais nem emite NFC-e.
            </p>
            <div className="mt-3 flex flex-wrap gap-2 text-xs">
              {confidenceOrder.map((level) => (
                <span
                  key={level}
                  className="border-line rounded-full border px-3 py-1"
                >
                  {level}:{" "}
                  {
                    candidates.items.filter((item) => item.confidence === level)
                      .length
                  }
                </span>
              ))}
            </div>
            <p className="text-ink-soft mt-3 text-xs">
              Para cada item, confirmar identidade por ID, NCM/CEST/CFOP/CSOSN,
              origem ICMS, PIS/COFINS, GTIN ou SEM GTIN e unidade fiscal com a
              contabilidade e os documentos de origem. CEST depende de
              aplicabilidade. Conferir opções e preço final à parte.
            </p>
          </Card>
          <div className="flex flex-wrap gap-3">
            <input
              type="search"
              aria-label="Buscar produto ou candidato Sischef"
              placeholder="Buscar produto ou candidato"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="border-line bg-surface min-w-64 flex-1 rounded-lg border px-3 py-2 text-sm"
            />
            <select
              aria-label="Filtrar por confiança do vínculo"
              value={confidence}
              onChange={(event) => setConfidence(event.target.value)}
              className="border-line bg-surface rounded-lg border px-3 py-2 text-sm"
            >
              <option value="TODOS">Todos os vínculos</option>
              {confidenceOrder.map((level) => (
                <option key={level} value={level}>
                  {level}
                </option>
              ))}
            </select>
          </div>
          <p className="text-ink-soft text-sm">
            {filtered.length} itens na revisão
          </p>
          <div className="flex flex-col gap-3">
            {filtered.map((item) => {
              const product = productById.get(item.productId);
              const profile = product?.fiscalProfileId
                ? profileById.get(product.fiscalProfileId)
                : null;
              return (
                <Card key={item.productId}>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <h2 className="font-semibold">{item.name}</h2>
                      <p className="text-ink-soft text-xs">
                        {item.section} ·{" "}
                        {item.qrId ? `QR ${item.qrId}` : "Painel QR Pedir"} ·
                        preço observado R$ {item.qrPrice}
                      </p>
                    </div>
                    <span className="border-line rounded-full border px-3 py-1 text-xs font-semibold">
                      {item.confidence}
                    </span>
                  </div>
                  <p className="text-ink-soft mt-2 text-sm">
                    {confidenceDescriptions[item.confidence]}
                  </p>
                  <p className="mt-3 text-sm">
                    <strong>Candidato Sischef:</strong>{" "}
                    {shown(item.sischefCandidate)}
                  </p>
                  <dl className="mt-3 grid grid-cols-2 gap-2 text-xs sm:grid-cols-3 lg:grid-cols-6">
                    {(
                      [
                        ["NCM", item.ncm],
                        ["CEST", item.cest],
                        ["CFOP", item.cfop],
                        ["CSOSN", item.csosn],
                        ["Código interno", item.sischefInternalCode],
                        ["Linhas XML", item.xmlLines?.toString() ?? null],
                      ] as [string, string | null][]
                    ).map(([label, value]) => (
                      <div
                        key={label}
                        className="border-line rounded-lg border p-2"
                      >
                        <dt className="text-ink-soft">{label}</dt>
                        <dd className="mt-1 font-semibold">{shown(value)}</dd>
                      </div>
                    ))}
                  </dl>
                  <p className="text-ink-soft mt-3 text-xs">
                    {item.reviewNote}
                  </p>
                  <div className="border-line mt-3 border-t pt-3 text-xs">
                    {productsQuery.isPending || profilesQuery.isPending ? (
                      <p>Consultando cadastro Farelo OS...</p>
                    ) : productsQuery.isError || profilesQuery.isError ? (
                      <p className="text-red">
                        Não foi possível comparar com o cadastro Farelo OS.
                      </p>
                    ) : product ? (
                      <p>
                        <strong>Farelo OS agora:</strong> GTIN{" "}
                        {shown(product.gtin)} · unidade fiscal{" "}
                        {shown(product.fiscalUnit)} · perfil{" "}
                        {profile?.name ?? "não atribuído"}. Estes campos
                        preenchidos não equivalem a aprovação contábil.
                      </p>
                    ) : (
                      <p className="text-red">
                        Produto não encontrado no cadastro Farelo OS atual.
                      </p>
                    )}
                  </div>
                </Card>
              );
            })}
          </div>
          <Link
            href="/admin/products"
            className="text-primary text-sm font-semibold"
          >
            Abrir cadastro de produtos para revisão manual →
          </Link>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}
