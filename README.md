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
| `/holdings` | 持股清單：記買進、賣出（股數用「股」，可記零股），顯示平均成本、收盤價、未實現與已實現損益、總計 |
| `/holdings/{id}` | 單檔的買賣紀錄：每筆的手續費、證交稅、已實現損益；可以修改、刪除 |

## API

| 版本 | 路徑 | 文件 |
|---|---|---|
| 1.0 | `/api/v1` | 《App 1.0 API 規格》，格式不會再改 |
| 2.0 | `/api/v2` | [API.md](API.md) |

從 1.0 升到 2.0 的步驟、資料庫變動、App 1.0 會看到什麼：[UPGRADE.md](UPGRADE.md)

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

| 測試 | 內容 |
|---|---|
| `ApiV1ContractTest` | App 1.0 的契約。**v1.0.0 之後沒改過**，2.0 照樣全部通過 |
| `ApiV1CompatTest` | 2.0 記了零股、多筆買賣之後，App 1.0 看到的樣子 |
| `ApiV2Test`、`PositionTest` | 2.0 的買賣、移動平均成本、已實現損益 |
| `MigrationTest` | 資料庫停在 1.0、有資料，升級後 id 不變、張數換成股數 |
| `ConcurrentSellTest` | 兩筆賣出同時進來只能成功一筆 |

> H2 的交易隔離跟 MySQL 不同，`ConcurrentSellTest` 在 H2 上通過不代表 MySQL 沒問題（2.0 開發時就踩過，見 REVIEW.md）。改到鎖或交易的程式時，要在 MySQL 上手動再驗一次。

## 行情資料

| 市場 | 來源 |
|---|---|
| 上市 | 證交所 `STOCK_DAY`，一次回一個月的日資料 |
| 上櫃 | 櫃買中心 `tradingStock`，一次回一個月的日資料 |

- 新增持股時先查上市、查不到再查上櫃，兩邊都沒有就回「查無此股」；官方連不上時回 503，不會誤判成查無此股
- 同一檔同一天只查一次；下午 3 點前查過的，3 點後會再查一次拿當天收盤價
- 官方掛掉時清單照樣顯示，收盤價顯示「未取得」，1 分鐘內不重試
