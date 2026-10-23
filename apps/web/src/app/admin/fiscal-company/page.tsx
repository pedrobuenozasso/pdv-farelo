"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/client";
import {
  getCompanyFiscalConfiguration,
  putCompanyFiscalConfiguration,
  type CompanyFiscalConfiguration,
  type CompanyFiscalConfigurationInput,
} from "@/lib/api/company-fiscal-configuration";

const QUERY_KEY = ["company-fiscal-configuration"];
const inputClass =
  "rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-primary";

const optionalText = z.string().trim();
const schema = z.object({
  cnpj: z.string().trim().min(1, "CNPJ é obrigatório"),
  legalName: z.string().trim().min(1, "Razão social é obrigatória"),
  tradeName: optionalText,
  stateRegistration: optionalText,
  address: optionalText,
  taxRegime: z.enum(["", "1", "3"]),
  street: optionalText.max(60, "Máximo de 60 caracteres"),
  streetNumber: optionalText.max(60, "Máximo de 60 caracteres"),
  district: optionalText.max(60, "Máximo de 60 caracteres"),
  cityCode: optionalText.regex(/^$|^[0-9]{7}$/, "Código IBGE: 7 dígitos"),
  city: optionalText.max(60, "Máximo de 60 caracteres"),
  postalCode: optionalText.regex(
    /^$|^[0-9]{8}$/,
    "CEP: 8 dígitos sem pontuação",
  ),
});
type FormValues = z.infer<typeof schema>;

function initialValues(data: CompanyFiscalConfiguration | null): FormValues {
  return {
    cnpj: data?.cnpj ?? "",
    legalName: data?.legalName ?? "",
    tradeName: data?.tradeName ?? "",
    stateRegistration: data?.stateRegistration ?? "",
    address: data?.address ?? "",
    taxRegime: (data?.taxRegime as "1" | "3" | null | undefined) ?? "",
    street: data?.street ?? "",
    streetNumber: data?.streetNumber ?? "",
    district: data?.district ?? "",
    cityCode: data?.cityCode ?? "",
    city: data?.city ?? "",
    postalCode: data?.postalCode ?? "",
  };
}

function toInput(values: FormValues): CompanyFiscalConfigurationInput {
  return {
    cnpj: values.cnpj,
    legalName: values.legalName,
    tradeName: values.tradeName || null,
    stateRegistration: values.stateRegistration || null,
    address: values.address || null,
    taxRegime: values.taxRegime || null,
    street: values.street || null,
    streetNumber: values.streetNumber || null,
    district: values.district || null,
    cityCode: values.cityCode || null,
    city: values.city || null,
    postalCode: values.postalCode || null,
  };
}

const fields: { name: keyof FormValues; label: string; hint?: string }[] = [
  { name: "cnpj", label: "CNPJ" },
  { name: "legalName", label: "Razão social" },
  { name: "tradeName", label: "Nome fantasia" },
  { name: "stateRegistration", label: "Inscrição estadual" },
  { name: "street", label: "Logradouro" },
  { name: "streetNumber", label: "Número" },
  { name: "district", label: "Bairro" },
  { name: "cityCode", label: "Código IBGE do município", hint: "7 dígitos" },
  { name: "city", label: "Município" },
  { name: "postalCode", label: "CEP", hint: "8 dígitos, sem pontuação" },
];

function IssuerForm({ data }: { data: CompanyFiscalConfiguration | null }) {
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: initialValues(data),
  });
  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      putCompanyFiscalConfiguration(toInput(values)),
    onSuccess: (saved) => queryClient.setQueryData(QUERY_KEY, saved),
  });

  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
    >
      <div className="grid gap-4 sm:grid-cols-2">
        {fields.map((field) => (
          <label
            key={field.name}
            className="flex flex-col gap-1 text-sm font-medium"
          >
            {field.label}
            <input
              className={inputClass}
              placeholder={field.hint}
              inputMode={
                ["cityCode", "postalCode"].includes(field.name)
                  ? "numeric"
                  : "text"
              }
              {...register(field.name)}
            />
            {errors[field.name] && (
              <span className="text-red text-xs">
                {errors[field.name]?.message}
              </span>
            )}
          </label>
        ))}
        <label className="flex flex-col gap-1 text-sm font-medium">
          Regime tributário (CRT)
          <select className={inputClass} {...register("taxRegime")}>
            <option value="">Selecione após conferir</option>
            <option value="1">1 — Simples Nacional</option>
            <option value="3">3 — Regime Normal</option>
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm font-medium sm:col-span-2">
          Endereço em texto livre (cadastro anterior)
          <input className={inputClass} {...register("address")} />
        </label>
      </div>
      <p className="text-ink-soft mt-4 text-xs">
        O endereço em texto livre não substitui os campos estruturados. Salvar
        substitui todos os dados do emitente; campos vazios serão removidos.
      </p>
      {mutation.error && (
        <p role="alert" className="text-red mt-3 text-sm">
          {apiErrorMessage(
            mutation.error,
            "Não foi possível salvar os dados fiscais.",
          )}
        </p>
      )}
      {mutation.isSuccess && (
        <p role="status" className="mt-3 text-sm">
          Dados salvos. Ainda precisam de conferência fiscal.
        </p>
      )}
      <Button type="submit" className="mt-4" disabled={mutation.isPending}>
        {mutation.isPending ? "Salvando..." : "Salvar dados do emitente"}
      </Button>
    </form>
  );
}

export default function FiscalCompanyPage() {
  const companyQuery = useQuery({
    queryKey: QUERY_KEY,
    queryFn: getCompanyFiscalConfiguration,
  });

  return (
    <AuthGuard allow={["ADMIN"]}>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <div>
            <Link
              href="/admin/fiscal-profiles"
              className="text-primary text-sm"
            >
              ← Perfis fiscais
            </Link>
            <h1 className="mt-2 font-serif text-2xl font-semibold">
              Dados fiscais da empresa
            </h1>
            <p className="text-ink-soft mt-1 text-sm">
              Informe os dados do emitente a partir dos documentos oficiais.
              Este cadastro, mesmo completo, não libera a emissão de NFC-e.
            </p>
          </div>
          <Card>
            {companyQuery.isPending ? (
              <p>Carregando cadastro...</p>
            ) : companyQuery.isError ? (
              <p role="alert" className="text-red">
                Não foi possível carregar o cadastro. Nenhum campo será
                substituído.
              </p>
            ) : (
              <IssuerForm
                key={companyQuery.data?.id ?? "new"}
                data={companyQuery.data}
              />
            )}
          </Card>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}
