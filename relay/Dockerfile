FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json ./
COPY shared ./shared
COPY relay ./relay
COPY client/package.json ./client/package.json
COPY packages/mcp/package.json ./packages/mcp/package.json

RUN npm ci

ENV HOST=0.0.0.0
ENV PORT=8787
EXPOSE 8787

CMD ["npm", "run", "start", "-w", "@pony/relay"]
