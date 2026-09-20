# Website

This website is built using [Docusaurus](https://docusaurus.io/), a modern static website generator.

## Installation

```bash
npm install
```

**Note**: feel free to use the package manager of your choice.

## Local Development

```bash
npm run start
```

This command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.

## Build

```bash
npm run build
```

This command generates static content into the `build` directory and can be served using any static contents hosting service.

## Deployment

The site is deployed automatically to <https://modulekit.cubix.gg> by the
`Deploy Docs` GitHub Actions workflow (`.github/workflows/deploy-docs.yml`)
on every push to `master` that touches `docs/`. Pull requests run the same
build without deploying, so broken links fail before they reach `master`.

To check a production build locally before pushing:

```bash
npm run build && npm run serve
```

The custom domain is pinned by `static/CNAME`; do not delete that file or the
domain is dropped on the next deploy.
