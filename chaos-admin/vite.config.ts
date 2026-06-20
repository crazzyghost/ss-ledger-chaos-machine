import path from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("/node_modules/")) {
            return undefined;
          }

          if (
            id.includes("/node_modules/react-router/") ||
            id.includes("/node_modules/react-router-dom/") ||
            id.includes("/node_modules/@remix-run/router/")
          ) {
            return "router";
          }

          if (id.includes("/node_modules/@tanstack/")) {
            return "query";
          }

          return undefined;
        }
      }
    }
  },
  server: {
    port: 5173,
    host: true
  }
});
