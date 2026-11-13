-- 2.0：一檔持股可以有多筆買賣。holding 留著當主檔（id 不變，App 1.0 記住的 id 才對得上），
-- 每筆買進、賣出記在 trade。
CREATE TABLE trade (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    holding_id BIGINT        NOT NULL,
    side       VARCHAR(4)    NOT NULL,
    trade_date DATE          NOT NULL,
    shares     INT           NOT NULL,
    price      DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trade_holding FOREIGN KEY (holding_id) REFERENCES holding (id) ON DELETE CASCADE
);
CREATE INDEX idx_trade_holding ON trade (holding_id, trade_date, id);

-- 1.0 的每筆持股轉成一筆買進：股數 = 張數 × 1000；1.0 沒有買進日期，用建立時間的日期。
INSERT INTO trade (holding_id, side, trade_date, shares, price, created_at)
SELECT id, 'BUY', CAST(created_at AS DATE), lots * 1000, buy_price, created_at
FROM holding;
