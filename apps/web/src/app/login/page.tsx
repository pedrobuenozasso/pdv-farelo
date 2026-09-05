"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { login } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { decodeRole, setSession } from "@/lib/auth";
import { LogoBadge } from "@/components/logo-badge";
import { Button } from "@/components/ui/button";

const loginFormSchema = z.object({
  email: z.string().trim().min(1, "Email é obrigatório"),
  password: z.string().min(1, "Senha é obrigatória"),
});

type LoginFormValues = z.infer<typeof loginFormSchema>;

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginFormSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      const result = await login(values);
      setSession({ ...result, role: decodeRole(result.token) });
      const redirect = searchParams.get("redirect") ?? "/pdv";
      router.push(redirect);
    } catch (error) {
      setError("root", {
        message:
          error instanceof ApiError
            ? error.message
            : "Não foi possível entrar. Tente novamente.",
      });
    }
  });

  return (
    <main className="bg-bg flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <LogoBadge size={52} />
          <div>
            <div className="font-serif text-xl font-semibold italic">
              Farelo OS
            </div>
            <div className="text-ink-soft text-sm">Acesso da equipe</div>
          </div>
        </div>

        <form
          onSubmit={onSubmit}
          noValidate
          className="border-line bg-surface flex flex-col gap-4 rounded-2xl border p-6 shadow-[0_1px_2px_oklch(30%_0.03_50_/_6%),0_8px_20px_oklch(30%_0.03_50_/_7%)]"
        >
          <div className="flex flex-col gap-1.5">
            <label htmlFor="email" className="text-ink text-sm font-medium">
              Email
            </label>
            <input
              id="email"
              type="email"
              placeholder="voce@farelo.dev"
              autoComplete="username"
              className="border-line bg-surface text-ink focus:border-primary rounded-lg border px-3.5 py-2.5 text-sm outline-none"
              {...register("email")}
            />
            {errors.email ? (
              <p className="text-red text-sm">{errors.email.message}</p>
            ) : null}
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="password" className="text-ink text-sm font-medium">
              Senha
            </label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              className="border-line bg-surface text-ink focus:border-primary rounded-lg border px-3.5 py-2.5 text-sm outline-none"
              {...register("password")}
            />
            {errors.password ? (
              <p className="text-red text-sm">{errors.password.message}</p>
            ) : null}
          </div>

          {errors.root ? (
            <p className="text-red text-sm">{errors.root.message}</p>
          ) : null}

          <Button type="submit" disabled={isSubmitting} className="mt-1 w-full">
            {isSubmitting ? "Entrando..." : "Entrar"}
          </Button>
        </form>
      </div>
    </main>
  );
}
