FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/rivy-sync .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates && adduser -D -u 10001 rivy
USER rivy
COPY --from=build /out/rivy-sync /usr/local/bin/rivy-sync
EXPOSE 8080
ENTRYPOINT ["rivy-sync"]
