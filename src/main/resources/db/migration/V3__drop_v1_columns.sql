-- 張數與買進價已經搬到 trade，主檔不再存，免得兩邊不一致。
ALTER TABLE holding DROP COLUMN lots;
ALTER TABLE holding DROP COLUMN buy_price;
