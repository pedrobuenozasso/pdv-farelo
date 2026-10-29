"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/client";
import {
  getCompanyFiscalConfiguration,
  type CompanyFiscalConfiguration,
} from "@/lib/api/company-fiscal-configuration";
import {
  createFiscalProfile,
  listFiscalProfiles,
  updateFiscalProfile,
  type FiscalProfile,
  type FiscalProfileInput,
} from "@/lib/api/fiscal-profiles";
import { listProducts, type Product } from "@/lib/api/products";

const QUERY_KEY = ["fiscal-profiles"];
const PRODUCTS_QUERY_KEY = ["products"];
const issuerFields: { key: keyof CompanyFiscalConfiguration; label: string }[] =
  [
    { key: "stateRegistration", label: "inscrição estadual" },
    { key: "taxRegime", label: "regime tributário" },
    { key: "street", label: "logradouro" },
    { key: "streetNumber", label: "número" },
    { key: "district", label: "bairro" },
    { key: "cityCode", label: "código IBGE do município" },
    { key: "city", label: "município" },
    { key: "postalCode", label: "CEP" },
  ];
const inputClass =
  "rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-primary";

// Format validation only. The validity of each tax classification must be
// checked against the accountant's source records before fiscal issuance.
const schema = z
  .object({
    name: z.string().trim().min(1, "Nome é obrigatório"),
    description: z.string().trim(),
    ncm: z
      .string()
      .trim()
      .regex(/^[0-9]{8}$/, "NCM: 8 dígitos")
      .or(z.literal("")),
    cfop: z
      .string()
      .trim()
      .regex(/^[0-9]{4}$/, "CFOP: 4 dígitos")
      .or(z.literal("")),
    cst: z
      .string()
      .trim()
      .regex(/^[0-9]{2,3}$/, "CST: 2 ou 3 dígitos")
      .or(z.literal("")),
    csosn: z
      .string()
      .trim()
      .regex(/^[0-9]{3}$/, "CSOSN: 3 dígitos")
      .or(z.literal("")),
    icmsOrigin: z
      .string()
      .trim()
      .regex(/^[0-8]$/, "Origem ICMS: 0 a 8")
      .or(z.literal("")),
    cest: z
      .string()
      .trim()
      .regex(/^[0-9]{7}$/, "CEST: 7 dígitos")
      .or(z.literal("")),
    pisCst: z.enum(["", "04", "06", "07", "08", "09", "99"]),
    cofinsCst: z.enum(["", "04", "06", "07", "08", "09", "99"]),
    active: z.boolean(),
  })
  .refine((value) => !value.cst || !value.csosn, {
    path: ["csosn"],
    message: "Escolha CST ou CSOSN, nunca ambos",
  });

type FormValues = z.infer<typeof schema>;

function formValues(profile?: FiscalProfile): FormValues {
  return {
    name: profile?.name ?? "",
    description: profile?.description ?? "",
    ncm: profile?.ncm ?? "",
    cfop: profile?.cfop ?? "",
    cst: profile?.cst ?? "",
    csosn: profile?.csosn ?? "",
    icmsOrigin: profile?.icmsOrigin ?? "",
    cest: profile?.cest ?? "",
    pisCst: (profile?.pisCst as FormValues["pisCst"]) ?? "",
    cofinsCst: (profile?.cofinsCst as FormValues["cofinsCst"]) ?? "",
    active: profile?.active ?? true,
  };
}

function toInput(values: FormValues): FiscalProfileInput {
  return {
    name: values.name,
    description: values.description || null,
    ncm: values.ncm || null,
    cfop: values.cfop || null,
    cst: values.cst || null,
    csosn: values.csosn || null,
    icmsOrigin: values.icmsOrigin || null,
    cest: values.cest || null,
    pisCst: values.pisCst || null,
    cofinsCst: values.cofinsCst || null,
  };
}

