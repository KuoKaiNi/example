# 持股記帳 API

| 版本 | 路徑 | 給誰用 |
|---|---|---|
| 1.0 | `/api/v1` | App 1.0（已發布、不會更新）。規格照《App 1.0 API 規格》，**格式不會再改**。2.0 之後的資料怎麼對應過去，見 `UPGRADE.md` |
| 2.0 | `/api/v2` | App 2.0、網頁 |

共通規則（兩版相同）：

- JSON、UTF-8，欄位名稱小寫加底線
- 金額是整數（元）；價格最多兩位小數；日期 `YYYY-MM-DD`（西元年）
- 錯誤一律 `{"error":{"code":"...","message":"..."}}`，`message` 可以直接顯示給使用者

## 2.0：股數用「股」，一檔可以有多筆買賣

### 計算規則

- 手續費、證交稅同 1.0：手續費 0.1425% 無條件捨去、每筆最低 20 元（零股也是 20 元）；證交稅一般 0.3%、ETF（00 開頭）0.1%，只有賣出收
- 成交金額 = 價格 × 股數，無條件捨去到元
- 交易依「交易日期、再依建立順序」排序後重算
- 成本用**移動平均**：
  - 買進：成本加上「成交金額＋手續費」
  - 賣出：從成本按比例扣掉「賣出股數 ÷ 賣出前股數」那一份，四捨五入到元；全部賣光就整筆扣掉
  - 已實現損益 = 成交金額 − 手續費 − 證交稅 − 扣掉的成本
- 未實現損益 = 剩下股數用收盤價全部賣出的淨額 − 剩下的成本
- 任何時間點都不能賣超過當時持有的股數（補記較早日期的賣出、修改或刪除交易時也會檢查）

### 持股物件

```json
{
  "id": 1, "code": "2330", "name": "台積電", "market": "TWSE",
  "shares": 1200, "avg_cost": 1435.38, "cost": 1722450,
  "current_price": 2350.00, "price_date": "2026-11-13", "market_value": 2820000,
  "unrealized_pnl": 1085072, "unrealized_return_pct": 63.00,
  "realized_pnl": 271268
}
```

| 欄位 | 說明 |
|---|---|
| `shares` | 持有股數 |
| `avg_cost` | 平均成本（每股，含買進手續費），兩位小數；沒有持股時 `null` |
| `cost` | 剩下持股的成本（含買進手續費） |
| `current_price`、`price_date`、`market_value`、`unrealized_pnl`、`unrealized_return_pct` | 取不到收盤價、或已經全部賣掉時為 `null` |
| `realized_pnl` | 這檔所有賣出的已實現損益加總 |

### 交易物件

```json
{ "id": 5, "side": "SELL", "trade_date": "2026-11-13", "shares": 300, "price": 2350.00,
  "amount": 705000, "fee": 1004, "tax": 2115, "realized_pnl": 271268, "shares_after": 1200 }
```

買進的 `tax`、`realized_pnl` 為 0。

### API 清單

| 方法 | 路徑 | 成功 | 失敗 |
|---|---|---|---|
| GET | `/api/v2/holdings` | 200，持股物件陣列。預設只回還有持股的；`?include_closed=true` 連全部賣掉的也回 | |
| GET | `/api/v2/holdings/{id}` | 200，`{"holding": 持股物件, "trades": [交易物件...]}` | 404 `NOT_FOUND` |
| DELETE | `/api/v2/holdings/{id}` | 204，整檔連同所有交易刪除 | 404 `NOT_FOUND` |
| POST | `/api/v2/trades` | 201，`{"trade_id": 新交易 id, "holding": ..., "trades": [...]}` | 400 `VALIDATION_ERROR`、404 `STOCK_NOT_FOUND`、409 `INSUFFICIENT_SHARES`、503 `QUOTE_SOURCE_DOWN` |
| PUT | `/api/v2/trades/{id}` | 200，格式同 POST | 400、404 `NOT_FOUND`、409 `INSUFFICIENT_SHARES` |
| DELETE | `/api/v2/trades/{id}` | 204；刪掉的是這檔最後一筆時整檔一起刪 | 404 `NOT_FOUND`、409 `TRADE_CONFLICT` |
| GET | `/api/v2/summary` | 200，總計物件 | |

**POST 的請求內容**

```json
{ "code": "2330", "side": "SELL", "trade_date": "2026-11-13", "shares": 300, "price": 2350 }
```

- `side`：`BUY` 或 `SELL`
- `shares`：1 ～ 9,999,999 的整數
- `trade_date`：不能是未來
- 買進的代號還沒有持股時，會向證交所、櫃買中心確認代號並建立持股

**PUT 的請求內容**：`trade_date`、`shares`、`price` 三個都要給；`code`、`side` 不能改，送了也會忽略。

**總計物件**

```json
{ "total_cost": 2037899, "market_value": 3135000, "unrealized_pnl": 1083230,
  "unrealized_return_pct": 53.15, "realized_pnl": 151984, "quote_missing": 0 }
```

`total_cost`、`market_value`、`unrealized_pnl` 只算還有持股的；`realized_pnl` 包含已經全部賣掉的。

### 錯誤代碼

| code | HTTP | 什麼時候 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 欄位缺漏、格式不對、股數不是整數、日期是未來 |
| `NOT_FOUND` | 404 | 持股或交易不存在 |
| `STOCK_NOT_FOUND` | 404 | 上市、上櫃都查不到這個代號 |
| `INSUFFICIENT_SHARES` | 409 | 賣出（或修改後）會超過當時持有的股數 |
| `TRADE_CONFLICT` | 409 | 刪掉這筆會讓後面的賣出超過持有股數 |
| `MULTIPLE_TRADES` | 409 | 只有 `/api/v1` 會出現，見 `UPGRADE.md` |
| `QUOTE_SOURCE_DOWN` | 503 | 證交所、櫃買中心連不上，無法確認代號 |
| `INTERNAL_ERROR` | 500 | 其他 |
