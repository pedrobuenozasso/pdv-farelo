"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";

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
    <main className="mx-auto flex max-w-2xl flex-col gap-8 p-8">
      <div>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">Admin</p>
        <h1 className="text-2xl font-semibold text-black dark:text-zinc-50">
          Categorias
        </h1>
      </div>

      <form
        onSubmit={onSubmit}
        noValidate
        className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800"
      >
        <label
          htmlFor="name"
          className="text-sm font-medium text-black dark:text-zinc-50"
        >
          Nome
        </label>
        <input
          id="name"
          type="text"
          placeholder="Ex: Bebidas"
          className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
          {...register("name")}
        />
        {errors.name ? (
          <p className="text-sm text-red-600 dark:text-red-400">
            {errors.name.message}
          </p>
        ) : null}
        {apiErrorMessage ? (
          <p className="text-sm text-red-600 dark:text-red-400">
            {apiErrorMessage}
          </p>
        ) : null}
        <button
          type="submit"
          disabled={isSubmitting || createCategoryMutation.isPending}
          className="self-start rounded bg-black px-4 py-2 text-sm font-medium text-white disabled:opacity-50 dark:bg-white dark:text-black"
        >
          {createCategoryMutation.isPending
            ? "Salvando..."
            : "Adicionar categoria"}
        </button>
      </form>

      <div className="flex flex-col gap-2">
        {categoriesQuery.isLoading ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Carregando...
          </p>
        ) : null}
        {categoriesQuery.isError ? (
          <p className="text-sm text-red-600 dark:text-red-400">
            Não foi possível carregar as categorias.
          </p>
        ) : null}
        {categoriesQuery.data && categoriesQuery.data.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Nenhuma categoria cadastrada.
          </p>
        ) : null}
        <ul className="flex flex-col divide-y divide-zinc-200 dark:divide-zinc-800">
          {categoriesQuery.data?.map((category: Category) => (
            <li
              key={category.id}
              className="flex items-center justify-between py-2"
            >
              <span className="text-sm text-black dark:text-zinc-50">
                {category.name}
              </span>
              <span
                className={
                  category.active
                    ? "text-xs text-green-600 dark:text-green-400"
                    : "text-xs text-zinc-400"
                }
              >
                {category.active ? "Ativa" : "Inativa"}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}
