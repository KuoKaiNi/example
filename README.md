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

## 網頁

| 網址 | 內容 |
|---|---|
| `/` | 首頁 |
| `/holdings` | 持股清單：新增、修改、刪除，顯示收盤價、盈虧、總計 |

## App 1.0 用的 API

照《App 1.0 API 規格》實作，路徑 `/api/v1`，欄位用小寫加底線。

```bash
curl -X POST localhost:8080/api/v1/holdings -H 'Content-Type: application/json' \
     -d '{"code":"2330","lots":1,"buy_price":1000}'
curl localhost:8080/api/v1/holdings
curl localhost:8080/api/v1/summary
```

## 測試

```bash
./mvnw test
```

測試不連網：行情改用 `FakeMarketData`，資料庫用 H2（MySQL 模式），不用先起 Docker。
`ApiV1ContractTest` 是 App 1.0 的契約，之後的版本只能加測試、不能改這個檔案裡的預期值。

## 行情資料

| 市場 | 來源 |
|---|---|
| 上市 | 證交所 `STOCK_DAY`，一次回一個月的日資料 |
| 上櫃 | 櫃買中心 `tradingStock`，一次回一個月的日資料 |

- 新增持股時先查上市、查不到再查上櫃，兩邊都沒有就回「查無此股」；官方連不上時回 503，不會誤判成查無此股
- 同一檔同一天只查一次；下午 3 點前查過的，3 點後會再查一次拿當天收盤價
- 官方掛掉時清單照樣顯示，收盤價顯示「未取得」，1 分鐘內不重試
