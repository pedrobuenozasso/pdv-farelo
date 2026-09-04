import type { Metadata } from "next";
import { Lora, Plus_Jakarta_Sans } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

// Farelo OS's brand type pairing: Lora (serif) for brand/display moments
// — the menu header, screen titles, comanda numbers on the KDS — Plus
// Jakarta Sans (sans) for everything operational (body copy, forms,
// tables). See the design canvas (Sistema.dc.html) for the full system.
const lora = Lora({
  variable: "--font-lora",
  subsets: ["latin"],
  style: ["normal", "italic"],
});

const plusJakartaSans = Plus_Jakarta_Sans({
  variable: "--font-plus-jakarta-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Farelo OS",
  description: "Farelo OS — sistema de operação para cafeteria.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      className={`${lora.variable} ${plusJakartaSans.variable} h-full antialiased`}
    >
      <body className="bg-bg text-ink flex min-h-full flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
