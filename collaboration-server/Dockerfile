FROM node:24-bookworm-slim AS build
WORKDIR /build/collaboration-server
COPY collaboration-server/package*.json ./
RUN npm ci
COPY cloudflare/src ../cloudflare/src
RUN npm run build && npm prune --omit=dev

FROM node:24-bookworm-slim
ENV NODE_ENV=production PORT=3000 DATABASE_PATH=/data/rooms.sqlite
WORKDIR /app
COPY --from=build /build/collaboration-server/node_modules ./node_modules
COPY --from=build /build/collaboration-server/dist ./dist
COPY collaboration-server/package.json collaboration-server/server.mjs ./
RUN mkdir /data && chown node:node /data
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s CMD node -e "fetch('http://127.0.0.1:3000/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server.mjs"]
