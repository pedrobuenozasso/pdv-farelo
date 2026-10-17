"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/client";
import {
  listProductSteps,
  replaceProductSteps,
  type ProductStep,
  type ProductStepInput,
} from "@/lib/api/product-steps";
import { listProducts, type Product } from "@/lib/api/products";

type DraftOption = {
  key: string;
  optionProductId: string;
  displayOrder: string;
  price: string;
};

type DraftStep = {
  key: string;
  label: string;
  minChoices: string;
  maxChoices: string;
  displayOrder: string;
  level: string;
  chargeType: string;
  options: DraftOption[];
};

const inputClass =
  "w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-primary";
const money = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

function toDraft(steps: ProductStep[]): DraftStep[] {
  return steps.map((step) => ({
    key: step.id,
    label: step.label,
    minChoices: String(step.minChoices),
    maxChoices: String(step.maxChoices),
    displayOrder: String(step.displayOrder),
    level: step.level,
    chargeType: step.chargeType,
    options: step.options.map((option) => ({
      key: option.id,
      optionProductId: option.optionProductId,
      displayOrder: String(option.displayOrder),
      price: String(option.price),
    })),
  }));
}

function nonNegativeInteger(value: string): boolean {
  return /^(0|[1-9]\d*)$/.test(value) && Number.isSafeInteger(Number(value));
}

function validateDraft(steps: DraftStep[], products: Product[]): string | null {
  const ids = new Set(products.map((product) => product.id));
  const stepOrders = new Set<string>();
  for (const [stepIndex, step] of steps.entries()) {
    const label = `Passo ${stepIndex + 1}`;
    if (!step.label.trim() || step.label.length > 120)
      return `${label}: informe um nome de até 120 caracteres.`;
    if (!step.level.trim() || step.level.length > 80)
      return `${label}: informe o nível (até 80 caracteres).`;
    if (!step.chargeType.trim() || step.chargeType.length > 40)
      return `${label}: informe o tipo de cobrança (até 40 caracteres).`;
    if (
      ![step.minChoices, step.maxChoices, step.displayOrder].every(
        nonNegativeInteger,
      )
    ) {
      return `${label}: mínimo, máximo e ordem devem ser inteiros não negativos.`;
    }
    if (Number(step.minChoices) > Number(step.maxChoices))
      return `${label}: o mínimo não pode superar o máximo.`;
    if (stepOrders.has(step.displayOrder))
      return `A ordem ${step.displayOrder} está repetida entre passos.`;
    stepOrders.add(step.displayOrder);
    const optionOrders = new Set<string>();
    const optionIds = new Set<string>();
    for (const [optionIndex, option] of step.options.entries()) {
      const name = `${label}, opção ${optionIndex + 1}`;
      if (!ids.has(option.optionProductId))
        return `${name}: selecione um produto existente.`;
      if (!nonNegativeInteger(option.displayOrder))
        return `${name}: a ordem deve ser um inteiro não negativo.`;
      if (optionOrders.has(option.displayOrder))
        return `${label}: há opções com a mesma ordem.`;
      if (optionIds.has(option.optionProductId))
        return `${label}: o mesmo produto foi selecionado duas vezes.`;
      if (!/^\d{1,8}(\.\d{1,2})?$/.test(option.price)) {
        return `${name}: informe um preço não negativo com até duas casas decimais (ex.: 16.00).`;
      }
      optionOrders.add(option.displayOrder);
      optionIds.add(option.optionProductId);
    }
  }
  return null;
}

function toInput(steps: DraftStep[]): ProductStepInput[] {
  return steps.map((step) => ({
    label: step.label.trim(),
    minChoices: Number(step.minChoices),
    maxChoices: Number(step.maxChoices),
    displayOrder: Number(step.displayOrder),
    level: step.level.trim(),
    chargeType: step.chargeType.trim(),
    options: step.options.map((option) => ({
      optionProductId: option.optionProductId,
      displayOrder: Number(option.displayOrder),
      price: option.price,
    })),
  }));
}

