import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// Build statique empaqueté dans le jar arago-web : la sortie atterrit dans
// target/classes/static pour que maven-jar-plugin la package sous /static, là où
// Chappe la sert (vidocq.http.mount.ui.classpath=static).
export default defineConfig({
  plugins: [svelte()],
  base: '/',
  build: {
    outDir: 'target/classes/static',
    emptyOutDir: true,
  },
});
