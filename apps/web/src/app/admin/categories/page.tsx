"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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

const CATEGORIES_QUERY_KEY = ["categories"];

const categoryFormSchema = z.object({
  name: z.string().trim().min(1, "Nome é obrigatório"),
});

type CategoryFormValues = z.infer<typeof categoryFormSchema>;

export default function CategoriesAdminPage() {
  const queryClient = useQueryClient();

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
    defaultValues: { name: "" },
  });

  const createCategoryMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CATEGORIES_QUERY_KEY });
      reset();
    },
  });

  const onSubmit = handleSubmit((values) => {
    createCategoryMutation.mutate(values);
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
                <li
                  key={category.id}
                  className="flex items-center justify-between px-5 py-3.5"
                >
                  <span className="text-sm font-semibold">{category.name}</span>
                  <Badge tone={category.active ? "green" : "neutral"}>
                    {category.active ? "Ativa" : "Inativa"}
                  </Badge>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}
