FROM node:22-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . .
# 운영은 nginx가 /api를 backend로 프록시하므로 같은 출처다.
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
# curl: 컨테이너 healthcheck용. alpine nginx 이미지에는 없다.
# tzdata: 액세스 로그 시각을 KST로 남긴다 (Alpine 기본은 UTC).
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone
COPY --from=build /src/dist /usr/share/nginx/html
# conf.d에 직접 넣지 않는다. templates에 두면 시작 시 envsubst로 ${DOMAIN}이 치환된다.
COPY templates /etc/nginx/templates
EXPOSE 80 443
