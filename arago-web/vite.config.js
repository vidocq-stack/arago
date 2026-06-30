import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// Build statique empaqueté dans le jar arago-web : la sortie atterrit dans
// target/classes/static pour que maven-jar-plugin la package sous /static, là où
// Chappe la sert (vidocq.http.mount.ui.classpath=static).
//
// Multi-page : la SPA speaker (index.html → /) et la console admin distincte
// (admin.html). Chappe la sert à l'URL propre /admin (StaticFileHandler.cleanUrls,
// cf. ARAGO-006) ; le fichier reste admin.html dans le jar. Voir arago-spec §4.8.
export default defineConfig({
  plugins: [svelte()],
  base: '/',
  // Vidocq version captured at build time (env VIDOCQ_VERSION, set by frontend-maven-plugin from the
  // Maven property vidocq.runtime.version). Falls back to "dev" for a plain `npm run dev`.
  define: {
    __VIDOCQ_VERSION__: JSON.stringify(process.env.VIDOCQ_VERSION || 'dev'),
  },
  build: {
    outDir: 'target/classes/static',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        main: 'index.html',
        admin: 'admin.html',
        mydata: 'mes-donnees.html',
        speaker: 'speaker.html',
        display: 'display.html',
      },
    },
  },
});
