import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";

import { Providers } from "@/app/providers";
import { ExtensionAttributeGuard } from "@/components/extension-attribute-guard";
import { ThemeScript } from "@/components/theme-script";
import { DEFAULT_THEME } from "@/lib/theme";
import "./globals.css";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Bank Portal | Digital Lending Platform",
  description: "Back-office portal for loan origination, credit, approval and disbursement.",
};

/**
 * Root layout: the document, the fonts and the client data layer, and nothing
 * else. Page chrome belongs to the route group that needs it, so that the
 * sign-in screen can own the full viewport.
 *
 * `data-theme` starts on the default and is corrected by ThemeScript, which is
 * the first thing in the body so it runs before anything is painted. That is
 * why <html> suppresses its hydration warning.
 */
export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      data-theme={DEFAULT_THEME}
      suppressHydrationWarning
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
        <ThemeScript />
        <ExtensionAttributeGuard />
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