function FiscalProfileForm({
  profile,
  onSaved,
  onCancel,
}: {
  profile?: FiscalProfile;
  onSaved: () => void;
  onCancel?: () => void;
}) {
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: formValues(profile),
  });
  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      profile
        ? updateFiscalProfile(profile.id, {
            ...toInput(values),
            active: values.active,
          })
        : createFiscalProfile(toInput(values)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
      reset(formValues());
      onSaved();
    },
  });

  const fields = [
    { name: "ncm" as const, label: "NCM", hint: "8 dígitos" },
    { name: "cfop" as const, label: "CFOP", hint: "4 dígitos" },
    { name: "cst" as const, label: "CST", hint: "2 ou 3 dígitos" },
    { name: "csosn" as const, label: "CSOSN", hint: "3 dígitos" },
    { name: "icmsOrigin" as const, label: "Origem ICMS", hint: "0 a 8" },
    {
      name: "cest" as const,
      label: "CEST, quando aplicável",
      hint: "7 dígitos",
    },
  ];
  const pisCst = useWatch({ control, name: "pisCst" });
  const cofinsCst = useWatch({ control, name: "cofinsCst" });

  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
      className="flex flex-col gap-4"
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm font-medium">
          Nome do perfil
          <input className={inputClass} {...register("name")} />
          {errors.name && (
            <span className="text-red">{errors.name.message}</span>
          )}
        </label>
        <label className="flex flex-col gap-1 text-sm font-medium">
          Descrição
          <input className={inputClass} {...register("description")} />
        </label>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {fields.map((field) => (
          <label
            key={field.name}
            className="flex flex-col gap-1 text-sm font-medium"
          >
            {field.label}
            <input
              className={inputClass}
              inputMode="numeric"
              placeholder={field.hint}
              {...register(field.name)}
            />
            {errors[field.name] && (
              <span className="text-red">{errors[field.name]?.message}</span>
            )}
          </label>
        ))}
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        {(["pisCst", "cofinsCst"] as const).map((name) => (
          <label key={name} className="flex flex-col gap-1 text-sm font-medium">
            {name === "pisCst" ? "PIS CST" : "COFINS CST"}
            <select className={inputClass} {...register(name)}>
              <option value="">Não informado</option>
              {["04", "06", "07", "08", "09", "99"].map((code) => (
                <option key={code} value={code}>
                  {code}
                </option>
              ))}
            </select>
          </label>
        ))}
      </div>
      {(pisCst === "99" || cofinsCst === "99") && (
        <p role="alert" className="text-red text-sm">
          CST 99 exige base, alíquota e valor explícitos no XML. Este cadastro
          ainda não possui esses valores; confira antes de tentar emitir.
        </p>
      )}
      {profile && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" {...register("active")} />
          Perfil ativo
        </label>
      )}
      <p className="text-ink-soft text-xs">
        Códigos são opcionais. Um contador deve conferir classificação, operação
        e regime tributário antes da emissão fiscal.
      </p>
      {mutation.error && (
        <p role="alert" className="text-red text-sm">
          {apiErrorMessage(
            mutation.error,
            "Não foi possível salvar o perfil fiscal.",
          )}
        </p>
      )}
      <div className="flex gap-2">
        <Button type="submit" disabled={isSubmitting || mutation.isPending}>
          {mutation.isPending
            ? "Salvando..."
            : profile
              ? "Salvar perfil"
              : "Criar perfil"}
        </Button>
        {onCancel && (
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancelar
          </Button>
        )}
      </div>
    </form>
  );
}

