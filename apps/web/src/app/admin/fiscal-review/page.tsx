"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Card } from "@/components/ui/card";
import candidates from "@/data/fiscal-candidates-20261027.json";
import { apiErrorMessage } from "@/lib/api/client";
import {
  listFiscalNcmReviews,
  saveFiscalNcmReview,
  saveFiscalTaxCodesReview,
  type FiscalNcmReview,
} from "@/lib/api/fiscal-ncm-reviews";
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

function TaxCodesReviewEditor({
  productId,
  review,
}: {
  productId: string;
  review: FiscalNcmReview | undefined;
}) {
  const queryClient = useQueryClient();
  const [cest, setCest] = useState(review?.draftCest ?? "");
  const [cfop, setCfop] = useState(review?.draftCfop ?? "");
  const [csosn, setCsosn] = useState(review?.draftCsosn ?? "");
  const valid =
    (!cest || /^\d{7}$/.test(cest)) &&
    (!cfop || /^\d{4}$/.test(cfop)) &&
    (!csosn || /^\d{3}$/.test(csosn));
  const mutation = useMutation({
    mutationFn: () =>
      saveFiscalTaxCodesReview(productId, {
        cest: cest || null,
        cfop: cfop || null,
        csosn: csosn || null,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["fiscal-ncm-reviews"] }),
  });

  return (
    <div className="border-line mt-3 border-t pt-3 text-sm">
      <p className="text-ink-soft text-xs">
        Códigos dos XMLs antigos: CEST {shown(review?.sourceCest)}, CFOP{" "}
        {shown(review?.sourceCfop)}, CSOSN {shown(review?.sourceCsosn)}.
        Corrigir aqui não altera a emissão de NFC-e.
      </p>
      <div className="mt-2 flex flex-wrap items-end gap-3">
        {(
          [
            ["CEST", cest, setCest, 7],
            ["CFOP", cfop, setCfop, 4],
            ["CSOSN", csosn, setCsosn, 3],
          ] as const
        ).map(([label, value, setter, length]) => (
          <label
            key={label}
            className="flex flex-col gap-1 text-xs font-medium"
          >
            {label} para conferência
            <input
              aria-label={`${label} para conferência`}
              inputMode="numeric"
              maxLength={length}
              value={value}
              onChange={(event) =>
                setter(event.target.value.replace(/\D/g, ""))
              }
              className="border-line bg-surface w-28 rounded-lg border px-3 py-2 text-sm"
            />
          </label>
        ))}
        <button
          type="button"
          disabled={!valid || mutation.isPending}
          onClick={() => mutation.mutate()}
          className="bg-primary rounded-lg px-3 py-2 text-xs font-semibold text-white disabled:opacity-50"
        >
          {mutation.isPending ? "Salvando..." : "Salvar códigos"}
        </button>
      </div>
      {mutation.isError && (
        <p className="text-red mt-2 text-xs">
          {apiErrorMessage(
            mutation.error,
            "Não foi possível salvar os códigos.",
          )}
        </p>
      )}
    </div>
  );
}

