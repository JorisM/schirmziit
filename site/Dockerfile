FROM node:24-alpine AS build
WORKDIR /site
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY . .
# Analytics is opt-in at build time: without these the pages ship with no
# third-party script at all, which is what a fork of this repo should get.
ARG PUBLIC_UMAMI_HOST=""
ARG PUBLIC_UMAMI_ID=""
ENV PUBLIC_UMAMI_HOST=$PUBLIC_UMAMI_HOST PUBLIC_UMAMI_ID=$PUBLIC_UMAMI_ID
RUN pnpm build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /site/dist /usr/share/nginx/html