export default function FiscalProfilesPage() {
  const [editingId, setEditingId] = useState<string | null>(null);
  const profilesQuery = useQuery({
    queryKey: QUERY_KEY,
    queryFn: listFiscalProfiles,
  });
  const productsQuery = useQuery({
    queryKey: PRODUCTS_QUERY_KEY,
    queryFn: listProducts,
  });
  const companyQuery = useQuery({
    queryKey: ["company-fiscal-configuration"],
    queryFn: getCompanyFiscalConfiguration,
  });
  const missingIssuerFields = companyQuery.data
    ? [
        ...issuerFields
          .filter(({ key }) => !companyQuery.data?.[key]?.trim())
          .map(({ label }) => label),
        ...(!/^[0-9]{14}$/.test(companyQuery.data.cnpj)
          ? ["CNPJ com 14 dígitos, sem pontuação"]
          : []),
        ...(companyQuery.data.stateRegistration &&
        !/^[0-9]{12}$/.test(companyQuery.data.stateRegistration)
          ? ["inscrição estadual paulista com 12 dígitos"]
          : []),
        ...(companyQuery.data.cityCode &&
        !/^35[0-9]{5}$/.test(companyQuery.data.cityCode)
          ? ["código IBGE paulista com 7 dígitos"]
          : []),
        ...(companyQuery.data.postalCode &&
        !/^[0-9]{8}$/.test(companyQuery.data.postalCode)
          ? ["CEP com 8 dígitos"]
          : []),
        ...(companyQuery.data.taxRegime &&
        !["1", "3"].includes(companyQuery.data.taxRegime)
          ? ["CRT suportado (1 ou 3)"]
          : []),
      ]
    : [];
  const activePosProducts = (productsQuery.data ?? []).filter(
    (product) => product.active && product.availableOnPos,
  );
  const profileById = new Map(
    (profilesQuery.data ?? []).map((profile) => [profile.id, profile]),
  );
  const fiscalIssues = (product: Product): string[] => {
    const issues: string[] = [];
    if (!product.gtin) issues.push("GTIN pendente");
    if (!product.fiscalUnit) issues.push("Unidade fiscal pendente");

    if (!product.fiscalProfileId) {
      issues.push("Sem perfil fiscal");
    } else {
      const profile = profileById.get(product.fiscalProfileId);
      if (!profile) issues.push("Perfil fiscal não encontrado");
      else if (!profile.active) issues.push("Perfil fiscal inativo");
      else {
        if (
          !profile.ncm ||
          !profile.cfop ||
          (!profile.cst && !profile.csosn) ||
          !profile.icmsOrigin ||
          !profile.pisCst ||
          !profile.cofinsCst
        )
          issues.push("Perfil com códigos pendentes");
        if (profile.pisCst === "99" || profile.cofinsCst === "99") {
          issues.push("CST 99: valores não cadastrados");
        }
      }
    }
    return issues;
  };
  const productsWithIssues = activePosProducts
    .map((product) => ({ product, issues: fiscalIssues(product) }))
    .filter(({ issues }) => issues.length > 0);

  return (
    <AuthGuard allow={["ADMIN"]}>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <div>
            <Link href="/admin/products" className="text-primary text-sm">
              ← Produtos
            </Link>
            <h1 className="mt-2 font-serif text-2xl font-semibold">
              Perfis fiscais
            </h1>
            <p className="text-ink-soft mt-1 text-sm">
              Cadastre classificações verificadas e depois associe cada produto
              ao perfil correto.
            </p>
          </div>
          <Card>
            <h2 className="font-semibold">
              Pendências para conferência fiscal
            </h2>
            <p className="text-ink-soft mt-1 text-sm">
              Este diagnóstico confere somente cadastro e preenchimento. Códigos
              presentes não significam classificação aprovada nem NFC-e pronta
              para emissão.
            </p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <div className="border-line rounded-lg border p-3">
                <p className="text-ink-soft text-xs">Dados do emitente</p>
                {companyQuery.isPending ? (
                  <p className="mt-1 text-sm">Consultando...</p>
                ) : companyQuery.isError ? (
                  <p className="text-red mt-1 text-sm">
                    Não foi possível consultar.
                  </p>
                ) : companyQuery.data ? (
                  <div className="mt-1 text-sm">
                    <p>
                      {companyQuery.data.legalName} · CNPJ{" "}
                      {companyQuery.data.cnpj}
                    </p>
                    {missingIssuerFields.length > 0 ? (
                      <p className="text-red mt-1">
                        Cadastro fiscal pendente: conferir{" "}
                        {missingIssuerFields.join(", ")}.
                      </p>
                    ) : (
                      <p className="text-ink-soft mt-1">
                        Campos do emitente preenchidos; conferir com os
                        documentos oficiais antes de emitir.
                      </p>
                    )}
                  </div>
                ) : (
                  <p className="text-red mt-1 text-sm">Cadastro ausente.</p>
                )}
                <Link
                  href="/admin/fiscal-company"
                  className="text-primary mt-2 inline-block text-sm font-semibold"
                >
                  Revisar dados da empresa →
                </Link>
              </div>
              <div className="border-line rounded-lg border p-3">
                <p className="text-ink-soft text-xs">Produtos ativos no PDV</p>
                {productsQuery.isPending || profilesQuery.isPending ? (
                  <p className="mt-1 text-sm">Consultando...</p>
                ) : productsQuery.isError || profilesQuery.isError ? (
                  <p className="text-red mt-1 text-sm">
                    Não foi possível consultar.
                  </p>
                ) : (
                  <p className="mt-1 text-sm">
                    {productsWithIssues.length} de {activePosProducts.length}{" "}
                    com pendência de cadastro fiscal.
                  </p>
                )}
              </div>
            </div>
            {!productsQuery.isPending &&
              !profilesQuery.isPending &&
              !productsQuery.isError &&
              !profilesQuery.isError &&
              productsWithIssues.length > 0 && (
                <div className="mt-4">
                  <h3 className="text-sm font-semibold">Produtos a revisar</h3>
                  <ul className="divide-line border-line mt-2 max-h-64 divide-y overflow-y-auto rounded-lg border">
                    {productsWithIssues.map(({ product, issues }) => (
                      <li
                        key={product.id}
                        className="flex flex-wrap justify-between gap-1 px-3 py-2 text-sm"
                      >
                        <span>{product.name}</span>
                        <span className="text-ink-soft">
                          {issues.join(" · ")}
                        </span>
                      </li>
                    ))}
                  </ul>
                  <Link
                    href="/admin/products"
                    className="text-primary mt-2 inline-block text-sm font-semibold"
                  >
                    Abrir produtos para revisar GTIN, unidade e perfil →
                  </Link>
                </div>
              )}
          </Card>
          <Card>
            <h2 className="mb-4 font-semibold">Novo perfil fiscal</h2>
            <FiscalProfileForm onSaved={() => undefined} />
          </Card>
          <div className="flex flex-col gap-3">
            {profilesQuery.isLoading && <p>Carregando perfis...</p>}
            {profilesQuery.isError && (
              <p className="text-red">
                Não foi possível carregar os perfis fiscais.
              </p>
            )}
            {profilesQuery.data?.length === 0 && (
              <p>Nenhum perfil fiscal cadastrado.</p>
            )}
            {profilesQuery.data?.map((profile) => (
              <Card key={profile.id}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="font-semibold">{profile.name}</h2>
                    {profile.description && (
                      <p className="text-ink-soft text-sm">
                        {profile.description}
                      </p>
                    )}
                    <p className="text-ink-soft mt-1 text-xs">
                      NCM {profile.ncm ?? "—"} · CFOP {profile.cfop ?? "—"} ·
                      CST {profile.cst ?? "—"} · CSOSN {profile.csosn ?? "—"}
                    </p>
                    <p className="text-ink-soft mt-1 text-xs">
                      Origem ICMS {profile.icmsOrigin ?? "—"} · CEST{" "}
                      {profile.cest ?? "—"} · PIS CST {profile.pisCst ?? "—"} ·
                      COFINS CST {profile.cofinsCst ?? "—"}
                    </p>
                  </div>
                  <Badge tone={profile.active ? "green" : "red"}>
                    {profile.active ? "Ativo" : "Inativo"}
                  </Badge>
                </div>
                {editingId === profile.id ? (
                  <div className="border-line mt-4 border-t pt-4">
                    <FiscalProfileForm
                      profile={profile}
                      onSaved={() => setEditingId(null)}
                      onCancel={() => setEditingId(null)}
                    />
                  </div>
                ) : (
                  <Button
                    type="button"
                    variant="outline"
                    className="mt-3"
                    onClick={() => setEditingId(profile.id)}
                  >
                    Editar
                  </Button>
                )}
              </Card>
            ))}
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}
