# 持股記帳

記下自己買了哪些台股，用證交所、櫃買中心的收盤價算出「現在全部賣掉，淨賺或淨虧多少」。

## 需要先安裝

- JDK 17 以上
- Docker（或 Podman）：用來跑 MySQL 8.4
- 不用另外裝 Maven，專案附了 `./mvnw`

## 啟動

```bash
docker compose up -d          # 起 MySQL，對外 port 3307（避開本機可能已有的 3306）
./mvnw spring-boot:run        # 第一次啟動時 Flyway 會自動建表
```

用 Podman 的話，第一行改成 `podman compose up -d`。

啟動後打開 http://localhost:8080