function NcmReviewEditor({
  productId,
  review,
  sourceNcm,
}: {
  productId: string;
  review: FiscalNcmReview | undefined;
  sourceNcm: string | null;
}) {
  const queryClient = useQueryClient();
  const [draftNcm, setDraftNcm] = useState(review?.draftNcm ?? sourceNcm ?? "");
  const [reviewed, setReviewed] = useState(review?.reviewed ?? false);
  const isValid = !draftNcm || /^[0-9]{8}$/.test(draftNcm);
  const mutation = useMutation({
    mutationFn: () =>
      saveFiscalNcmReview(productId, {
        draftNcm: draftNcm || null,
        reviewed,
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["fiscal-ncm-reviews"] }),
  });

  return (
    <div className="border-line mt-3 border-t pt-3 text-sm">
      <p className="text-ink-soft text-xs">
        NCM original Sischef: {shown(review?.sourceNcm ?? sourceNcm)}. O
        rascunho abaixo não alimenta a emissão de NFC-e.
      </p>
      <div className="mt-2 flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-xs font-medium">
          NCM para conferência
          <input
            aria-label="NCM para conferência"
            inputMode="numeric"
            maxLength={8}
            value={draftNcm}
            onChange={(event) => {
              setDraftNcm(event.target.value.replace(/\D/g, ""));
              setReviewed(false);
            }}
            className="border-line bg-surface w-36 rounded-lg border px-3 py-2 text-sm"
          />
        </label>
        <label className="flex items-center gap-2 pb-2 text-xs">
          <input
            type="checkbox"
            checked={reviewed}
            disabled={!draftNcm || !isValid}
            onChange={(event) => setReviewed(event.target.checked)}
          />
          Conferi este NCM
        </label>
        <button
          type="button"
          disabled={!isValid || mutation.isPending || (reviewed && !draftNcm)}
          onClick={() => mutation.mutate()}
          className="bg-primary rounded-lg px-3 py-2 text-xs font-semibold text-white disabled:opacity-50"
        >
          {mutation.isPending ? "Salvando..." : "Salvar rascunho"}
        </button>
      </div>
      {review?.reviewed && reviewed && !mutation.isPending && (
        <p className="mt-2 text-xs text-green-700">Conferido no cadastro.</p>
      )}
      {mutation.isError && (
        <p className="text-red mt-2 text-xs">
          {apiErrorMessage(mutation.error, "Não foi possível salvar o NCM.")}
        </p>
      )}
    </div>
  );
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
  const reviewsQuery = useQuery({
    queryKey: ["fiscal-ncm-reviews"],
    queryFn: listFiscalNcmReviews,
  });
  const productById = new Map(
    (productsQuery.data ?? []).map((item) => [item.id, item]),
  );
  const profileById = new Map(
    (profilesQuery.data ?? []).map((item) => [item.id, item]),
  );
  const reviewByProductId = new Map(
    (reviewsQuery.data ?? []).map((item) => [item.productId, item]),
  );
  const filtered = candidates.items.filter((item) => {
    if (confidence !== "TODOS" && item.confidence !== confidence) return false;
    const query = search.trim().toLocaleLowerCase("pt-BR");
    return (
      !query ||
      `${item.name} ${item.section} ${item.sischefCandidate ?? ""} ${item.ncm ?? ""} ${reviewByProductId.get(item.productId)?.draftNcm ?? ""}`
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
              código interno do Sischef não é GTIN validado. Os rascunhos de
              NCM, CEST, CFOP e CSOSN podem ser corrigidos aqui; eles não
              alteram o perfil usado na NFC-e.
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
            {filtered.length} itens na revisão ·{" "}
            {reviewsQuery.data?.filter((item) => item.reviewed).length ?? 0}{" "}
            conferidos de {candidates.items.length}
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
                  {reviewsQuery.isPending ? (
                    <p className="text-ink-soft mt-3 text-xs">
                      Carregando rascunho fiscal...
                    </p>
                  ) : reviewsQuery.isError ? (
                    <p className="text-red mt-3 text-xs">
                      Não foi possível carregar os rascunhos fiscais.
                    </p>
                  ) : product ? (
                    <div>
                      <NcmReviewEditor
                        key={`${item.productId}:${reviewByProductId.get(item.productId)?.updatedAt ?? "new"}`}
                        productId={item.productId}
                        review={reviewByProductId.get(item.productId)}
                        sourceNcm={item.ncm}
                      />
                      <TaxCodesReviewEditor
                        key={`tax:${item.productId}:${reviewByProductId.get(item.productId)?.updatedAt ?? "new"}`}
                        productId={item.productId}
                        review={reviewByProductId.get(item.productId)}
                      />
                    </div>
                  ) : null}
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