export default function ProductStepsPage() {
  const { productId } = useParams<{ productId: string }>();
  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: listProducts,
  });
  const stepsQuery = useQuery({
    queryKey: ["product-steps", productId],
    queryFn: () => listProductSteps(productId),
  });
  const product = productsQuery.data?.find((item) => item.id === productId);

  return (
    <AuthGuard allow={["ADMIN", "MANAGER"]}>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-5">
          <Link
            href="/admin/products"
            className="text-primary text-sm font-semibold"
          >
            ← Voltar aos produtos
          </Link>
          <div>
            <h1 className="font-serif text-2xl font-semibold">
              Passos e opções
            </h1>
            <p className="text-ink-soft mt-1 text-sm">
              {product?.name ?? "Produto"} · preço simples{" "}
              {product ? money.format(product.price) : "—"}
            </p>
          </div>
          {productsQuery.isPending || stepsQuery.isPending ? (
            <p className="text-ink-soft text-sm">Carregando configuração...</p>
          ) : null}
          {productsQuery.isError || stepsQuery.isError ? (
            <p role="alert" className="text-red text-sm">
              {apiErrorMessage(
                stepsQuery.error ?? productsQuery.error,
                "Não foi possível carregar a configuração.",
              )}
            </p>
          ) : null}
          {productsQuery.data && !product ? (
            <p className="text-red text-sm">Produto não encontrado.</p>
          ) : null}
          {product && stepsQuery.data ? (
            <StepsEditor
              key={productId}
              product={product}
              products={productsQuery.data ?? []}
              initialSteps={stepsQuery.data}
            />
          ) : null}
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

function StepsEditor({
  product,
  products,
  initialSteps,
}: {
  product: Product;
  products: Product[];
  initialSteps: ProductStep[];
}) {
  const [draft, setDraft] = useState<DraftStep[]>(() => toDraft(initialSteps));
  const [validationError, setValidationError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const mutation = useMutation({
    mutationFn: (steps: ProductStepInput[]) =>
      replaceProductSteps(product.id, steps),
    onSuccess: (steps) => {
      setDraft(toDraft(steps));
      setValidationError(null);
      setSaved(true);
    },
  });

  function updateStep(index: number, patch: Partial<DraftStep>) {
    setDraft((current) =>
      current.map((step, i) => (i === index ? { ...step, ...patch } : step)),
    );
    setSaved(false);
  }

  function updateOption(
    stepIndex: number,
    optionIndex: number,
    patch: Partial<DraftOption>,
  ) {
    setDraft((current) =>
      current.map((step, i) =>
        i === stepIndex
          ? {
              ...step,
              options: step.options.map((option, j) =>
                j === optionIndex ? { ...option, ...patch } : option,
              ),
            }
          : step,
      ),
    );
    setSaved(false);
  }

  function save() {
    const error = validateDraft(draft, products);
    setValidationError(error);
    setSaved(false);
    if (!error) mutation.mutate(toInput(draft));
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-ink-soft text-sm">
        O preço do vínculo pertence a esta escolha e pode diferir do preço
        simples do produto. Esta tela edita o cadastro; o cálculo do pedido
        ainda depende de uma etapa posterior.
      </p>
      {draft.map((step, stepIndex) => (
        <Card key={step.key}>
          <div className="mb-4 flex items-start justify-between gap-3">
            <h2 className="font-serif text-lg font-semibold">
              Passo {stepIndex + 1}
            </h2>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setDraft((current) =>
                  current.filter((_, i) => i !== stepIndex),
                );
                setSaved(false);
              }}
            >
              Remover passo
            </Button>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Nome do passo">
              <input
                className={inputClass}
                value={step.label}
                maxLength={120}
                onChange={(event) =>
                  updateStep(stepIndex, { label: event.target.value })
                }
              />
            </Field>
            <Field label="Ordem">
              <input
                className={inputClass}
                type="number"
                min="0"
                step="1"
                value={step.displayOrder}
                onChange={(event) =>
                  updateStep(stepIndex, { displayOrder: event.target.value })
                }
              />
            </Field>
            <Field label="Mínimo de escolhas">
              <input
                className={inputClass}
                type="number"
                min="0"
                step="1"
                value={step.minChoices}
                onChange={(event) =>
                  updateStep(stepIndex, { minChoices: event.target.value })
                }
              />
            </Field>
            <Field label="Máximo de escolhas">
              <input
                className={inputClass}
                type="number"
                min="0"
                step="1"
                value={step.maxChoices}
                onChange={(event) =>
                  updateStep(stepIndex, { maxChoices: event.target.value })
                }
              />
            </Field>
            <Field label="Nível">
              <input
                className={inputClass}
                value={step.level}
                maxLength={80}
                onChange={(event) =>
                  updateStep(stepIndex, { level: event.target.value })
                }
              />
            </Field>
            <Field label="Tipo de cobrança">
              <input
                className={inputClass}
                value={step.chargeType}
                maxLength={40}
                onChange={(event) =>
                  updateStep(stepIndex, { chargeType: event.target.value })
                }
              />
            </Field>
          </div>
          <div className="mt-5 flex items-center justify-between gap-3">
            <h3 className="font-semibold">Opções ({step.options.length})</h3>
            <Button
              type="button"
              variant="outline"
              onClick={() =>
                updateStep(stepIndex, {
                  options: [
                    ...step.options,
                    {
                      key: crypto.randomUUID(),
                      optionProductId: "",
                      displayOrder: String(step.options.length),
                      price: "0.00",
                    },
                  ],
                })
              }
            >
              Adicionar opção
            </Button>
          </div>
          <div className="mt-3 flex flex-col gap-3">
            {step.options.map((option, optionIndex) => {
              const linked = products.find(
                (item) => item.id === option.optionProductId,
              );
              const simplePrice = linked?.price;
              return (
                <div
                  key={option.key}
                  className="border-line rounded-xl border p-3"
                >
                  <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_6rem_8rem_auto] sm:items-end">
                    <Field label={`Produto da opção ${optionIndex + 1}`}>
                      <select
                        className={inputClass}
                        value={option.optionProductId}
                        onChange={(event) =>
                          updateOption(stepIndex, optionIndex, {
                            optionProductId: event.target.value,
                          })
                        }
                      >
                        <option value="">Selecione um produto</option>
                        {products.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name}
                            {item.active ? "" : " (inativo)"}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Ordem">
                      <input
                        className={inputClass}
                        type="number"
                        min="0"
                        step="1"
                        value={option.displayOrder}
                        onChange={(event) =>
                          updateOption(stepIndex, optionIndex, {
                            displayOrder: event.target.value,
                          })
                        }
                      />
                    </Field>
                    <Field label="Preço do vínculo">
                      <input
                        className={inputClass}
                        inputMode="decimal"
                        value={option.price}
                        onChange={(event) =>
                          updateOption(stepIndex, optionIndex, {
                            price: event.target.value,
                          })
                        }
                      />
                    </Field>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() =>
                        updateStep(stepIndex, {
                          options: step.options.filter(
                            (_, i) => i !== optionIndex,
                          ),
                        })
                      }
                    >
                      Remover
                    </Button>
                  </div>
                  {linked ? (
                    <div className="text-ink-soft mt-2 text-xs">
                      Vínculo:{" "}
                      {/^\d+(\.\d{1,2})?$/.test(option.price)
                        ? money.format(Number(option.price))
                        : "preço inválido"}{" "}
                      · Preço simples: {money.format(simplePrice ?? 0)}
                      {/^\d+(\.\d{1,2})?$/.test(option.price) &&
                      money.format(Number(option.price)) !==
                        money.format(simplePrice ?? 0)
                        ? " · preços diferentes"
                        : ""}
                    </div>
                  ) : null}
                  {linked && !linked.active ? (
                    <p className="text-red mt-2 text-sm font-semibold">
                      Atenção: esta opção está inativa no cadastro de produtos.
                    </p>
                  ) : null}
                </div>
              );
            })}
          </div>
        </Card>
      ))}
      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setDraft((current) => [
              ...current,
              {
                key: crypto.randomUUID(),
                label: "",
                minChoices: "0",
                maxChoices: "1",
                displayOrder: String(current.length),
                level: "",
                chargeType: "",
                options: [],
              },
            ]);
            setSaved(false);
          }}
        >
          Adicionar passo
        </Button>
        <Button type="button" onClick={save} disabled={mutation.isPending}>
          {mutation.isPending ? "Salvando..." : "Salvar configuração"}
        </Button>
        {saved ? (
          <span role="status" className="text-primary text-sm">
            Configuração salva.
          </span>
        ) : null}
      </div>
      {validationError ? (
        <p role="alert" className="text-red text-sm">
          {validationError}
        </p>
      ) : null}
      {mutation.isError ? (
        <p role="alert" className="text-red text-sm">
          {apiErrorMessage(
            mutation.error,
            "Não foi possível salvar a configuração.",
          )}
        </p>
      ) : null}
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm font-medium">
      {label}
      {children}
    </label>
  );
}
