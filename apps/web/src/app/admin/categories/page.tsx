"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  ApiError,
  createCategory,
  listCategories,
  type Category,
} from "@/lib/api/categories";
import { listProducts, type Product } from "@/lib/api/products";

const CATEGORIES_QUERY_KEY = ["categories"];

const categoryFormSchema = z.object({
  name: z.string().trim().min(1, "Nome é obrigatório"),
  description: z.string().trim().optional(),
  sortOrder: z
    .string()
    .trim()
    .optional()
    .refine(
      (value) => !value || !Number.isNaN(Number(value)),
      "Ordem deve ser um número",
    ),
});

type CategoryFormValues = z.infer<typeof categoryFormSchema>;

export default function CategoriesAdminPage() {
  const queryClient = useQueryClient();
  const [expandedCategoryId, setExpandedCategoryId] = useState<string | null>(
    null,
  );

  const categoriesQuery = useQuery({
    queryKey: CATEGORIES_QUERY_KEY,
    queryFn: listCategories,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CategoryFormValues>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: { name: "", description: "", sortOrder: "" },
  });

  const createCategoryMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CATEGORIES_QUERY_KEY });
      reset();
    },
  });

  const onSubmit = handleSubmit((values) => {
    createCategoryMutation.mutate({
      name: values.name,
      description: values.description?.trim() || undefined,
      sortOrder: values.sortOrder ? Number(values.sortOrder) : undefined,
    });
  });

  const apiErrorMessage =
    createCategoryMutation.error instanceof ApiError
      ? createCategoryMutation.error.message
      : createCategoryMutation.error
        ? "Não foi possível criar a categoria."
        : null;

  return (
    <AuthGuard>
      <AdminShell>
        <div className="mx-auto flex max-w-2xl flex-col gap-6">
          <div>
            <h1 className="font-serif text-2xl font-semibold">Categorias</h1>
            <p className="text-ink-soft mt-0.5 text-sm">
              {categoriesQuery.data?.length ?? 0} categorias cadastradas
            </p>
          </div>

          <Card>
            <form
              onSubmit={onSubmit}
              noValidate
              className="flex flex-col gap-3"
            >
              <div className="flex flex-col gap-1">
                <label htmlFor="name" className="text-sm font-medium">
                  Nome
                </label>
                <input
                  id="name"
                  type="text"
                  placeholder="Ex: Bebidas"
                  className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
                  {...register("name")}
                />
                {errors.name ? (
                  <p className="text-red text-sm">{errors.name.message}</p>
                ) : null}
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor="description" className="text-sm font-medium">
                  Descrição · opcional
                </label>
                <input
                  id="description"
                  type="text"
                  placeholder="Ex: Cafés, chás e sucos"
                  className="border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none"
                  {...register("description")}
                />
              </div>

              <div className="flex flex-col gap-1">
                <label htmlFor="sortOrder" className="text-sm font-medium">
                  Ordem de exibição · opcional
                </label>
                <input
                  id="sortOrder"
                  inputMode="numeric"
                  placeholder="0"
                  className="border-line bg-surface focus:border-primary w-32 rounded-lg border px-3 py-2 text-sm outline-none"
                  {...register("sortOrder")}
                />
                {errors.sortOrder ? (
                  <p className="text-red text-sm">{errors.sortOrder.message}</p>
                ) : null}
              </div>

              {apiErrorMessage ? (
                <p className="text-red text-sm">{apiErrorMessage}</p>
              ) : null}
              <Button
                type="submit"
                disabled={isSubmitting || createCategoryMutation.isPending}
                className="self-start"
              >
                {createCategoryMutation.isPending
                  ? "Salvando..."
                  : "Adicionar categoria"}
              </Button>
            </form>
          </Card>

          <div className="border-line bg-surface overflow-hidden rounded-2xl border">
            {categoriesQuery.isLoading ? (
              <p className="text-ink-faint p-5 text-sm">Carregando...</p>
            ) : null}
            {categoriesQuery.isError ? (
              <p className="text-red p-5 text-sm">
                Não foi possível carregar as categorias.
              </p>
            ) : null}
            {categoriesQuery.data && categoriesQuery.data.length === 0 ? (
              <p className="text-ink-faint p-5 text-sm">
                Nenhuma categoria cadastrada.
              </p>
            ) : null}
            <ul className="divide-line flex flex-col divide-y">
              {categoriesQuery.data?.map((category: Category) => (
                <li key={category.id}>
                  <button
                    type="button"
                    onClick={() =>
                      setExpandedCategoryId((current) =>
                        current === category.id ? null : category.id,
                      )
                    }
                    className="hover:bg-bg-alt flex w-full items-center justify-between px-5 py-3.5 text-left"
                  >
                    <div>
                      <span className="text-sm font-semibold">
                        {category.name}
                      </span>
                      {category.description ? (
                        <p className="text-ink-faint text-xs">
                          {category.description}
                        </p>
                      ) : null}
                    </div>
                    <Badge tone={category.active ? "green" : "neutral"}>
                      {category.active ? "Ativa" : "Inativa"}
                    </Badge>
                  </button>
                  {expandedCategoryId === category.id ? (
                    <CategoryProducts categoryId={category.id} />
                  ) : null}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

// FARELO-263 ("Visualizar produtos da categoria"). No new backend
// endpoint — GET /api/v1/products already returns every product with its
// categoryId, so filtering client-side avoids a redundant, near-identical
// GET /api/v1/categories/{id}/products (same reasoning app/c/[commandNumber]
// /page.tsx's loadMenu already uses for the analogous active/availableOnMenu
// filtering).
function CategoryProducts({ categoryId }: { categoryId: string }) {
  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: listProducts,
  });

  const products = (productsQuery.data ?? []).filter(
    (product: Product) => product.categoryId === categoryId,
  );

  return (
    <div className="bg-bg border-line border-t px-5 py-3">
      {productsQuery.isLoading ? (
        <p className="text-ink-faint text-xs">Carregando produtos...</p>
      ) : null}
      {productsQuery.data && products.length === 0 ? (
        <p className="text-ink-faint text-xs">
          Nenhum produto nesta categoria.
        </p>
      ) : null}
      <ul className="flex flex-col gap-1">
        {products.map((product) => (
          <li
            key={product.id}
            className="flex items-center justify-between text-sm"
          >
            <span className={product.active ? "" : "text-ink-faint"}>
              {product.name}
            </span>
            <span className="text-ink-soft text-xs">
              {new Intl.NumberFormat("pt-BR", {
                style: "currency",
                currency: "BRL",
              }).format(product.price)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
