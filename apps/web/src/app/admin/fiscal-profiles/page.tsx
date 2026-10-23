"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/client";
import { getCompanyFiscalConfiguration } from "@/lib/api/company-fiscal-configuration";
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
  ];

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
  const activePosProducts = (productsQuery.data ?? []).filter(
    (product) => product.active && product.availableOnPos,
  );
  const profileById = new Map(
    (profilesQuery.data ?? []).map((profile) => [profile.id, profile]),
  );
  const fiscalIssue = (product: Product): string | null => {
    if (!product.fiscalProfileId) return "Sem perfil fiscal";
    const profile = profileById.get(product.fiscalProfileId);
    if (!profile) return "Perfil fiscal não encontrado";
    if (!profile.active) return "Perfil fiscal inativo";
    if (!profile.ncm || !profile.cfop || (!profile.cst && !profile.csosn)) {
      return "Perfil com códigos pendentes";
    }
    return null;
  };
  const productsWithIssues = activePosProducts
    .map((product) => ({ product, issue: fiscalIssue(product) }))
    .filter((entry): entry is { product: Product; issue: string } =>
      Boolean(entry.issue),
    );

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
                  <p className="mt-1 text-sm">
                    Cadastrado: {companyQuery.data.legalName} · CNPJ{" "}
                    {companyQuery.data.cnpj}. Conferir dados completos antes de
                    emitir.
                  </p>
                ) : (
                  <p className="text-red mt-1 text-sm">Cadastro ausente.</p>
                )}
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
                    {productsWithIssues.map(({ product, issue }) => (
                      <li
                        key={product.id}
                        className="flex justify-between gap-3 px-3 py-2 text-sm"
                      >
                        <span>{product.name}</span>
                        <span className="text-ink-soft shrink-0">{issue}</span>
                      </li>
                    ))}
                  </ul>
                  <Link
                    href="/admin/products"
                    className="text-primary mt-2 inline-block text-sm font-semibold"
                  >
                    Abrir produtos para associar perfis →
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
