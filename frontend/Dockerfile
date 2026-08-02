# Obraz frontu (M5.3/D30) — statyki zbudowane Vite i podane przez nginx.
# To osobna aplikacja od backendu: własny obraz, własny cykl życia, własny port.
# nginx przekazuje /api na backend, więc front nadal woła adresy względne
# i nie trzeba otwierać CORS-a w aplikacji bez auth (D2/D14).

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . ./
RUN npm run build

FROM nginx:1.27-alpine AS runtime
# entrypoint obrazu nginx renderuje szablony z /etc/nginx/templates przez envsubst
ENV API_HOST=api
ENV API_PORT=8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
