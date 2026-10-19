# --- BUILD ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./

# cached layer: only reruns when the dependencies change
RUN npm ci

COPY . .
RUN npm run build

# --- RUNTIME ---
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
