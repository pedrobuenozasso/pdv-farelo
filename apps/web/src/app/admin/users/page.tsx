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
import { Switch } from "@/components/ui/switch";
import { apiErrorMessage } from "@/lib/api/client";
import {
  createUser,
  listUsers,
  updateUser,
  updateUserPassword,
  type User,
  type UserRole,
} from "@/lib/api/users";

const USERS_QUERY_KEY = ["users"];

const ROLE_LABEL: Record<UserRole, string> = {
  ADMIN: "Admin",
  MANAGER: "Gerente",
  CASHIER: "Caixa",
  KITCHEN: "Cozinha",
  ATTENDANT: "Atendente",
};

const inputClass =
  "border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none";

const userFormSchema = z.object({
  name: z.string().trim().min(1, "Nome é obrigatório"),
  email: z
    .string()
    .trim()
    .min(1, "Email é obrigatório")
    .email("Email inválido"),
  role: z.enum(["ADMIN", "MANAGER", "CASHIER", "KITCHEN", "ATTENDANT"]),
});

const createUserFormSchema = userFormSchema.extend({
  password: z
    .string()
    .min(8, "Mínimo 8 caracteres")
    .max(72, "Máximo 72 caracteres"),
});

type CreateUserFormValues = z.infer<typeof createUserFormSchema>;
type EditUserFormValues = z.infer<typeof userFormSchema> & { active: boolean };

