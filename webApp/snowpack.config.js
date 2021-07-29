import { AppConfig } from './src/config/AppConfig.js'
export default {
  buildOptions: {
    clean: true,
    sourceMaps: false,
    out: AppConfig.applicationName
  },
  mount: {
    public: "/",
    src: "/dist",
    test: "/dist_test"
  },
  plugins: [["@snowpack/plugin-typescript"]],
  alias: {
    "io-ts/lib": "io-ts/es6",
    "fp-ts/lib": "fp-ts/es6"
  },
  routes: [{ match: 'routes', src: '.*', dest: '/index.html' }]
}