export default function UsersAdminPage() {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [passwordId, setPasswordId] = useState<string | null>(null);

  const usersQuery = useQuery({
    queryKey: USERS_QUERY_KEY,
    queryFn: listUsers,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateUserFormValues>({
    resolver: zodResolver(createUserFormSchema),
    defaultValues: { name: "", email: "", password: "", role: "ATTENDANT" },
  });

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY });
      reset();
      setCreateOpen(false);
    },
  });

  const createErrorMsg = apiErrorMessage(
    createMutation.error,
    "Não foi possível criar o usuário.",
  );

  return (
    <AuthGuard>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="font-serif text-2xl font-semibold">Usuários</h1>
              <p className="text-ink-soft mt-0.5 text-sm">
                {usersQuery.data?.length ?? 0} contas da equipe
              </p>
            </div>
            <Button onClick={() => setCreateOpen((open) => !open)}>
              {createOpen ? "Fechar" : "Novo usuário"}
            </Button>
          </div>

          {createOpen ? (
            <Card>
              <form
                onSubmit={handleSubmit((values) =>
                  createMutation.mutate(values),
                )}
                noValidate
                className="flex flex-wrap items-end gap-3"
              >
                <div className="flex min-w-[160px] flex-1 flex-col gap-1">
                  <label className="text-sm font-medium">Nome</label>
                  <input className={inputClass} {...register("name")} />
                  {errors.name ? (
                    <p className="text-red text-sm">{errors.name.message}</p>
                  ) : null}
                </div>
                <div className="flex min-w-[200px] flex-1 flex-col gap-1">
                  <label className="text-sm font-medium">Email</label>
                  <input
                    type="email"
                    className={inputClass}
                    {...register("email")}
                  />
                  {errors.email ? (
                    <p className="text-red text-sm">{errors.email.message}</p>
                  ) : null}
                </div>
                <div className="flex min-w-[160px] flex-col gap-1">
                  <label className="text-sm font-medium">Senha</label>
                  <input
                    type="password"
                    className={inputClass}
                    {...register("password")}
                  />
                  {errors.password ? (
                    <p className="text-red text-sm">
                      {errors.password.message}
                    </p>
                  ) : null}
                </div>
                <div className="flex w-36 flex-col gap-1">
                  <label className="text-sm font-medium">Papel</label>
                  <select className={inputClass} {...register("role")}>
                    {Object.entries(ROLE_LABEL).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </div>
                {createErrorMsg ? (
                  <p className="text-red w-full text-sm">{createErrorMsg}</p>
                ) : null}
                <Button
                  type="submit"
                  disabled={isSubmitting || createMutation.isPending}
                >
                  {createMutation.isPending ? "Salvando..." : "Adicionar"}
                </Button>
              </form>
            </Card>
          ) : null}

          <div className="border-line bg-surface overflow-hidden rounded-2xl border">
            {usersQuery.isLoading ? (
              <p className="text-ink-faint p-5 text-sm">Carregando...</p>
            ) : null}
            {usersQuery.isError ? (
              <p className="text-red p-5 text-sm">
                Não foi possível carregar os usuários.
              </p>
            ) : null}
            {usersQuery.data?.map((user) => (
              <UserRow
                key={user.id}
                user={user}
                editing={editingId === user.id}
                passwordOpen={passwordId === user.id}
                onToggleEdit={() =>
                  setEditingId((current) =>
                    current === user.id ? null : user.id,
                  )
                }
                onTogglePassword={() =>
                  setPasswordId((current) =>
                    current === user.id ? null : user.id,
                  )
                }
                onEditSaved={() => setEditingId(null)}
                onPasswordSaved={() => setPasswordId(null)}
              />
            ))}
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

function UserRow({
  user,
  editing,
  passwordOpen,
  onToggleEdit,
  onTogglePassword,
  onEditSaved,
  onPasswordSaved,
}: {
  user: User;
  editing: boolean;
  passwordOpen: boolean;
  onToggleEdit: () => void;
  onTogglePassword: () => void;
  onEditSaved: () => void;
  onPasswordSaved: () => void;
}) {
  return (
    <div className="border-line border-t first:border-t-0">
      <div className="flex items-center gap-4 px-5 py-3.5">
        <div className="flex-1">
          <div className="text-sm font-semibold">{user.name}</div>
          <div className="text-ink-faint text-xs">{user.email}</div>
        </div>
        <span className="text-ink-soft w-24 text-sm">
          {ROLE_LABEL[user.role]}
        </span>
        <div className="w-20">
          <Badge tone={user.active ? "green" : "red"}>
            {user.active ? "Ativo" : "Inativo"}
          </Badge>
        </div>
        <button
          type="button"
          onClick={onTogglePassword}
          className="text-ink-faint hover:text-primary"
          aria-label="Alterar senha"
        >
          <svg
            width="17"
            height="17"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <rect x="3" y="11" width="18" height="11" rx="2" />
            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
          </svg>
        </button>
        <button
          type="button"
          onClick={onToggleEdit}
          className="text-ink-faint hover:text-primary"
          aria-label="Editar"
        >
          <svg
            width="17"
            height="17"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M12 20h9" />
            <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
          </svg>
        </button>
      </div>

      {editing ? <UserEditForm user={user} onSaved={onEditSaved} /> : null}
      {passwordOpen ? (
        <UserPasswordForm user={user} onSaved={onPasswordSaved} />
      ) : null}
    </div>
  );
}

function UserEditForm({ user, onSaved }: { user: User; onSaved: () => void }) {
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<EditUserFormValues>({
    resolver: zodResolver(userFormSchema.extend({ active: z.boolean() })),
    defaultValues: {
      name: user.name,
      email: user.email,
      role: user.role,
      active: user.active,
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: EditUserFormValues) => updateUser(user.id, values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY });
      onSaved();
    },
  });

  const errorMsg = apiErrorMessage(
    updateMutation.error,
    "Não foi possível salvar o usuário.",
  );

  return (
    <form
      onSubmit={handleSubmit((values) => updateMutation.mutate(values))}
      noValidate
      className="bg-primary-soft/40 flex flex-wrap items-end gap-3 px-5 py-4"
    >
      <div className="flex min-w-[160px] flex-1 flex-col gap-1">
        <input className={inputClass} {...register("name")} />
        {errors.name ? (
          <p className="text-red text-xs">{errors.name.message}</p>
        ) : null}
      </div>
      <div className="flex min-w-[200px] flex-1 flex-col gap-1">
        <input type="email" className={inputClass} {...register("email")} />
        {errors.email ? (
          <p className="text-red text-xs">{errors.email.message}</p>
        ) : null}
      </div>
      <div className="flex w-36 flex-col gap-1">
        <select className={inputClass} {...register("role")}>
          {Object.entries(ROLE_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <label className="flex items-center gap-2 text-sm font-medium">
        <Switch {...register("active")} />
        Ativo
      </label>
      {errorMsg ? <p className="text-red w-full text-sm">{errorMsg}</p> : null}
      <Button
        type="submit"
        disabled={isSubmitting || updateMutation.isPending}
        className="px-4 py-2 text-[13px]"
      >
        {updateMutation.isPending ? "Salvando..." : "Salvar"}
      </Button>
    </form>
  );
}

function UserPasswordForm({
  user,
  onSaved,
}: {
  user: User;
  onSaved: () => void;
}) {
  const [newPassword, setNewPassword] = useState("");

  const passwordMutation = useMutation({
    mutationFn: () => updateUserPassword(user.id, newPassword),
    onSuccess: () => {
      setNewPassword("");
      onSaved();
    },
  });

  const errorMsg = apiErrorMessage(
    passwordMutation.error,
    "Não foi possível alterar a senha.",
  );

  return (
    <div className="border-line bg-bg flex flex-wrap items-end gap-3 border-t px-5 py-4">
      <div className="flex min-w-[200px] flex-1 flex-col gap-1">
        <label className="text-sm font-medium">Nova senha</label>
        <input
          type="password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
          className={inputClass}
        />
        {errorMsg ? <p className="text-red text-xs">{errorMsg}</p> : null}
      </div>
      <Button
        disabled={newPassword.length < 8 || passwordMutation.isPending}
        onClick={() => passwordMutation.mutate()}
        className="px-4 py-2 text-[13px]"
      >
        {passwordMutation.isPending ? "Salvando..." : "Alterar senha"}
      </Button>
    </div>
  );
}
